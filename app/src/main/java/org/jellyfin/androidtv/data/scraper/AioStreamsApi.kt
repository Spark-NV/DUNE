package org.jellyfin.androidtv.data.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.preference.UserPreferences
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern
import kotlinx.coroutines.withContext

/**
 * AIOStreams API client for fetching stream links
 * Based on the ElfHosted AIOStreams Stremio addon API
 */
class AioStreamsApi(private val userPreferences: UserPreferences) {
	companion object {
		private const val BASE_URL = "https://aiostreams.elfhosted.com/stremio"
	}

	/**
	 * Get streams for a movie or TV episode
	 * @param imdbId IMDB ID (e.g., "tt1234567")
	 * @param isMovie true for movies, false for TV shows
	 * @param seasonNumber Season number (only for TV shows)
	 * @param episodeNumber Episode number (only for TV shows)
	 * @return List of parsed stream data
	 */
	suspend fun getStreams(
		imdbId: String,
		isMovie: Boolean,
		seasonNumber: Int = 0,
		episodeNumber: Int = 0
	): List<StreamData> = withContext(Dispatchers.IO) {
		if (imdbId.isEmpty()) {
			Timber.e("[AIOStreamsAPI] Error: IMDB ID is required")
			return@withContext emptyList()
		}

		if (!isMovie && (seasonNumber <= 0 || episodeNumber <= 0)) {
			Timber.e("[AIOStreamsAPI] Error: Season and episode numbers are required for TV shows")
			return@withContext emptyList()
		}

		val aioConfig = userPreferences[UserPreferences.aiostreamsConfig]
		if (aioConfig.isEmpty()) {
			Timber.e("[AIOStreamsAPI] Error: AIOStreams config is required")
			return@withContext emptyList()
		}

		val mediaType = if (isMovie) "movie" else "series"
		var endpoint = "$BASE_URL/$aioConfig/stream/$mediaType/$imdbId"

		if (!isMovie) {
			endpoint += "%3A$seasonNumber%3A$episodeNumber"
		}

		endpoint += ".json"

		Timber.d("[AIOStreamsAPI] Request URL: $endpoint")

		try {
			val url = URL(endpoint)
			val connection = url.openConnection() as HttpURLConnection
			connection.requestMethod = "GET"
			connection.setRequestProperty("Host", "aiostreams.elfhosted.com")
			connection.setRequestProperty("Connection", "keep-alive")
			connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36")
			connection.setRequestProperty("Accept", "*/*")
			connection.setRequestProperty("Origin", "https://web.stremio.com")
			connection.setRequestProperty("Sec-Fetch-Site", "cross-site")
			connection.setRequestProperty("Sec-Fetch-Mode", "cors")
			connection.setRequestProperty("Sec-Fetch-Dest", "empty")
			connection.setRequestProperty("Referer", "https://web.stremio.com/")
			connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
			connection.connectTimeout = 30000
			connection.readTimeout = 30000

			val responseCode = connection.responseCode
			Timber.d("[AIOStreamsAPI] HTTP Response Code: $responseCode")

			if (responseCode != HttpURLConnection.HTTP_OK) {
				Timber.e("[AIOStreamsAPI] HTTP error: $responseCode")
				try {
					val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error body"
					Timber.e("[AIOStreamsAPI] Error response: ${errorBody.take(500)}")
				} catch (e: Exception) {
					Timber.e("[AIOStreamsAPI] Could not read error response: ${e.message}")
				}
				return@withContext emptyList()
			}

			val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
			connection.disconnect()

			Timber.d("[AIOStreamsAPI] Response body (first 1000 chars): ${responseBody.take(1000)}")
			if (responseBody.length > 1000) {
				Timber.d("[AIOStreamsAPI] Response body continues... (total length: ${responseBody.length})")
			}

			parseStreams(responseBody)
		} catch (e: Exception) {
			Timber.e(e, "[AIOStreamsAPI] Error fetching streams")
			return@withContext emptyList()
		}
	}

	private fun parseStreams(responseBody: String): List<StreamData> {
		val streams = mutableListOf<StreamData>()

		try {
			val json = Json { ignoreUnknownKeys = true }
			val response = json.decodeFromString<AioStreamsResponse>(responseBody)

			if (response.streams == null) {
				return streams
			}

			for (stream in response.streams) {
				val parsed = parseSingleStream(stream)
				if (parsed != null) {
					streams.add(parsed)
				}
			}
		} catch (e: Exception) {
			Timber.e(e, "[AIOStreamsAPI] Error parsing response")
		}

		return streams
	}

