package org.jellyfin.androidtv.ui.preference.screen

import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.androidtv.ui.preference.dsl.text
import org.jellyfin.androidtv.ui.preference.dsl.info
import org.koin.android.ext.android.inject

class AioStreamsConfigPreferencesScreen : OptionsFragment() {
	private val userPreferences: UserPreferences by inject()

	override val stores: Array<org.jellyfin.preference.store.PreferenceStore<*, *>>
		get() = arrayOf(userPreferences)

	override val rebuildOnResume = true

	override val screen by optionsScreen {
		setTitle(R.string.pref_aiostreams_config)

		category {
			text {
				setTitle(R.string.pref_aiostreams_config)
				setContent(R.string.pref_aiostreams_config_description)
				bind(userPreferences, UserPreferences.aiostreamsConfig)
			}

			info {
				setTitle(R.string.pref_saved_value)
				setContent {
					val savedValue = userPreferences[UserPreferences.aiostreamsConfig]
					if (savedValue.isNotEmpty()) {
						"Saved: $savedValue"
					} else {
						"Saved: Not set"
					}
				}
			}
		}
	}
}

