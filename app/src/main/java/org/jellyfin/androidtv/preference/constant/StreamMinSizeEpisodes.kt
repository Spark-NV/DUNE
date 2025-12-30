package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

enum class StreamMinSizeEpisodes(
    override val nameRes: Int,
    val sizeInBytes: Long
) : PreferenceEnum {
    SIZE_50_MB(R.string.pref_stream_min_size_episodes_50mb, 50L * 1024 * 1024),
    SIZE_100_MB(R.string.pref_stream_min_size_episodes_100mb, 100L * 1024 * 1024),
    SIZE_200_MB(R.string.pref_stream_min_size_episodes_200mb, 200L * 1024 * 1024),
    SIZE_300_MB(R.string.pref_stream_min_size_episodes_300mb, 300L * 1024 * 1024),
    SIZE_500_MB(R.string.pref_stream_min_size_episodes_500mb, 500L * 1024 * 1024),
    SIZE_750_MB(R.string.pref_stream_min_size_episodes_750mb, 750L * 1024 * 1024),
    SIZE_1_GB(R.string.pref_stream_min_size_episodes_1gb, 1L * 1024 * 1024 * 1024),
    SIZE_2_GB(R.string.pref_stream_min_size_episodes_2gb, 2L * 1024 * 1024 * 1024),
    SIZE_3_GB(R.string.pref_stream_min_size_episodes_3gb, 3L * 1024 * 1024 * 1024),
    SIZE_4_GB(R.string.pref_stream_min_size_episodes_4gb, 4L * 1024 * 1024 * 1024),
    SIZE_5_GB(R.string.pref_stream_min_size_episodes_5gb, 5L * 1024 * 1024 * 1024),
    DISABLED(R.string.pref_stream_filter_disabled, 0L)
}