	private fun parseSingleStream(stream: AioStreamsStream): StreamData? {
		val url = stream.url ?: return null
		val name = stream.name ?: ""
		val description = stream.description ?: ""
		val filename = stream.behaviorHints?.filename ?: ""
		val videoSize = stream.behaviorHints?.videoSize ?: 0
		val parsedFile = stream.streamData?.parsedFile

		var quality = "unknown"
		val qualityPattern = Pattern.compile("(\\d+p|4K)", Pattern.CASE_INSENSITIVE)
		val qualityMatcher = qualityPattern.matcher(name)
		if (qualityMatcher.find()) {
			quality = qualityMatcher.group(1).lowercase()
		}

		var seeds = 0
		val seedsPattern = Pattern.compile("👤\\s*(\\d+)")
		val seedsMatcher = seedsPattern.matcher(description)
		if (seedsMatcher.find()) {
			seeds = seedsMatcher.group(1).toIntOrNull() ?: 0
		}

		var fileSize = "Unknown"
		if (videoSize > 0) {
			fileSize = if (videoSize >= 1024 * 1024 * 1024) {
				"%.2f GB".format(videoSize / (1024.0 * 1024.0 * 1024.0))
			} else {
				"%.2f MB".format(videoSize / (1024.0 * 1024.0))
			}
		} else {
			val sizePattern = Pattern.compile("💾\\s*([\\d.]+)\\s*(GB|MB|GiB|MiB)")
			val sizeMatcher = sizePattern.matcher(description)
			if (sizeMatcher.find()) {
				fileSize = "${sizeMatcher.group(1)} ${sizeMatcher.group(2)}"
			}
		}

		var source = "AIOStreams"
		val sourcePattern = Pattern.compile("⚙️\\s*(.+)$")
		val sourceMatcher = sourcePattern.matcher(description)
		if (sourceMatcher.find()) {
			source = sourceMatcher.group(1).trim()
		}

		val hdrFormats = mutableListOf<String>()
		val filenameLower = filename.lowercase()

		if (parsedFile?.visualTags != null) {
			for (tag in parsedFile.visualTags) {
				val tagStr = tag.toString().lowercase()
				when {
					"hdr10+" in tagStr || "hdr10plus" in tagStr -> {
						if ("HDR10+" !in hdrFormats) hdrFormats.add("HDR10+")
					}
					"hdr10" in tagStr -> {
						if ("HDR10" !in hdrFormats) hdrFormats.add("HDR10")
					}
					"hdr" in tagStr -> {
						if ("HDR" !in hdrFormats) hdrFormats.add("HDR")
					}
				}
			}
		}

		if (Pattern.compile("\\b(dolby.?vision|dv)\\b", Pattern.CASE_INSENSITIVE).matcher(filenameLower).find()) {
			if ("Dolby Vision" !in hdrFormats) hdrFormats.add("Dolby Vision")
		}
		if (hdrFormats.isEmpty() && Pattern.compile("\\bhdr\\b|\\.hdr\\.", Pattern.CASE_INSENSITIVE).matcher(filenameLower).find()) {
			hdrFormats.add("HDR")
		}

		if (hdrFormats.isEmpty()) {
			hdrFormats.add("SDR")
		}

		var codec = "unknown"
		if (parsedFile?.encode != null && parsedFile.encode.isNotEmpty()) {
			codec = parsedFile.encode.lowercase()
		}
		if (codec == "unknown" || codec.isEmpty()) {
			val codecPattern = Pattern.compile("(?i)(x264|x265|HEVC|AVC|AV1)")
			val codecMatcher = codecPattern.matcher(filename)
			if (codecMatcher.find()) {
				codec = codecMatcher.group(1)
			}
		}

		var release = "unknown"
		if (parsedFile?.quality != null && parsedFile.quality.isNotEmpty() && parsedFile.quality.uppercase() != "UNKNOWN") {
			release = parsedFile.quality.uppercase()
		}
		if (release == "unknown" || release.isEmpty()) {
			val releasePattern = Pattern.compile("(?i)(BluRay|WEB-DL|HDTV|REMUX|WEBRip|BDRip)")
			val releaseMatcher = releasePattern.matcher(filename)
			if (releaseMatcher.find()) {
				release = releaseMatcher.group(1).uppercase()
			}
		}

		var isAtmos = false
		if (parsedFile?.audioTags != null) {
			for (tag in parsedFile.audioTags) {
				if ("atmos" in tag.toString().lowercase()) {
					isAtmos = true
					break
				}
			}
		}
		if (!isAtmos) {
			isAtmos = "atmos" in filenameLower
		}

		var audioChannels = 2
		if (parsedFile?.audioChannels != null && parsedFile.audioChannels.isNotEmpty()) {
			val channelStr = parsedFile.audioChannels[0].toString()
			val channelPattern = Pattern.compile("(\\d+)\\.(\\d+)")
			val channelMatcher = channelPattern.matcher(channelStr)
			if (channelMatcher.find()) {
				audioChannels = channelMatcher.group(1).toIntOrNull() ?: 2
			}
		}

		val streamData = StreamData(
			id = url,
			url = url,
			title = description,
			name = name,
			filename = filename,
			quality = quality,
			seeds = seeds,
			fileSize = fileSize,
			source = source,
			provider = "AIOStreams",
			hdrFormats = hdrFormats,
			codec = codec,
			isAtmos = isAtmos,
			release = release,
			audioChannels = audioChannels
		)
		return streamData
	}

	@Serializable
	private data class AioStreamsResponse(
		val streams: List<AioStreamsStream>? = null
	)

	@Serializable
	private data class AioStreamsStream(
		val name: String? = null,
		val description: String? = null,
		val url: String? = null,
		val behaviorHints: AioBehaviorHints? = null,
		val streamData: AioStreamData? = null
	)

	@Serializable
	private data class AioBehaviorHints(
		val filename: String? = null,
		val videoSize: Long? = null
	)

	@Serializable
	private data class AioStreamData(
		val parsedFile: ParsedFile? = null
	)

	@Serializable
	private data class ParsedFile(
		val visualTags: List<String>? = null,
		val encode: String? = null,
		val quality: String? = null,
		val audioTags: List<String>? = null,
		val audioChannels: List<String>? = null
	)
}

