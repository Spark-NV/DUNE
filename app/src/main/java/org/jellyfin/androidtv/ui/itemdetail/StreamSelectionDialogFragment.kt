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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.scraper.StreamData
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

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
            Timber.d("[StreamSelectionDialog] Launching stream URL: $url")

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
            Timber.d("[StreamSelectionDialog] Parsed URI: $videoUri")

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

            // Log the complete intent details
            Timber.d("[StreamSelectionDialog] Intent action: ${intent.action}")
            Timber.d("[StreamSelectionDialog] Intent data: ${intent.data}")
            Timber.d("[StreamSelectionDialog] Intent type: ${intent.type}")
            Timber.d("[StreamSelectionDialog] Intent package: ${intent.`package`}")
            Timber.d("[StreamSelectionDialog] Intent flags: ${intent.flags}")
            Timber.d("[StreamSelectionDialog] Intent extras: ${intent.extras?.keySet()?.joinToString { key ->
                val value = intent.extras?.get(key)
                "$key=$value"
            } ?: "none"}")

            requireContext().startActivity(intent)
            Timber.d("[StreamSelectionDialog] Stream launched successfully")
        } catch (e: android.content.ActivityNotFoundException) {
            Timber.e(e, "[StreamSelectionDialog] Player app not found")
            Utils.showToast(requireContext(), "External player app not found. Please install com.brouken.player")
        } catch (e: Exception) {
            Timber.e(e, "[StreamSelectionDialog] Error launching stream")
            Utils.showToast(requireContext(), getString(R.string.error_launching_stream))
        }
    }

    override fun onStreamItemClicked(streamData: StreamData?) {
        if (streamData != null && streamData.url.isNotEmpty()) {
            // Check if URL might need redirect resolution (debrid services)
            if (needsRedirectResolution(streamData.url)) {
                Utils.showToast(requireContext(), getString(R.string.resolving_stream))
                resolveAndLaunchStream(streamData.url)
            } else {
                launchStream(streamData.url)
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
                    Timber.d("[StreamSelectionDialog] Resolved URL: $resolvedUrl")
                    launchStream(resolvedUrl)
                } else {
                    // Fallback to original URL if resolution fails
                    Timber.w("[StreamSelectionDialog] URL resolution failed, using original URL")
                    launchStream(url)
                }
            } catch (e: Exception) {
                Timber.e(e, "[StreamSelectionDialog] Error resolving URL")
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
                Timber.d("[StreamSelectionDialog] URL check: $currentUrl -> $responseCode")

                when (responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        // Check Content-Type to verify it's a video
                        val contentType = connection.contentType ?: ""
                        Timber.d("[StreamSelectionDialog] Content-Type: $contentType")
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
                            Timber.d("[StreamSelectionDialog] Following redirect to: $currentUrl")
                            redirectCount++
                        } else {
                            Timber.w("[StreamSelectionDialog] Redirect without Location header")
                            return currentUrl
                        }
                    }
                    else -> {
                        connection.disconnect()
                        Timber.w("[StreamSelectionDialog] Unexpected response code: $responseCode")
                        return currentUrl
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[StreamSelectionDialog] Error checking URL: $currentUrl")
                return currentUrl
            }
        }

        Timber.w("[StreamSelectionDialog] Max redirects reached")
        return currentUrl
    }

}
