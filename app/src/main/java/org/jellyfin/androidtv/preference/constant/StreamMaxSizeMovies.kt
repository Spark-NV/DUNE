package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

enum class StreamMaxSizeMovies(
    override val nameRes: Int,
    val sizeInBytes: Long
) : PreferenceEnum {
    SIZE_2_GB(R.string.pref_stream_max_size_movies_2gb, 2L * 1024 * 1024 * 1024),
    SIZE_3_GB(R.string.pref_stream_max_size_movies_3gb, 3L * 1024 * 1024 * 1024),
    SIZE_4_GB(R.string.pref_stream_max_size_movies_4gb, 4L * 1024 * 1024 * 1024),
    SIZE_5_GB(R.string.pref_stream_max_size_movies_5gb, 5L * 1024 * 1024 * 1024),
    SIZE_7_GB(R.string.pref_stream_max_size_movies_7gb, 7L * 1024 * 1024 * 1024),
    SIZE_8_GB(R.string.pref_stream_max_size_movies_8gb, 8L * 1024 * 1024 * 1024),
    SIZE_10_GB(R.string.pref_stream_max_size_movies_10gb, 10L * 1024 * 1024 * 1024),
    SIZE_12_GB(R.string.pref_stream_max_size_movies_12gb, 12L * 1024 * 1024 * 1024),
    SIZE_15_GB(R.string.pref_stream_max_size_movies_15gb, 15L * 1024 * 1024 * 1024),
    SIZE_17_GB(R.string.pref_stream_max_size_movies_17gb, 17L * 1024 * 1024 * 1024),
    SIZE_20_GB(R.string.pref_stream_max_size_movies_20gb, 20L * 1024 * 1024 * 1024),
    SIZE_25_GB(R.string.pref_stream_max_size_movies_25gb, 25L * 1024 * 1024 * 1024),
    SIZE_30_GB(R.string.pref_stream_max_size_movies_30gb, 30L * 1024 * 1024 * 1024),
    SIZE_50_GB(R.string.pref_stream_max_size_movies_50gb, 50L * 1024 * 1024 * 1024),
    SIZE_75_GB(R.string.pref_stream_max_size_movies_75gb, 75L * 1024 * 1024 * 1024),
    DISABLED(R.string.pref_stream_filter_disabled, Long.MAX_VALUE)
}
