package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

enum class StreamMaxSizeEpisodes(
    override val nameRes: Int,
    val sizeInBytes: Long
) : PreferenceEnum {
    SIZE_1_GB(R.string.pref_stream_max_size_episodes_1gb, 1L * 1024 * 1024 * 1024),
    SIZE_2_GB(R.string.pref_stream_max_size_episodes_2gb, 2L * 1024 * 1024 * 1024),
    SIZE_3_GB(R.string.pref_stream_max_size_episodes_3gb, 3L * 1024 * 1024 * 1024),
    SIZE_4_GB(R.string.pref_stream_max_size_episodes_4gb, 4L * 1024 * 1024 * 1024),
    SIZE_5_GB(R.string.pref_stream_max_size_episodes_5gb, 5L * 1024 * 1024 * 1024),
    SIZE_7_GB(R.string.pref_stream_max_size_episodes_7gb, 7L * 1024 * 1024 * 1024),
    SIZE_8_GB(R.string.pref_stream_max_size_episodes_8gb, 8L * 1024 * 1024 * 1024),
    SIZE_10_GB(R.string.pref_stream_max_size_episodes_10gb, 10L * 1024 * 1024 * 1024),
    SIZE_12_GB(R.string.pref_stream_max_size_episodes_12gb, 12L * 1024 * 1024 * 1024),
    SIZE_15_GB(R.string.pref_stream_max_size_episodes_15gb, 15L * 1024 * 1024 * 1024),
    DISABLED(R.string.pref_stream_filter_disabled, Long.MAX_VALUE)
}
