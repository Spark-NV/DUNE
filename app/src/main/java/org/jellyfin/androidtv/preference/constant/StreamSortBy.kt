package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

enum class StreamSortBy(
    override val nameRes: Int,
) : PreferenceEnum {
    /**
     * Sort streams by size in descending order (largest first)
     */
    SIZE_DESCENDING(R.string.pref_stream_sort_size_descending),

    /**
     * Sort streams by size in ascending order (smallest first)
     */
    SIZE_ASCENDING(R.string.pref_stream_sort_size_ascending),
}