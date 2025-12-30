package org.jellyfin.androidtv.data.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.preference.UserPreferences
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Torrentio API client for fetching stream links
 * Based on the Stremio Torrentio addon API
 */
class TorrentioApi(private val userPreferences: UserPreferences) {
	companion object {
		private const val BASE_URL = "https://torrentio.strem.fun"
		private const val PROVIDERS = "yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentgalaxy,magnetdl,horriblesubs,nyaasi,tokyotosho,anidex,rutor,rutracker"
		private const val DEBRID_OPTIONS = "nodownloadlinks"
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
			Timber.e("[TorrentioApi] Error: IMDB ID is required")
			return@withContext emptyList()
		}

		if (!isMovie && (seasonNumber <= 0 || episodeNumber <= 0)) {
			Timber.e("[TorrentioApi] Error: Season and episode numbers are required for TV shows")
			return@withContext emptyList()
		}

		val premiumizeKey = userPreferences[UserPreferences.premiumizeApiKey]
		val premiumizeParam = if (premiumizeKey.isNotEmpty()) premiumizeKey else ""

		val mediaType = if (isMovie) "movie" else "series"
		var endpoint = "$BASE_URL/providers=$PROVIDERS|debridoptions=$DEBRID_OPTIONS|premiumize=$premiumizeParam/stream/$mediaType/$imdbId"

		if (!isMovie) {
			endpoint += ":$seasonNumber:$episodeNumber"
		}

		endpoint += ".json"

		Timber.d("[TorrentioApi] Request URL: $endpoint")

		try {
			val url = URL(endpoint)
			val connection = url.openConnection() as HttpURLConnection
			connection.requestMethod = "GET"
			connection.setRequestProperty("Host", "torrentio.strem.fun")
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
			Timber.d("[TorrentioApi] HTTP Response Code: $responseCode")

			if (responseCode != HttpURLConnection.HTTP_OK) {
				Timber.e("[TorrentioApi] HTTP error: $responseCode")
				try {
					val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error body"
					Timber.e("[TorrentioApi] Error response: ${errorBody.take(500)}")
				} catch (e: Exception) {
					Timber.e("[TorrentioApi] Could not read error response: ${e.message}")
				}
				return@withContext emptyList()
			}

			val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
			connection.disconnect()

			Timber.d("[TorrentioApi] Response body (first 1000 chars): ${responseBody.take(1000)}")
			if (responseBody.length > 1000) {
				Timber.d("[TorrentioApi] Response body continues... (total length: ${responseBody.length})")
			}

			parseStreams(responseBody)
		} catch (e: Exception) {
			Timber.e(e, "[TorrentioApi] Error fetching streams")
			return@withContext emptyList()
		}
	}

	private fun parseStreams(responseBody: String): List<StreamData> {
		val streams = mutableListOf<StreamData>()

		try {
			val json = Json { ignoreUnknownKeys = true }
			val response = json.decodeFromString<TorrentioResponse>(responseBody)

			if (response.streams == null) {
				Timber.d("[TorrentioApi] No streams found in response")
				return streams
			}

			for (stream in response.streams) {
				val parsed = parseSingleStream(stream)
				if (parsed != null) {
					streams.add(parsed)
				}
			}

			Timber.d("[TorrentioApi] Parsed ${streams.size} streams")
		} catch (e: Exception) {
			Timber.e(e, "[TorrentioApi] Error parsing response")
		}

		return streams
	}

	private fun parseSingleStream(stream: TorrentioStream): StreamData? {
		val url = stream.url ?: return null
		val title = stream.title ?: ""
		val name = stream.name ?: ""
		val filename = stream.behaviorHints?.filename ?: ""

		var quality = "unknown"
		val qualityPattern = Pattern.compile("(\\d+p|4K)", Pattern.CASE_INSENSITIVE)
		val qualityMatcher = qualityPattern.matcher(name)
		if (qualityMatcher.find()) {
			quality = qualityMatcher.group(1).lowercase()
		}

		var seeds = 0
		val seedsPattern = Pattern.compile("👤\\s*(\\d+)")
		val seedsMatcher = seedsPattern.matcher(title)
		if (seedsMatcher.find()) {
			seeds = seedsMatcher.group(1).toIntOrNull() ?: 0
		}

		var fileSize = "Unknown"
		val sizePattern = Pattern.compile("💾\\s*([\\d.]+)\\s*(GB|MB|GiB|MiB)")
		val sizeMatcher = sizePattern.matcher(title)
		if (sizeMatcher.find()) {
			fileSize = "${sizeMatcher.group(1)} ${sizeMatcher.group(2)}"
		}

		var source = "Torrentio"
		val sourcePattern = Pattern.compile("⚙️\\s*(.+)$")
		val sourceMatcher = sourcePattern.matcher(title)
		if (sourceMatcher.find()) {
			source = sourceMatcher.group(1).trim()
		}

		val hdrFormats = mutableListOf<String>()
		val filenameLower = filename.lowercase()

		if (Pattern.compile("\\b(dolby.?vision|dv)\\b", Pattern.CASE_INSENSITIVE).matcher(filenameLower).find()) {
			hdrFormats.add("Dolby Vision")
		}
		if (Pattern.compile("\\bhdr.?10.?\\+|\\bhdr.?10.?plus\\b", Pattern.CASE_INSENSITIVE).matcher(filenameLower).find()) {
			hdrFormats.add("HDR10+")
		} else if (Pattern.compile("\\bhdr.?10\\b", Pattern.CASE_INSENSITIVE).matcher(filenameLower).find()) {
			hdrFormats.add("HDR10")
		} else if (Pattern.compile("\\bhdr\\b|\\.hdr\\.", Pattern.CASE_INSENSITIVE).matcher(filenameLower).find()) {
			hdrFormats.add("HDR")
		}

		if (hdrFormats.isEmpty()) {
			hdrFormats.add("SDR")
		}

		var codec = "unknown"
		val codecPattern = Pattern.compile("(?i)(x264|x265|HEVC|AVC|AV1)")
		val codecMatcher = codecPattern.matcher(filename)
		if (codecMatcher.find()) {
			codec = codecMatcher.group(1)
		}

		val isAtmos = "atmos" in filenameLower

		return StreamData(
			id = url,
			url = url,
			title = title,
			name = name,
			filename = filename,
			quality = quality,
			seeds = seeds,
			fileSize = fileSize,
			source = source,
			provider = "Torrentio",
			hdrFormats = hdrFormats,
			codec = codec,
			isAtmos = isAtmos
		)
	}

	@Serializable
	private data class TorrentioResponse(
		val streams: List<TorrentioStream>? = null
	)

	@Serializable
	private data class TorrentioStream(
		val title: String? = null,
		val name: String? = null,
		val url: String? = null,
		val behaviorHints: BehaviorHints? = null
	)

	@Serializable
	private data class BehaviorHints(
		val filename: String? = null
	)
}

