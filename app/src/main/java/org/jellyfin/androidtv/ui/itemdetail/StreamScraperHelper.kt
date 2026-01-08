package org.jellyfin.androidtv.ui.itemdetail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
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
import java.util.UUID
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

	/**
	 * Remove duplicate streams across providers based on filename.
	 * Prefers Torrentio streams over AIOStreams when duplicates are found.
	 */
	private fun removeDuplicateStreams(streams: List<StreamData>): List<StreamData> {
		val seenFilenames = mutableSetOf<String>()
		val result = mutableListOf<StreamData>()

		// Sort so Torrentio streams come first (they will be kept over AIOStreams duplicates)
		val sortedByProvider = streams.sortedBy { if (it.provider == "Torrentio") 0 else 1 }

		for (stream in sortedByProvider) {
			val filename = stream.filename.trim().lowercase()
			// Only dedupe if filename is non-empty
			if (filename.isEmpty() || filename !in seenFilenames) {
				if (filename.isNotEmpty()) {
					seenFilenames.add(filename)
				}
				result.add(stream)
			} else {
				Timber.d("[StreamScraperHelper] Removing duplicate stream: ${stream.filename} (${stream.provider})")
			}
		}

		Timber.d("[StreamScraperHelper] Removed ${streams.size - result.size} duplicate streams")
		return result
	}

	/**
	 * Check if an item belongs to the anime library by traversing its parent chain.
	 * Returns true if any ancestor's ID matches the anime library ID.
	 * @param item The item to check (should be a series, not an episode)
	 * @param animeLibraryId The UUID of the anime library
	 * @param maxDepth Maximum depth to traverse (to avoid too many API calls)
	 */
	private suspend fun isItemInAnimeLibrary(
		item: BaseItemDto,
		animeLibraryId: UUID,
		maxDepth: Int = 10
	): Boolean {
		val animeLibraryIdStr = animeLibraryId.toString().lowercase()
		Timber.d("[StreamScraperHelper] === Checking if '${item.name}' is in anime library ===")
		Timber.d("[StreamScraperHelper] Target anime library ID: $animeLibraryIdStr")
		Timber.d("[StreamScraperHelper] Item ID: ${item.id}, parentId: ${item.parentId}")

		// First check if the item's ID itself matches (in case it's directly in the library)
		if (item.id.toString().lowercase() == animeLibraryIdStr) {
			Timber.d("[StreamScraperHelper] Item itself is the anime library!")
			return true
		}

		// Also check the immediate parentId first
		val immediateParentIdStr = item.parentId?.toString()?.lowercase()
		Timber.d("[StreamScraperHelper] Comparing immediate parent: '$immediateParentIdStr' vs target: '$animeLibraryIdStr'")
		if (immediateParentIdStr == animeLibraryIdStr) {
			Timber.d("[StreamScraperHelper] ✓ MATCH on immediate parent! Item '${item.name}' is in anime library")
			return true
		}

		var currentParentId = item.parentId
		var depth = 0

		while (currentParentId != null && depth < maxDepth) {
			val currentIdStr = currentParentId.toString().lowercase()
			Timber.d("[StreamScraperHelper] Checking parent at depth $depth: $currentIdStr (target: $animeLibraryIdStr)")

			// Check if current parent matches the anime library (string comparison)
			if (currentIdStr == animeLibraryIdStr) {
				Timber.d("[StreamScraperHelper] ✓ MATCH! Item '${item.name}' is in anime library (found at depth $depth)")
				return true
			}

			// Fetch the parent item to continue traversing
			try {
				val parentResponse = withContext(Dispatchers.IO) {
					api.userLibraryApi.getItem(currentParentId!!)
				}
				val parentItem = parentResponse.content
				Timber.d("[StreamScraperHelper] Parent at depth $depth: name='${parentItem.name}', type=${parentItem.type}, id=${parentItem.id}, nextParentId=${parentItem.parentId}")
				currentParentId = parentItem.parentId
				depth++
			} catch (e: Exception) {
				Timber.e(e, "[StreamScraperHelper] Error fetching parent item at depth $depth")
				break
			}
		}

		Timber.d("[StreamScraperHelper] ✗ Item '${item.name}' is NOT in anime library (checked $depth levels, final parentId: $currentParentId)")
		Timber.d("[StreamScraperHelper]   Expected library ID: $animeLibraryIdStr")
		Timber.d("[StreamScraperHelper]   Item hierarchy checked: ${item.id} -> ${item.parentId} -> ... (traversed $depth levels)")
		return false
	}

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

			// Determine if this is anime content by checking if it belongs to the anime library
			// Only check if anime endpoint is enabled in settings
			val animeEndpointEnabled = userPreferences[UserPreferences.animeEndpointEnabled]
			val isAnime = if (!isMovie && animeEndpointEnabled) {
				val animeLibraryIdStr = userPreferences[UserPreferences.animeLibraryId]
				Timber.d("[StreamScraperHelper] Anime endpoint enabled, checking library membership. Current animeLibraryId: $animeLibraryIdStr")
				if (animeLibraryIdStr.isNotEmpty()) {
					try {
						// Handle UUID strings that might be missing dashes
						val normalizedAnimeLibraryIdStr = if (animeLibraryIdStr.length == 32 && !animeLibraryIdStr.contains("-")) {
							// Re-insert dashes into UUID format: 8-4-4-4-12
							"${animeLibraryIdStr.substring(0, 8)}-${animeLibraryIdStr.substring(8, 12)}-${animeLibraryIdStr.substring(12, 16)}-${animeLibraryIdStr.substring(16, 20)}-${animeLibraryIdStr.substring(20)}"
						} else {
							animeLibraryIdStr
						}
						val animeLibraryId = UUID.fromString(normalizedAnimeLibraryIdStr)
						Timber.d("[StreamScraperHelper] Normalized anime library ID: $normalizedAnimeLibraryIdStr -> $animeLibraryId")
						Timber.d("[StreamScraperHelper] Checking if item belongs to anime library: $animeLibraryId")
						// For episodes, we need to check the series
						val seriesItem = if (item.seriesId != null) {
							val fetchedSeries = withContext(Dispatchers.IO) {
								api.userLibraryApi.getItem(item.seriesId!!).content
							}
							Timber.d("[StreamScraperHelper] Fetched series: ${fetchedSeries.name}, parentId: ${fetchedSeries.parentId}")
							fetchedSeries
						} else {
							// If this is already a series, use the item itself
							Timber.d("[StreamScraperHelper] Using item directly: ${item.name}, parentId: ${item.parentId}")
							item
						}
						isItemInAnimeLibrary(seriesItem, animeLibraryId)
					} catch (e: Exception) {
						Timber.e(e, "[StreamScraperHelper] Error checking anime library membership")
						// If we can't parse the UUID, clear the invalid setting so user can re-select
						if (e is IllegalArgumentException && e.message?.contains("Invalid UUID string") == true) {
							Timber.w("[StreamScraperHelper] Clearing invalid anime library ID: $animeLibraryIdStr")
							userPreferences[UserPreferences.animeLibraryId] = ""
						}
						false
					}
				} else {
					// No anime library configured, always use series endpoint
					Timber.d("[StreamScraperHelper] No anime library configured, using series endpoint")
					false
				}
			} else if (!isMovie) {
				// Anime endpoint is disabled, use series endpoint
				Timber.d("[StreamScraperHelper] Anime endpoint disabled, using series endpoint")
				false
			} else {
				// Movies don't use anime endpoint, or anime endpoint is disabled
				if (!animeEndpointEnabled) {
					Timber.d("[StreamScraperHelper] Anime endpoint disabled in settings, using series endpoint")
				}
				false
			}

			// Query enabled scrapers
			// For anime content, we query both anime AND series endpoints with a 1 second delay between them
			val allStreams = mutableListOf<StreamData>()

			Timber.d("[StreamScraperHelper] Querying streams for IMDB ID: $safeImdbId, isMovie: $isMovie, isAnime: $isAnime, season: $seasonNumber, episode: $episodeNumber")

			if (isAnime && !isMovie) {
				// For anime: query anime endpoint first, then series endpoint after 1 second delay
				Timber.d("[StreamScraperHelper] Anime content detected - will query both anime and series endpoints")

				// First query: anime endpoint
				val animeResults = mutableListOf<kotlinx.coroutines.Deferred<List<StreamData>>>()

				if (torrentioEnabled) {
					Timber.d("[StreamScraperHelper] Querying Torrentio (anime endpoint)...")
					animeResults.add(
						async(Dispatchers.IO) {
							try {
								val result = torrentioApi.getStreams(safeImdbId, isMovie, seasonNumber, episodeNumber, true)
								Timber.d("[StreamScraperHelper] Torrentio (anime) returned ${result.size} streams")
								result
							} catch (e: Exception) {
								Timber.e(e, "Error querying Torrentio anime endpoint")
								emptyList()
							}
						}
					)
				}

				if (aiostreamsEnabled) {
					Timber.d("[StreamScraperHelper] Querying AIOStreams (anime endpoint)...")
					animeResults.add(
						async(Dispatchers.IO) {
							try {
								val result = aioStreamsApi.getStreams(safeImdbId, isMovie, seasonNumber, episodeNumber, true)
								Timber.d("[StreamScraperHelper] AIOStreams (anime) returned ${result.size} streams")
								result
							} catch (e: Exception) {
								Timber.e(e, "Error querying AIOStreams anime endpoint")
								emptyList()
							}
						}
					)
				}

				// Wait for anime results
				val animeStreams = animeResults.awaitAll().flatten()
				allStreams.addAll(animeStreams)
				Timber.d("[StreamScraperHelper] Got ${animeStreams.size} streams from anime endpoints")

				// 1 second delay before querying series endpoints
				delay(1000)

				// Second query: series endpoint
				val seriesResults = mutableListOf<kotlinx.coroutines.Deferred<List<StreamData>>>()

				if (torrentioEnabled) {
					Timber.d("[StreamScraperHelper] Querying Torrentio (series endpoint)...")
					seriesResults.add(
						async(Dispatchers.IO) {
							try {
								val result = torrentioApi.getStreams(safeImdbId, isMovie, seasonNumber, episodeNumber, false)
								Timber.d("[StreamScraperHelper] Torrentio (series) returned ${result.size} streams")
								result
							} catch (e: Exception) {
								Timber.e(e, "Error querying Torrentio series endpoint")
								emptyList()
							}
						}
					)
				}

				if (aiostreamsEnabled) {
					Timber.d("[StreamScraperHelper] Querying AIOStreams (series endpoint)...")
					seriesResults.add(
						async(Dispatchers.IO) {
							try {
								val result = aioStreamsApi.getStreams(safeImdbId, isMovie, seasonNumber, episodeNumber, false)
								Timber.d("[StreamScraperHelper] AIOStreams (series) returned ${result.size} streams")
								result
							} catch (e: Exception) {
								Timber.e(e, "Error querying AIOStreams series endpoint")
								emptyList()
							}
						}
					)
				}

				// Wait for series results
				val seriesStreams = seriesResults.awaitAll().flatten()
				allStreams.addAll(seriesStreams)
				Timber.d("[StreamScraperHelper] Got ${seriesStreams.size} streams from series endpoints, total: ${allStreams.size}")

			} else {
				// For non-anime: query single endpoint as before
				val deferredResults = mutableListOf<kotlinx.coroutines.Deferred<List<StreamData>>>()

				if (torrentioEnabled) {
					Timber.d("[StreamScraperHelper] Querying Torrentio...")
					deferredResults.add(
						async(Dispatchers.IO) {
							try {
								val result = torrentioApi.getStreams(safeImdbId, isMovie, seasonNumber, episodeNumber, false)
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
								val result = aioStreamsApi.getStreams(safeImdbId, isMovie, seasonNumber, episodeNumber, false)
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
				allStreams.addAll(results.flatten())
			}

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

			// Remove duplicates if enabled
			val removeDuplicates = userPreferences[UserPreferences.streamRemoveDuplicates]
			val finalStreams = if (removeDuplicates) {
				removeDuplicateStreams(filteredStreams)
			} else {
				filteredStreams
			}

			Timber.d("Found ${allStreams.size} streams total, ${filteredStreams.size} after filtering, ${finalStreams.size} after deduplication, sorted by $sortBy")
			onComplete(finalStreams)
		}
	}
}

