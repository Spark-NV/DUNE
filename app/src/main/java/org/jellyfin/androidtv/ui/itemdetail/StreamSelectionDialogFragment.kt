package org.jellyfin.androidtv.ui.itemdetail

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.scraper.StreamData
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.koin.android.ext.android.inject
import timber.log.Timber

class StreamSelectionDialogFragment : DialogFragment(), StreamItemClickListener {
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


    companion object {
        private const val ARG_STREAMS = "streams"
        private const val ARG_MEDIA_TITLE = "media_title"
        private const val ARG_MEDIA_SUBTITLE = "media_subtitle"
        private const val ARG_ITEM_ID = "item_id"
        private const val ARG_RESUME_POSITION = "resume_position"

        @JvmStatic
        fun newInstance(
            streams: List<StreamData>,
            mediaTitle: String,
            mediaSubtitle: String = "",
            itemId: String = "",
            resumePosition: Int = 0
        ): StreamSelectionDialogFragment {
            return StreamSelectionDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_STREAMS, ArrayList(streams.map { StreamDataParcelable.from(it) }))
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
            val parcelables = it.getParcelableArrayList<StreamDataParcelable>(ARG_STREAMS)
            streams = parcelables?.map { it.toStreamData() } ?: emptyList()
            mediaTitle = it.getString(ARG_MEDIA_TITLE, "")
            mediaSubtitle = it.getString(ARG_MEDIA_SUBTITLE, "")
            itemId = it.getString(ARG_ITEM_ID, "")
            resumePosition = it.getInt(ARG_RESUME_POSITION, 0)
            Timber.d("[StreamSelectionDialogFragment] Loaded ${streams.size} streams, title: '$mediaTitle', subtitle: '$mediaSubtitle', itemId: '$itemId', resumePosition: '$resumePosition'")
        }

        // Make it full screen
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stream_selection, container, false)
        titleText = view.findViewById(R.id.titleText)
        subtitleText = view.findViewById(R.id.subtitleText)

        titleText?.text = getString(R.string.select_stream)
        subtitleText?.text = mediaSubtitle.ifEmpty { mediaTitle }

        rowsFragment = RowsSupportFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.rowsFragment, rowsFragment!!)
            .commit()

        setupRows()

        return view
    }

    override fun onStart() {
        super.onStart()
        // Make dialog full screen
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun setupRows() {
        val presenterSelector = ClassPresenterSelector()
        presenterSelector.addClassPresenter(StreamRow::class.java, StreamRowPresenter(this))

        rowsAdapter = MutableObjectAdapter(presenterSelector)
        rowsFragment?.adapter = rowsAdapter

        if (streams.isEmpty()) {
            // Show empty state
            val emptyItem = StreamRow(StreamItem(getString(R.string.no_streams_found), ""))
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


    private fun launchStream(url: String) {
        try {

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

            // Log the complete intent details
            Timber.d("{JellyfinIntentService} Intent action: ${intent.action}")
            Timber.d("{JellyfinIntentService} Intent data: ${intent.data}")
            Timber.d("{JellyfinIntentService} Intent type: ${intent.type}")
            Timber.d("{JellyfinIntentService} Intent package: ${intent.`package`}")
            Timber.d("{JellyfinIntentService} Intent flags: ${intent.flags}")
            Timber.d("{JellyfinIntentService} Intent extras: ${intent.extras?.keySet()?.joinToString { key ->
                val value = intent.extras?.get(key)
                "$key=$value"
            } ?: "none"}")
            Timber.d("{JellyfinIntentService} Raw intent: $intent")

            requireContext().startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Error launching stream")
            Utils.showToast(requireContext(), getString(R.string.error_launching_stream))
        }
    }

    override fun onStreamItemClicked(streamData: StreamData?) {
        if (streamData != null && streamData.url.isNotEmpty()) {
            // Keep dialog open after stream selection
            launchStream(streamData.url)
        } else {
            Utils.showToast(requireContext(), getString(R.string.invalid_stream_url))
        }
    }

}
