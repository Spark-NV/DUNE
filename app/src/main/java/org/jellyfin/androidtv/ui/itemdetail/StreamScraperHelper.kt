package org.jellyfin.androidtv.ui.itemdetail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.scraper.AioStreamsApi
import org.jellyfin.androidtv.data.scraper.StreamData
import org.jellyfin.androidtv.data.scraper.TorrentioApi
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.androidtv.preference.constant.StreamSortBy
import org.jellyfin.androidtv.preference.constant.StreamMinSizeMovies
import org.jellyfin.androidtv.preference.constant.StreamMaxSizeMovies
import org.jellyfin.androidtv.preference.constant.StreamMinSizeEpisodes
import org.jellyfin.androidtv.preference.constant.StreamMaxSizeEpisodes
import timber.log.Timber

class StreamScraperHelper(
	private val userPreferences: UserPreferences,
	private val scope: CoroutineScope,
	private val api: ApiClient
) {
	/**
	 * Parse file size string into bytes for sorting
	 * Supports formats like "29.35 GB", "500.2 MB", etc.
	 */
	private fun parseFileSize(fileSize: String): Long {
		if (fileSize == "Unknown") return 0L

		val regex = Regex("([\\d.]+)\\s*(GB|MB|GiB|MiB)")
		val match = regex.find(fileSize) ?: return 0L

		val size = match.groupValues[1].toDoubleOrNull() ?: return 0L
		val unit = match.groupValues[2]

		return when (unit.uppercase()) {
			"GB", "GIB" -> (size * 1024 * 1024 * 1024).toLong()
			"MB", "MIB" -> (size * 1024 * 1024).toLong()
			else -> 0L
		}
	}

	/**
	 * Sort streams based on user preference
	 */
	private fun sortStreams(streams: List<StreamData>, sortBy: StreamSortBy): List<StreamData> {
		return when (sortBy) {
			StreamSortBy.SIZE_DESCENDING -> streams.sortedByDescending { parseFileSize(it.fileSize) }
			StreamSortBy.SIZE_ASCENDING -> streams.sortedBy { parseFileSize(it.fileSize) }
		}
	}

	/**
	 * Filter streams based on size preferences
	 */
	private fun filterStreams(
		streams: List<StreamData>,
		isMovie: Boolean,
		minSizeMovies: StreamMinSizeMovies,
		maxSizeMovies: StreamMaxSizeMovies,
		minSizeEpisodes: StreamMinSizeEpisodes,
		maxSizeEpisodes: StreamMaxSizeEpisodes
	): List<StreamData> {
		return streams.filter { stream ->
			val streamSizeBytes = parseFileSize(stream.fileSize)

			// Skip streams with unknown sizes
			if (streamSizeBytes == 0L) return@filter false

			val minSize: Long
			val maxSize: Long
			val minDisabled: Any
			val maxDisabled: Any

			if (isMovie) {
				minSize = minSizeMovies.sizeInBytes
				maxSize = maxSizeMovies.sizeInBytes
				minDisabled = StreamMinSizeMovies.DISABLED
				maxDisabled = StreamMaxSizeMovies.DISABLED
			} else {
				minSize = minSizeEpisodes.sizeInBytes
				maxSize = maxSizeEpisodes.sizeInBytes
				minDisabled = StreamMinSizeEpisodes.DISABLED
				maxDisabled = StreamMaxSizeEpisodes.DISABLED
			}

			// Check minimum size (if not disabled)
			if ((if (isMovie) minSizeMovies else minSizeEpisodes) != minDisabled && streamSizeBytes < minSize) {
				return@filter false
			}

			// Check maximum size (if not disabled)
			if ((if (isMovie) maxSizeMovies else maxSizeEpisodes) != maxDisabled && streamSizeBytes > maxSize) {
				return@filter false
			}

			true
		}
	}
	private val torrentioApi = TorrentioApi(userPreferences)
	private val aioStreamsApi = AioStreamsApi(userPreferences)

	fun queryStreams(
		item: BaseItemDto,
		onComplete: (List<StreamData>) -> Unit
	) {
		scope.launch(Dispatchers.Main) {
			val torrentioEnabled = userPreferences[UserPreferences.torrentioEnabled]
			val aiostreamsEnabled = userPreferences[UserPreferences.aiostreamsEnabled]

			if (!torrentioEnabled && !aiostreamsEnabled) {
				Timber.d("No scrapers enabled")
				onComplete(emptyList())
				return@launch
			}

			// Determine if movie or episode
			val isMovie = item.type == BaseItemKind.MOVIE

			// Get IMDB ID - for episodes, use the series IMDB ID, not the episode IMDB ID
			val imdbId = if (!isMovie && item.seriesId != null) {
				// For episodes, fetch the series item to get its IMDB ID
				try {
					val seriesResponse = withContext(Dispatchers.IO) {
						api.userLibraryApi.getItem(item.seriesId!!)
					}
					val seriesImdbId = seriesResponse.content.providerIds?.get("Imdb")
					if (seriesImdbId.isNullOrEmpty()) {
						Timber.w("No IMDB ID found for series: ${seriesResponse.content.name}")
						null
					} else {
						Timber.d("Using series IMDB ID: $seriesImdbId for episode: ${item.name}")
						seriesImdbId
					}
				} catch (e: Exception) {
					Timber.e(e, "Failed to fetch series item for episode: ${item.name}")
					null
				}
			} else {
				// For movies or when seriesId is not available, use the item's IMDB ID
				item.providerIds?.get("Imdb")
			}

			if (imdbId == null || imdbId.isEmpty()) {
				Timber.w("No IMDB ID found for item: ${item.name}")
				onComplete(emptyList())
				return@launch
			}

			// At this point imdbId is guaranteed to be non-null and non-empty
			val safeImdbId = imdbId

			val seasonNumber = if (!isMovie) item.parentIndexNumber ?: 0 else 0
			val episodeNumber = if (!isMovie) item.indexNumber ?: 0 else 0

			if (!isMovie && (seasonNumber <= 0 || episodeNumber <= 0)) {
				Timber.w("Invalid season/episode numbers for TV show")
				onComplete(emptyList())
				return@launch
			}

			// Query enabled scrapers in parallel
			val deferredResults = mutableListOf<kotlinx.coroutines.Deferred<List<StreamData>>>()

			Timber.d("[StreamScraperHelper] Querying streams for IMDB ID: $safeImdbId, isMovie: $isMovie, season: $seasonNumber, episode: $episodeNumber")

			if (torrentioEnabled) {
				Timber.d("[StreamScraperHelper] Querying Torrentio...")
				deferredResults.add(
					async(Dispatchers.IO) {
						try {
							val result = torrentioApi.getStreams(safeImdbId, isMovie, seasonNumber, episodeNumber)
							Timber.d("[StreamScraperHelper] Torrentio returned ${result.size} streams")
							result
						} catch (e: Exception) {
							Timber.e(e, "Error querying Torrentio")
							emptyList()
						}
					}
				)
			}

			if (aiostreamsEnabled) {
				Timber.d("[StreamScraperHelper] Querying AIOStreams...")
				deferredResults.add(
					async(Dispatchers.IO) {
						try {
							val result = aioStreamsApi.getStreams(safeImdbId, isMovie, seasonNumber, episodeNumber)
							Timber.d("[StreamScraperHelper] AIOStreams returned ${result.size} streams")
							result
						} catch (e: Exception) {
							Timber.e(e, "Error querying AIOStreams")
							emptyList()
						}
					}
				)
			}

			// Wait for all results
			val results = deferredResults.awaitAll()
			val allStreams = results.flatten()

			// Apply sorting based on user preference
			val sortBy = userPreferences[UserPreferences.streamSortBy]
			val sortedStreams = sortStreams(allStreams, sortBy)

			// Apply size filtering based on user preferences
			val minSizeMovies = userPreferences[UserPreferences.streamMinSizeMovies]
			val maxSizeMovies = userPreferences[UserPreferences.streamMaxSizeMovies]
			val minSizeEpisodes = userPreferences[UserPreferences.streamMinSizeEpisodes]
			val maxSizeEpisodes = userPreferences[UserPreferences.streamMaxSizeEpisodes]

			val filteredStreams = filterStreams(
				sortedStreams,
				isMovie,
				minSizeMovies,
				maxSizeMovies,
				minSizeEpisodes,
				maxSizeEpisodes
			)

			Timber.d("Found ${allStreams.size} streams total, ${filteredStreams.size} after filtering, sorted by $sortBy")
			onComplete(filteredStreams)
		}
	}
}

