package org.jellyfin.androidtv.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

enum class PosterSize(
	override val nameRes: Int,
) : PreferenceEnum {
	/**
	 * Extra Extra Extra Smallest.
	 */
	XXX_SMALLEST(R.string.image_size_xxxsmallest),
	/**
	 * Extra Extra Smallest.
	 */
	XX_SMALLEST(R.string.image_size_xxsmallest),
	/**
	 * Extra Smallest.
	 */
	X_SMALLEST(R.string.image_size_xsmallest),
	/**
	 * Smallest.
	 */
	SMALLEST(R.string.image_size_smallest),
	/**
	 * Small.
	 */
	SMALL(R.string.image_size_small),

	/**
	 * Medium.
	 */
	MED(R.string.image_size_medium),

	/**
	 * Large.
	 */
	LARGE(R.string.image_size_large),

	/**
	 * Extra Large.
	 */
	X_LARGE(R.string.image_size_xlarge),
}
