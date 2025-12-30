package org.jellyfin.androidtv.ui.preference.screen

import android.app.AlertDialog
import android.content.Intent
import android.text.format.Formatter
import coil3.ImageLoader
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.SystemPreferences
import org.jellyfin.androidtv.preference.TelemetryPreferences
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.action
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.list
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.androidtv.util.isTvDevice
import org.koin.android.ext.android.inject

class DeveloperPreferencesScreen : OptionsFragment() {
	private val userPreferences: UserPreferences by inject()
	private val systemPreferences: SystemPreferences by inject()
	private val telemetryPreferences: TelemetryPreferences by inject()
	private val imageLoader: ImageLoader by inject()

	private fun showRestartDialog() {
		AlertDialog.Builder(requireContext())
			.setTitle(R.string.restart_required)
			.setMessage(R.string.restart_required_message)
			.setPositiveButton(R.string.restart_now) { _, _ ->
				val packageManager = requireContext().packageManager
				val intent = packageManager.getLaunchIntentForPackage(requireContext().packageName)
				val mainIntent = Intent.makeRestartActivityTask(intent?.component)
				requireContext().startActivity(mainIntent)
				Runtime.getRuntime().exit(0)
			}
			.setNegativeButton(R.string.restart_later, null)
			.show()
	}

	override val screen by optionsScreen {
		setTitle(R.string.pref_developer_link)

		category {
			// Back button cooldown
			list {
				setTitle(R.string.pref_back_button_cooldown)
				entries = setOf(
					500, 750, 1000, 1250, 1500, 1750, 2000, 2250, 2500, 2750, 3000, 3250, 3500, 3750, 4000
				).associate {
					it.toString() to "${it / 1000.0}s"
				}
				bind {
					get { userPreferences[UserPreferences.backButtonCooldownMs].toString() }
					set {
						val newValue = it.toInt()
						if (userPreferences[UserPreferences.backButtonCooldownMs] != newValue) {
							userPreferences[UserPreferences.backButtonCooldownMs] = newValue
						}
					}
					default { userPreferences[UserPreferences.backButtonCooldownMs].toString() }
				}
			}

			// Legacy debug flag
			// Not in use by much components anymore
			checkbox {
				setTitle(R.string.lbl_enable_debug)
				setContent(R.string.desc_debug)
				bind(userPreferences, UserPreferences.debuggingEnabled)
			}

			// UI Mode toggle
			if (!context.isTvDevice()) {
				checkbox {
					setTitle(R.string.disable_ui_mode_warning)
					bind(systemPreferences, SystemPreferences.disableUiModeWarning)
				}
			}

			// Enable new playback module removed
			/*// Only show in debug mode
			// some strings are hardcoded because these options don't show in beta/release builds
			if (BuildConfig.DEVELOPMENT) {
				checkbox {
					title = "Enable new playback module for video"
					setContent(R.string.enable_playback_module_description)

					bind(userPreferences, UserPreferences.playbackRewriteVideoEnabled)
				}
			}*/

			// Enable trickplay removed
			/*checkbox {
				setTitle(R.string.preference_enable_trickplay)
				setContent(R.string.enable_playback_module_description)

				bind(userPreferences, UserPreferences.trickPlayEnabled)
			}*/

			// Prefer ffmpeg removed
			/*checkbox {
				setTitle(R.string.prefer_exoplayer_ffmpeg)
				setContent(R.string.prefer_exoplayer_ffmpeg_content)

				bind(userPreferences, UserPreferences.preferExoPlayerFfmpeg)
			}*/

			// Hardware acceleration removed
			/*checkbox {
				setTitle(R.string.hardware_acceleration_enabled)
				setContent(R.string.hardware_acceleration_enabled_content)
				bind(userPreferences, UserPreferences.hardwareAccelerationEnabled)
			}*/

			// Crash reporting options moved from crash reporting screen
			checkbox {
				setTitle(R.string.pref_crash_reports)
				setContent(R.string.pref_crash_reports_enabled, R.string.pref_crash_reports_disabled)
				bind(telemetryPreferences, TelemetryPreferences.crashReportEnabled)
			}

			checkbox {
				setTitle(R.string.pref_crash_report_logs)
				setContent(R.string.pref_crash_report_logs_enabled, R.string.pref_crash_report_logs_disabled)
				bind(telemetryPreferences, TelemetryPreferences.crashReportIncludeLogs)
				depends { telemetryPreferences[TelemetryPreferences.crashReportEnabled] }
			}

			action {
				setTitle(R.string.clear_image_cache)
				content = getString(R.string.clear_image_cache_content, Formatter.formatFileSize(context, imageLoader.diskCache?.size ?: 0))
				onActivate = {
					imageLoader.memoryCache?.clear()
					imageLoader.diskCache?.clear()
					rebuild()
				}
			}

			list {
				setTitle(R.string.pref_disk_cache_size)
			entries = setOf(
				0, 100, 250, 500, 800, 1024, 1536, 2048, 3072, 4096, 5120, 6144, 7168, 8192, 9216, 10240, 11264, 12288, 13312, 14336, 15360
			).associate {
				it.toString() to when (it) {
					0 -> getString(R.string.pref_disk_cache_size_disabled)
					100 -> getString(R.string.pref_disk_cache_size_100mb)
					250 -> getString(R.string.pref_disk_cache_size_250mb)
					500 -> getString(R.string.pref_disk_cache_size_500mb)
					800 -> getString(R.string.pref_disk_cache_size_800mb)
					1024 -> getString(R.string.pref_disk_cache_size_1gb)
					1536 -> getString(R.string.pref_disk_cache_size_1_5gb)
					2048 -> getString(R.string.pref_disk_cache_size_2gb)
					3072 -> getString(R.string.pref_disk_cache_size_3gb)
					4096 -> getString(R.string.pref_disk_cache_size_4gb)
					5120 -> getString(R.string.pref_disk_cache_size_5gb)
					6144 -> getString(R.string.pref_disk_cache_size_6gb)
					7168 -> getString(R.string.pref_disk_cache_size_7gb)
					8192 -> getString(R.string.pref_disk_cache_size_8gb)
					9216 -> getString(R.string.pref_disk_cache_size_9gb)
					10240 -> getString(R.string.pref_disk_cache_size_10gb)
					11264 -> getString(R.string.pref_disk_cache_size_11gb)
					12288 -> getString(R.string.pref_disk_cache_size_12gb)
					13312 -> getString(R.string.pref_disk_cache_size_13gb)
					14336 -> getString(R.string.pref_disk_cache_size_14gb)
					15360 -> getString(R.string.pref_disk_cache_size_15gb)
					else -> it.toString()
				}
			}
				bind {
					get { userPreferences[UserPreferences.diskCacheSizeMb].toString() }
					set {
						val newValue = it.toInt()
						if (userPreferences[UserPreferences.diskCacheSizeMb] != newValue) {
							userPreferences[UserPreferences.diskCacheSizeMb] = newValue
							showRestartDialog()
						}
					}
					default { userPreferences[UserPreferences.diskCacheSizeMb].toString() }
				}
			}
		}
	}
}
