package org.jellyfin.androidtv.ui.navigation

import androidx.fragment.app.Fragment
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.browsing.BrowseGridFragment
import org.koin.java.KoinJavaComponent
import timber.log.Timber
import java.util.Stack
import kotlin.reflect.KClass

// Extension property to get fragment class from Destination.Fragment
private val Destination.Fragment.fragmentClass: KClass<out Fragment>
    get() = fragment

/**
 * Repository for app navigation. This manages the screens/pages for the app.
 */
interface NavigationRepository {
	/**
	 * The current action to act on.
	 *
	 * @see NavigationAction
	 */
	val currentAction: SharedFlow<NavigationAction>

	/**
	 * Navigate to [destination].
	 *
	 * @see Destinations
	 */
	fun navigate(destination: Destination) = navigate(destination, false)

	/**
	 * Navigate to [destination].
	 *
	 * @see Destinations
	 */
	fun navigate(destination: Destination, replace: Boolean)

	/**
	 * Whether the [goBack] function will succeed or not.
	 *
	 * @see [goBack]
	 */
	val canGoBack: Boolean

	/**
	 * Go back to the previous fragment. The back stack does not consider other destination types.
	 *
	 * @see [canGoBack]
	 */
	fun goBack(): Boolean

	/**
	 * Reset navigation to the initial destination or a specific [Destination.Fragment].
	 *
	 * @param clearHistory Empty out the back stack
	 */
	fun reset(destination: Destination.Fragment? = null, clearHistory: Boolean)

	/**
	 * Reset navigation to the initial destination or a specific [Destination.Fragment] without clearing history.
	 */
	fun reset(destination: Destination.Fragment? = null) = reset(destination, false)

	/**
	 * Increment the counter for back navigation skips. Used when launching external intents.
	 * Each call increments the counter, and back presses will be ignored until the counter reaches 0.
	 */
	fun incrementSkipBackNavigation()
	
	/**
	 * Check if the backdrop should be cleared for the given destination.
	 */
	fun shouldClearBackdrop(destination: Destination): Boolean
}

class NavigationRepositoryImpl(
	private val defaultDestination: Destination.Fragment,
	private val userPreferences: UserPreferences,
) : NavigationRepository {
	private val fragmentHistory = Stack<Destination.Fragment>()

	private val _currentAction = MutableSharedFlow<NavigationAction>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
	override val currentAction = _currentAction.asSharedFlow()

	private var skipBackNavigationCount = 0
	private var lastBackNavigationTime = 0L
	private var fragmentLoadCooldownEndTime = 0L

	override fun shouldClearBackdrop(destination: Destination): Boolean {
		return when (destination) {
			is Destination.Fragment -> {
				destination.fragmentClass == BrowseGridFragment::class
			}
		}
	}

	@Suppress("UNCHECKED_CAST")
	private fun <T> getKoinComponent(klass: Class<T>): T {
		return KoinJavaComponent.get(klass) as T
	}

	private fun clearBackdropIfNeeded(destination: Destination) {
		try {
			if (shouldClearBackdrop(destination)) {
				val backgroundService: BackgroundService = getKoinComponent(BackgroundService::class.java)
				backgroundService.clearBackgrounds()
			}
		} catch (e: Exception) {
			Timber.e(e, "Failed to clear backdrop")
		}
	}

	override fun navigate(destination: Destination, replace: Boolean) {

		// Clear backdrop if needed
		clearBackdropIfNeeded(destination)

		val action = when (destination) {
			is Destination.Fragment -> NavigationAction.navigateFragment(destination, true, replace, false)
		}
		if (destination is Destination.Fragment) {
			// Set cooldown for back button after fragment navigation
			val cooldownMs = userPreferences[UserPreferences.backButtonCooldownMs].toLong()
			fragmentLoadCooldownEndTime = System.currentTimeMillis() + cooldownMs

			if (replace && fragmentHistory.isNotEmpty()) {
				fragmentHistory[fragmentHistory.lastIndex] = destination
			} else {
				fragmentHistory.push(destination)
			}
		}
		_currentAction.tryEmit(action)
	}

	override val canGoBack: Boolean get() = fragmentHistory.isNotEmpty()

	override fun goBack(): Boolean {

		// If we have pending skips (e.g., returning from external intents),
		// decrement counter and emit action without modifying history
		if (skipBackNavigationCount > 0) {
			skipBackNavigationCount--
			_currentAction.tryEmit(NavigationAction.GoBack)
			return true
		}

		if (fragmentHistory.empty()) {
			return false
		}

		// Rate limit back navigation to prevent rapid presses that cause focus issues
		val currentTime = System.currentTimeMillis()
		val cooldownMs = userPreferences[UserPreferences.backButtonCooldownMs].toLong()

		// Check if back button is disabled due to recent fragment navigation
		if (currentTime < fragmentLoadCooldownEndTime) {
			Timber.d("Back navigation disabled - fragment load cooldown active")
			return false
		}

		// Check for rapid presses between back button events
		if (currentTime - lastBackNavigationTime < cooldownMs) {
			Timber.d("Back navigation rate limited - too soon after previous navigation")
			return false
		}
		lastBackNavigationTime = currentTime

		val currentFragment = fragmentHistory.pop()

		// Check if we're navigating back to the horizontal grid browse
		if (fragmentHistory.isNotEmpty()) {
			val previousFragment = fragmentHistory.peek()
			if (previousFragment.fragmentClass == BrowseGridFragment::class) {
				// Clear backdrop when navigating back to horizontal grid browse
				clearBackdropIfNeeded(previousFragment)
			}
		}

		_currentAction.tryEmit(NavigationAction.GoBack)
		return true
	}

	override fun reset(destination: Destination.Fragment?, clearHistory: Boolean) {
		fragmentHistory.clear()
		val actualDestination = destination ?: defaultDestination

		// Clear backdrop if needed
		clearBackdropIfNeeded(actualDestination)

		_currentAction.tryEmit(NavigationAction.navigateFragment(actualDestination, true, false, clearHistory))
	}

	override fun incrementSkipBackNavigation() {
		skipBackNavigationCount++
		Timber.d("[BACK_NAV] NavigationRepository.incrementSkipBackNavigation: count now $skipBackNavigationCount")
	}
}

