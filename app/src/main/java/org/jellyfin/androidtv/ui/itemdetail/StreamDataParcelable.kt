package org.jellyfin.androidtv.ui.itemdetail

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.jellyfin.androidtv.data.scraper.StreamData

@Parcelize
data class StreamDataParcelable(
	val id: String,
	val url: String,
	val title: String,
	val name: String,
	val filename: String,
	val quality: String,
	val seeds: Int,
	val fileSize: String,
	val source: String,
	val provider: String,
	val hdrFormats: List<String>,
	val codec: String,
	val isAtmos: Boolean,
	val release: String,
	val audioChannels: Int
) : Parcelable {
	fun toStreamData(): StreamData {
		return StreamData(
			id = id,
			url = url,
			title = title,
			name = name,
			filename = filename,
			quality = quality,
			seeds = seeds,
			fileSize = fileSize,
			source = source,
			provider = provider,
			hdrFormats = hdrFormats,
			codec = codec,
			isAtmos = isAtmos,
			release = release,
			audioChannels = audioChannels
		)
	}

	companion object {
		fun from(streamData: StreamData): StreamDataParcelable {
			return StreamDataParcelable(
				id = streamData.id,
				url = streamData.url,
				title = streamData.title,
				name = streamData.name,
				filename = streamData.filename,
				quality = streamData.quality,
				seeds = streamData.seeds,
				fileSize = streamData.fileSize,
				source = streamData.source,
				provider = streamData.provider,
				hdrFormats = streamData.hdrFormats,
				codec = streamData.codec,
				isAtmos = streamData.isAtmos,
				release = streamData.release,
				audioChannels = streamData.audioChannels
			)
		}
	}
}

