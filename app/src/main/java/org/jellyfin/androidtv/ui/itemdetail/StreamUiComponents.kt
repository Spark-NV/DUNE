package org.jellyfin.androidtv.ui.itemdetail

import android.content.Context
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.scraper.StreamData
import org.jellyfin.androidtv.util.Utils
import timber.log.Timber

// Custom row for individual streams
data class StreamRow(val streamItem: StreamItem) : Row() {
    init {
        headerItem = HeaderItem("")
    }
}

// Presenter for stream rows
class StreamRowPresenter(private val clickListener: StreamItemClickListener) : RowPresenter() {
    init {
        headerPresenter = null // No header for individual rows
    }

    override fun createRowViewHolder(parent: android.view.ViewGroup): RowPresenter.ViewHolder {
        val view = StreamItemView(parent.context)
        view.clickListener = clickListener
        return ViewHolder(view)
    }

    override fun onBindRowViewHolder(vh: RowPresenter.ViewHolder, item: Any) {
        super.onBindRowViewHolder(vh, item)
        if (vh.view is StreamItemView && item is StreamRow) {
            val streamItemView = vh.view as StreamItemView
            streamItemView.bind(item.streamItem)
            streamItemView.streamData = item.streamItem.streamData
        }
    }

    override fun onUnbindRowViewHolder(vh: RowPresenter.ViewHolder) {
        super.onUnbindRowViewHolder(vh)
        if (vh.view is StreamItemView) {
            (vh.view as StreamItemView).unbind()
        }
    }
}

// Interface for stream item clicks
interface StreamItemClickListener {
    fun onStreamItemClicked(streamData: StreamData?)
}

// Custom view for stream items
class StreamItemView(context: android.content.Context) : android.widget.LinearLayout(context) {
    private val titleText: android.widget.TextView
    private val subtitleText: android.widget.TextView
    var clickListener: StreamItemClickListener? = null
    var streamData: StreamData? = null

    init {
        orientation = VERTICAL
        setPadding(
            Utils.convertDpToPixel(context, 32),
            Utils.convertDpToPixel(context, 16),
            Utils.convertDpToPixel(context, 32),
            Utils.convertDpToPixel(context, 16)
        )
        isFocusable = true
        isClickable = true

        titleText = android.widget.TextView(context).apply {
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        subtitleText = android.widget.TextView(context).apply {
            textSize = 14f
            setTextColor(android.graphics.Color.LTGRAY)
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        addView(titleText)
        addView(subtitleText)

        // Set focus ring background
        background = context.getDrawable(R.drawable.stream_item_focus_ring)

        // Handle clicks
        setOnClickListener {
            clickListener?.onStreamItemClicked(streamData)
        }
    }

    fun bind(streamItem: StreamItem) {
        titleText.text = streamItem.title
        subtitleText.text = streamItem.subtitle
    }

    fun unbind() {
        titleText.text = ""
        subtitleText.text = ""
    }
}

// Data class for stream items
data class StreamItem(
    val title: String,
    val subtitle: String,
    val streamData: StreamData? = null
) {
    companion object {
        fun fromStreamData(stream: StreamData): StreamItem {
            val title = stream.filename.ifEmpty { stream.name.ifEmpty { stream.title } }
            val metaParts = mutableListOf<String>()

            if (stream.fileSize.isNotEmpty() && stream.fileSize != "Unknown") {
                metaParts.add(stream.fileSize)
            }
            if (stream.quality.isNotEmpty() && stream.quality != "unknown") {
                metaParts.add(stream.quality.uppercase())
            }
            stream.hdrFormats.filter { it != "SDR" }.forEach { metaParts.add(it) }
            if (stream.codec.isNotEmpty() && stream.codec != "unknown") {
                metaParts.add(stream.codec.uppercase())
            }
            if (stream.isAtmos) {
                metaParts.add("Atmos")
            } else if (stream.audioChannels > 2) {
                metaParts.add("${stream.audioChannels}.1ch")
            }
            if (stream.seeds > 0) {
                metaParts.add("👤 ${stream.seeds}")
            }
            metaParts.add("[${stream.provider}]")
            if (stream.source.isNotEmpty() && stream.source != stream.provider) {
                metaParts.add("via ${stream.source}")
            }

            val subtitle = metaParts.joinToString(" • ")

            return StreamItem(title, subtitle, stream)
        }
    }
}
