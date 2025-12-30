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

class StreamSelectionFragment : Fragment(), StreamItemClickListener {
	private var rowsFragment: RowsSupportFragment? = null
	private var rowsAdapter: MutableObjectAdapter<Row>? = null
	private var titleText: TextView? = null
	private var subtitleText: TextView? = null
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
				launchStream(streamData.url)
			} else {
				Utils.showToast(requireContext(), "Invalid URL format for ${streamData.provider}")
			}
		} else {
			Utils.showToast(requireContext(), getString(R.string.invalid_stream_url))
		}
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
					setupRows() // Refresh UI with actual streams
				}

			} catch (e: Exception) {
				Utils.showToast(requireContext(), "Failed to load streams")
			}
		}
	}

	private fun launchStream(url: String) {
		try {

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

			// Create explicit intent for com.brouken.player
			val intent = android.content.Intent().apply {
				setPackage("com.brouken.player")
				setDataAndType(android.net.Uri.parse(url), "video/*")
				// Removed FLAG_ACTIVITY_NEW_TASK to preserve back navigation

				// Add Jellyfin-specific parameters
				putExtra("server", "jellyfin")
				putExtra("Jellyfin_URL", serverUrl)
				putExtra("user_id", userId)
				putExtra("user_token", accessToken)
				putExtra("item_id", itemId)

				// Always add position parameter - 0 for playback from beginning, add position for resume
				putExtra("position", resumePosition)

				// Add headers that might help with streaming
				putExtra("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
			}

			requireContext().startActivity(intent)
		} catch (e: Exception) {
			Timber.e(e, "Error launching stream")
			Utils.showToast(requireContext(), getString(R.string.error_launching_stream))
		}
	}
}


