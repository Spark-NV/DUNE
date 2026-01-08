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
	 * @param isAnime true to use the anime endpoint instead of series (only applies to TV shows)
	 * @return List of parsed stream data
	 */
	suspend fun getStreams(
		imdbId: String,
		isMovie: Boolean,
		seasonNumber: Int = 0,
		episodeNumber: Int = 0,
		isAnime: Boolean = false
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

		// For movies, always use "movie". For TV shows, use "anime" if isAnime is true, otherwise "series"
		val mediaType = when {
			isMovie -> "movie"
			isAnime -> "anime"
			else -> "series"
		}
		var endpoint = "$BASE_URL/$aioConfig/stream/$mediaType/$imdbId"

		if (!isMovie) {
			endpoint += "%3A$seasonNumber%3A$episodeNumber"
		}

		endpoint += ".json"

		Timber.d("[AIOStreamsAPI] Request URL: $endpoint (mediaType: $mediaType, isAnime: $isAnime)")

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
		val bingeGroup = stream.behaviorHints?.bingeGroup ?: ""

		// Parse bingeGroup into tokens for metadata extraction
		// Format: "com.aiostreams.viren070|premiumize|false|1080p|BluRay|HEVC|DTS-HD MA|HDR10|DV|..."
		val bingeTokens = bingeGroup.split("|").map { it.trim().lowercase() }

		// Extract quality from name first, then bingeGroup
		var quality = "unknown"
		val qualityPattern = Pattern.compile("(\\d+p|4K)", Pattern.CASE_INSENSITIVE)
		val qualityMatcher = qualityPattern.matcher(name)
		if (qualityMatcher.find()) {
			quality = qualityMatcher.group(1).lowercase()
		} else {
			// Try bingeGroup tokens
			for (token in bingeTokens) {
				if (token.matches(Regex("\\d+p")) || token == "4k") {
					quality = token
					break
				}
			}
		}

		// Extract seeds from description
		var seeds = 0
		val seedsPattern = Pattern.compile("👤\\s*(\\d+)")
		val seedsMatcher = seedsPattern.matcher(description)
		if (seedsMatcher.find()) {
			seeds = seedsMatcher.group(1).toIntOrNull() ?: 0
		}

		// Calculate file size
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

		// Extract source from description
		var source = "AIOStreams"
		val sourcePattern = Pattern.compile("⚙️\\s*(.+)$", Pattern.MULTILINE)
		val sourceMatcher = sourcePattern.matcher(description)
		if (sourceMatcher.find()) {
			source = sourceMatcher.group(1).trim()
		}

		// Detect HDR formats from bingeGroup, name, and filename
		val hdrFormats = mutableListOf<String>()
		val filenameLower = filename.lowercase()
		val nameLower = name.lowercase()
		val combinedText = "$nameLower $filenameLower ${bingeTokens.joinToString(" ")}"

		// Check for HDR10+
		if (combinedText.contains("hdr10+") || combinedText.contains("hdr10plus")) {
			hdrFormats.add("HDR10+")
		}
		// Check for Dolby Vision
		if (combinedText.contains("dolby vision") || combinedText.contains("dolbyvision") ||
			Regex("\\bdv\\b").containsMatchIn(combinedText) ||
			bingeTokens.contains("dv")) {
			if ("Dolby Vision" !in hdrFormats) hdrFormats.add("Dolby Vision")
		}
		// Check for HDR10
		if (bingeTokens.contains("hdr10") || combinedText.contains("hdr10")) {
			if ("HDR10" !in hdrFormats && "HDR10+" !in hdrFormats) hdrFormats.add("HDR10")
		}
		// Check for generic HDR
		if (hdrFormats.isEmpty() && (bingeTokens.contains("hdr") ||
			Pattern.compile("\\bhdr\\b|\\.hdr\\.", Pattern.CASE_INSENSITIVE).matcher(combinedText).find())) {
			hdrFormats.add("HDR")
		}

		if (hdrFormats.isEmpty()) {
			hdrFormats.add("SDR")
		}

		// Extract codec from bingeGroup, then filename
		var codec = "unknown"
		val codecTokens = listOf("hevc", "x265", "avc", "x264", "av1", "h265", "h264")
		for (token in bingeTokens) {
			if (token in codecTokens) {
				codec = token
				break
			}
		}
		if (codec == "unknown") {
			val codecPattern = Pattern.compile("(?i)(x264|x265|HEVC|AVC|AV1|H\\.?265|H\\.?264)")
			val codecMatcher = codecPattern.matcher(filename)
			if (codecMatcher.find()) {
				codec = codecMatcher.group(1)
			}
		}

		// Extract release quality from bingeGroup, then filename
		var release = "unknown"
		val releaseTokens = mapOf(
			"bluray" to "BLURAY",
			"blu-ray" to "BLURAY",
			"bluray remux" to "REMUX",
			"remux" to "REMUX",
			"web-dl" to "WEB-DL",
			"webdl" to "WEB-DL",
			"webrip" to "WEBRIP",
			"hdtv" to "HDTV",
			"bdrip" to "BDRIP",
			"dvdrip" to "DVDRIP",
			"hdrip" to "HDRIP"
		)
		for (token in bingeTokens) {
			if (releaseTokens.containsKey(token)) {
				release = releaseTokens[token]!!
				break
			}
		}
		if (release == "unknown") {
			val releasePattern = Pattern.compile("(?i)(BluRay|WEB-DL|HDTV|REMUX|WEBRip|BDRip|DVDRip|HDRip)")
			val releaseMatcher = releasePattern.matcher(filename)
			if (releaseMatcher.find()) {
				release = releaseMatcher.group(1).uppercase()
			}
		}

		// Detect Atmos from bingeGroup, name, and filename
		var isAtmos = bingeTokens.contains("atmos") ||
			combinedText.contains("atmos")

		// Extract audio channels from filename or description
		var audioChannels = 2
		val channelPattern = Pattern.compile("(\\d+)\\.(\\d+)")
		val channelMatcher = channelPattern.matcher("$filename $description")
		if (channelMatcher.find()) {
			audioChannels = channelMatcher.group(1).toIntOrNull() ?: 2
		} else {
			// Check bingeGroup for audio indicators
			if (bingeTokens.any { it.contains("7.1") }) audioChannels = 7
			else if (bingeTokens.any { it.contains("5.1") }) audioChannels = 5
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
		val behaviorHints: AioBehaviorHints? = null
	)

	@Serializable
	private data class AioBehaviorHints(
		val filename: String? = null,
		val videoSize: Long? = null,
		val bingeGroup: String? = null,
		val videoHash: String? = null
	)
}

