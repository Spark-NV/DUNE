package org.jellyfin.androidtv.ui.itemdetail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.leanback.app.RowsSupportFragment
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.scraper.StreamData
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.presentation.CustomListRowPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID
import org.koin.android.ext.android.inject
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class StreamSelectionFragment : Fragment(), StreamItemClickListener {
	private var rowsFragment: RowsSupportFragment? = null
	private var rowsAdapter: MutableObjectAdapter<Row>? = null
	private var titleText: TextView? = null
	private var subtitleText: TextView? = null
	private var streamCounterText: TextView? = null
	private var streams: List<StreamData> = emptyList()
	private var mediaTitle: String = ""
	private var mediaSubtitle: String = ""
	private var itemId: String = ""
	private var resumePosition: Int = 0

	private val api: ApiClient by inject()
	private val userPreferences by inject<UserPreferences>()
	private val navigationRepository by inject<NavigationRepository>()

	companion object {
		private const val ARG_MEDIA_TITLE = "media_title"
		private const val ARG_MEDIA_SUBTITLE = "media_subtitle"
		private const val ARG_ITEM_ID = "item_id"
		private const val ARG_RESUME_POSITION = "resume_position"

		@JvmStatic
		fun newInstance(
			mediaTitle: String,
			mediaSubtitle: String = "",
			itemId: String = "",
			resumePosition: Int = 0
		): StreamSelectionFragment {
			return StreamSelectionFragment().apply {
				arguments = Bundle().apply {
					putString(ARG_MEDIA_TITLE, mediaTitle)
					putString(ARG_MEDIA_SUBTITLE, mediaSubtitle)
					putString(ARG_ITEM_ID, itemId)
					putInt(ARG_RESUME_POSITION, resumePosition)
				}
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		arguments?.let {
			mediaTitle = it.getString(ARG_MEDIA_TITLE, "")
			mediaSubtitle = it.getString(ARG_MEDIA_SUBTITLE, "")
			itemId = it.getString(ARG_ITEM_ID, "")
			resumePosition = it.getInt(ARG_RESUME_POSITION, 0)
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View? {
		Timber.d("[StreamSelectionFragment] onCreateView called")
		val view = inflater.inflate(R.layout.fragment_stream_selection, container, false)
		titleText = view.findViewById(R.id.titleText)
		subtitleText = view.findViewById(R.id.subtitleText)
		streamCounterText = view.findViewById(R.id.streamCounterText)

		titleText?.text = getString(R.string.select_stream)
		subtitleText?.text = mediaSubtitle.ifEmpty { mediaTitle }

		rowsFragment = RowsSupportFragment()
		childFragmentManager.beginTransaction()
			.replace(R.id.rowsFragment, rowsFragment!!)
			.commit()


		setupRows(isLoading = true)

		// Fetch streams for the item
		fetchStreams()

		return view
	}

	private fun setupRows(isLoading: Boolean = false) {
		val presenterSelector = ClassPresenterSelector()
		presenterSelector.addClassPresenter(StreamRow::class.java, StreamRowPresenter(this))

		rowsAdapter = MutableObjectAdapter(presenterSelector)
		rowsFragment?.adapter = rowsAdapter

		if (streams.isEmpty()) {
			// Show loading state or empty state
			val message = if (isLoading) getString(R.string.loading) else getString(R.string.no_streams_found)
			val emptyItem = StreamRow(StreamItem(message, ""))
			rowsAdapter?.add(emptyItem)
		} else {
			// Create individual rows for each stream
			for ((index, stream) in streams.withIndex()) {
				val streamItem = StreamItem.fromStreamData(stream)
				val streamRow = StreamRow(streamItem)
				rowsAdapter?.add(streamRow)
			}
		}
	}

	override fun onStreamItemClicked(streamData: StreamData?) {
		if (streamData != null && streamData.url.isNotEmpty()) {

			// Validate URL format
			val isValidUrl = try {
				Uri.parse(streamData.url)
				streamData.url.startsWith("http") || streamData.url.startsWith("magnet:")
			} catch (e: Exception) {
				false
			}

			if (isValidUrl) {
				// Check if URL might need redirect resolution (debrid services)
				if (needsRedirectResolution(streamData.url)) {
					Utils.showToast(requireContext(), getString(R.string.resolving_stream))
					resolveAndLaunchStream(streamData.url)
				} else {
					launchStream(streamData.url)
				}
			} else {
				Utils.showToast(requireContext(), "Invalid URL format for ${streamData.provider}")
			}
		} else {
			Utils.showToast(requireContext(), getString(R.string.invalid_stream_url))
		}
	}

	/**
	 * Check if URL might need redirect resolution (debrid/proxy services)
	 */
	private fun needsRedirectResolution(url: String): Boolean {
		val debridHosts = listOf(
			"stremthru",
			"comet.elfhosted",
			"mediafusion.elfhosted",
			"jackettio.elfhosted",
			"torrentio.strem",
			"/playback/",
			"/stream/"
		)
		val urlLower = url.lowercase()
		return debridHosts.any { urlLower.contains(it) }
	}

	/**
	 * Resolve redirects and get the final stream URL before launching
	 */
	private fun resolveAndLaunchStream(url: String) {
		lifecycleScope.launch {
			try {
				val resolvedUrl = withContext(Dispatchers.IO) {
					resolveRedirects(url)
				}

				if (!isAdded) return@launch

				if (resolvedUrl != null && resolvedUrl.isNotEmpty()) {
					Timber.d("[StreamSelection] Resolved URL: $resolvedUrl")
					launchStream(resolvedUrl)
				} else {
					// Fallback to original URL if resolution fails
					Timber.w("[StreamSelection] URL resolution failed, using original URL")
					launchStream(url)
				}
			} catch (e: Exception) {
				Timber.e(e, "[StreamSelection] Error resolving URL")
				if (isAdded) {
					// Fallback to original URL
					launchStream(url)
				}
			}
		}
	}

	/**
	 * Follow redirects to get the final URL
	 */
	private fun resolveRedirects(originalUrl: String, maxRedirects: Int = 5): String {
		var currentUrl = originalUrl
		var redirectCount = 0

		while (redirectCount < maxRedirects) {
			try {
				val url = URL(currentUrl)
				val connection = url.openConnection() as HttpURLConnection
				connection.instanceFollowRedirects = false
				connection.requestMethod = "HEAD"
				connection.connectTimeout = 10000
				connection.readTimeout = 10000
				connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

				val responseCode = connection.responseCode
				Timber.d("[StreamSelection] URL check: $currentUrl -> $responseCode")

				when (responseCode) {
					HttpURLConnection.HTTP_OK -> {
						// Check Content-Type to verify it's a video
						val contentType = connection.contentType ?: ""
						Timber.d("[StreamSelection] Content-Type: $contentType")
						connection.disconnect()
						return currentUrl
					}
					HttpURLConnection.HTTP_MOVED_PERM,
					HttpURLConnection.HTTP_MOVED_TEMP,
					HttpURLConnection.HTTP_SEE_OTHER,
					307, 308 -> {
						val location = connection.getHeaderField("Location")
						connection.disconnect()
						if (location != null && location.isNotEmpty()) {
							// Handle relative redirects
							currentUrl = if (location.startsWith("http")) {
								location
							} else {
								URL(url, location).toString()
							}
							Timber.d("[StreamSelection] Following redirect to: $currentUrl")
							redirectCount++
						} else {
							Timber.w("[StreamSelection] Redirect without Location header")
							return currentUrl
						}
					}
					else -> {
						connection.disconnect()
						Timber.w("[StreamSelection] Unexpected response code: $responseCode")
						return currentUrl
					}
				}
			} catch (e: Exception) {
				Timber.e(e, "[StreamSelection] Error checking URL: $currentUrl")
				return currentUrl
			}
		}

		Timber.w("[StreamSelection] Max redirects reached")
		return currentUrl
	}

	private fun fetchStreams() {
		if (itemId.isEmpty()) {
			return
		}


		// Show loading state
		setupRows(isLoading = true)

		// Fetch item details and then streams
		lifecycleScope.launch {
			try {
				val itemResponse = withContext(Dispatchers.IO) {
					api.userLibraryApi.getItem(UUID.fromString(itemId))
				}

				val item = itemResponse.content

				// Now query streams
				val scope = viewLifecycleOwner.lifecycleScope
				val helper = StreamScraperHelper(userPreferences, scope, api)

				helper.queryStreams(item) { streamsResult ->
					if (!isAdded) return@queryStreams

					streams = streamsResult
					updateStreamCounter()
					setupRows() // Refresh UI with actual streams
				}

			} catch (e: Exception) {
				Utils.showToast(requireContext(), "Failed to load streams")
			}
		}
	}

	private fun launchStream(url: String) {
		try {
			Timber.d("[StreamSelection] Launching stream URL: $url")

			// Increment counter to skip back navigation when returning from external intent
			navigationRepository.incrementSkipBackNavigation()

			// Get current user information (blocking call since we're not in a coroutine)
			val currentUser: org.jellyfin.sdk.api.client.Response<org.jellyfin.sdk.model.api.UserDto> = kotlinx.coroutines.runBlocking {
				kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
					api.userApi.getCurrentUser()
				}
			}
			val userId = currentUser.content.id.toString()
			val accessToken = api.accessToken ?: ""

			// Get server URL
			val serverUrl = api.baseUrl ?: ""

			// Parse the URL
			val videoUri = android.net.Uri.parse(url)
			Timber.d("[StreamSelection] Parsed URI: $videoUri")

			// Create explicit intent for com.brouken.player
			val intent = android.content.Intent().apply {
				action = android.content.Intent.ACTION_VIEW
				setPackage("com.brouken.player")
				setDataAndType(videoUri, "video/*")
				addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)

				// Add Jellyfin-specific parameters
				putExtra("server", "jellyfin")
				putExtra("Jellyfin_URL", serverUrl)
				putExtra("user_id", userId)
				putExtra("user_token", accessToken)
				putExtra("item_id", itemId)

				// Always add position parameter - 0 for playback from beginning, add position for resume
				putExtra("position", resumePosition)

				// Add headers that might help with streaming (used by some players)
				putExtra("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

				// Alternative headers format used by some players like VLC/MX Player
				val headers = arrayOf("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
				putExtra("headers", headers)
			}

			// Log intent details for debugging
			Timber.d("[StreamSelection] Intent action: ${intent.action}")
			Timber.d("[StreamSelection] Intent data: ${intent.data}")
			Timber.d("[StreamSelection] Intent type: ${intent.type}")
			Timber.d("[StreamSelection] Intent package: ${intent.`package`}")

			requireContext().startActivity(intent)
			Timber.d("[StreamSelection] Stream launched successfully")
		} catch (e: android.content.ActivityNotFoundException) {
			Timber.e(e, "[StreamSelection] Player app not found")
			Utils.showToast(requireContext(), "External player app not found. Please install com.brouken.player")
		} catch (e: Exception) {
			Timber.e(e, "[StreamSelection] Error launching stream")
			Utils.showToast(requireContext(), getString(R.string.error_launching_stream))
		}
	}

	private fun updateStreamCounter() {
		val torrentioEnabled = userPreferences[UserPreferences.torrentioEnabled]
		val aiostreamsEnabled = userPreferences[UserPreferences.aiostreamsEnabled]

		val torrentioCount = streams.count { it.provider == "Torrentio" }
		val aioStreamsCount = streams.count { it.provider == "AIOStreams" }

		val spannableText = SpannableString(buildString {
			// Add header if any provider is disabled
			if (!torrentioEnabled || !aiostreamsEnabled) {
				if (!torrentioEnabled && aiostreamsEnabled) {
					append("Torrentio disabled in settings:\n")
				} else if (torrentioEnabled && !aiostreamsEnabled) {
					append("AIOStreams disabled in settings:\n")
				} else if (!torrentioEnabled && !aiostreamsEnabled) {
					append("Both providers disabled in settings:\n")
				}
			}

			// Torrentio line
			if (torrentioEnabled) {
				append("Torrentio: $torrentioCount\n")
			} else {
				append("Torrentio: Disabled\n")
			}

			// AIOStreams line
			if (aiostreamsEnabled) {
				append("AIOStreams: $aioStreamsCount")
			} else {
				append("AIOStreams: Disabled")
			}
		})

		// Make all "Disabled" text red
		var searchStart = 0
		while (true) {
			val disabledStart = spannableText.indexOf("Disabled", searchStart)
			if (disabledStart == -1) break

			val disabledEnd = disabledStart + "Disabled".length
			spannableText.setSpan(
				ForegroundColorSpan(Color.RED),
				disabledStart,
				disabledEnd,
				0
			)
			searchStart = disabledEnd
		}

		streamCounterText?.text = spannableText
		streamCounterText?.visibility = if (streams.isNotEmpty() || !torrentioEnabled || !aiostreamsEnabled) View.VISIBLE else View.GONE
	}
}


