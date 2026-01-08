package org.jellyfin.androidtv.ui.preference.screen

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.action
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.android.ext.android.inject
import timber.log.Timber

class AnimeLibrarySelectionScreen : OptionsFragment() {
    private val userPreferences: UserPreferences by inject()
    private val userViewsRepository by inject<UserViewsRepository>()
    private val userViews by lazy {
        userViewsRepository.views.stateIn(lifecycleScope, SharingStarted.Lazily, emptyList())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userViews.onEach {
            rebuild()
        }.launchIn(lifecycleScope)
    }

    override val screen by optionsScreen {
        setTitle(R.string.pref_select_anime_library)

        category {
            setTitle(R.string.pref_select_anime_library_description)

            userViews.value.forEach { library ->
                val currentSelection = userPreferences[UserPreferences.animeLibraryId]
                val isSelected = currentSelection == library.id.toString()

                action {
                    title = library.name ?: "Unknown Library"
                    content = "${getString(R.string.lbl_item_id, library.id)}${if (isSelected) " ✓ SELECTED" else ""}"

                    onActivate = {
                        val libraryIdStr = library.id.toString()
                        userPreferences[UserPreferences.animeLibraryId] = libraryIdStr
                        Timber.d("[AnimeLibrarySelectionScreen] Selected anime library: ${library.name} (ID: $libraryIdStr)")
                        // Show a toast or navigate back
                        requireActivity().onBackPressed()
                    }
                }
            }
        }
    }
}
