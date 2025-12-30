package org.jellyfin.androidtv.preference

import org.jellyfin.androidtv.constant.GridDirection
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.constant.PosterSize
import org.jellyfin.androidtv.preference.store.DisplayPreferencesStore
import org.jellyfin.preference.booleanPreference
import org.jellyfin.preference.enumPreference
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

class LibraryPreferences(
	displayPreferencesId: String,
	api: ApiClient,
) : DisplayPreferencesStore(
	displayPreferencesId = displayPreferencesId,
	api = api,
) {
	companion object {
		val posterSize = enumPreference("PosterSize", PosterSize.X_SMALLEST)
		val imageType = enumPreference("ImageType", ImageType.THUMB)
		val gridDirection = enumPreference("GridDirection", GridDirection.LIST)
		val enableSmartScreen = booleanPreference("SmartScreen", false)
		val showItemTitlesOnFocus = booleanPreference("ShowItemTitlesOnFocus", true)

		val filterFavoritesOnly = booleanPreference("FilterFavoritesOnly", false)
		val filterUnwatchedOnly = booleanPreference("FilterUnwatchedOnly", false)

		val sortBy = enumPreference("SortBy", ItemSortBy.SORT_NAME)
		val sortOrder = enumPreference("SortOrder", SortOrder.ASCENDING)
	}
	fun getShowItemTitlesOnFocus(): Boolean = getBool(showItemTitlesOnFocus.key, showItemTitlesOnFocus.defaultValue)
}
