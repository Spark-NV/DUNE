package org.jellyfin.androidtv.data.scraper

/**
 * Data class representing a stream from a scraper
 */
data class StreamData(
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
	val release: String = "unknown",
	val audioChannels: Int = 2
)

