package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

enum class StreamMinSizeMovies(
    override val nameRes: Int,
    val sizeInBytes: Long
) : PreferenceEnum {
    SIZE_300_MB(R.string.pref_stream_min_size_movies_300mb, 300L * 1024 * 1024),
    SIZE_500_MB(R.string.pref_stream_min_size_movies_500mb, 500L * 1024 * 1024),
    SIZE_1_GB(R.string.pref_stream_min_size_movies_1gb, 1L * 1024 * 1024 * 1024),
    SIZE_2_GB(R.string.pref_stream_min_size_movies_2gb, 2L * 1024 * 1024 * 1024),
    SIZE_3_GB(R.string.pref_stream_min_size_movies_3gb, 3L * 1024 * 1024 * 1024),
    SIZE_4_GB(R.string.pref_stream_min_size_movies_4gb, 4L * 1024 * 1024 * 1024),
    SIZE_5_GB(R.string.pref_stream_min_size_movies_5gb, 5L * 1024 * 1024 * 1024),
    SIZE_6_GB(R.string.pref_stream_min_size_movies_6gb, 6L * 1024 * 1024 * 1024),
    SIZE_8_GB(R.string.pref_stream_min_size_movies_8gb, 8L * 1024 * 1024 * 1024),
    SIZE_10_GB(R.string.pref_stream_min_size_movies_10gb, 10L * 1024 * 1024 * 1024),
    SIZE_12_GB(R.string.pref_stream_min_size_movies_12gb, 12L * 1024 * 1024 * 1024),
    SIZE_15_GB(R.string.pref_stream_min_size_movies_15gb, 15L * 1024 * 1024 * 1024),
    SIZE_20_GB(R.string.pref_stream_min_size_movies_20gb, 20L * 1024 * 1024 * 1024),
    DISABLED(R.string.pref_stream_filter_disabled, 0L)
}
