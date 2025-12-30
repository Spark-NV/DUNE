package org.jellyfin.androidtv.ui.preference.dsl

import android.content.Context
import androidx.annotation.StringRes
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import timber.log.Timber
import java.util.UUID

class OptionsItemEditText(
	private val context: Context
) : OptionsItemMutable<String>() {
	var summary: String? = null

	fun setTitle(@StringRes resId: Int) {
		title = context.getString(resId)
	}

	fun setContent(@StringRes resId: Int) {
		summary = context.getString(resId)
	}

	override fun build(category: PreferenceCategory, container: OptionsUpdateFunContainer) {
		val pref = EditTextPreference(context).also {
			val preferenceKey = boundPreference?.key ?: UUID.randomUUID().toString()
			it.isPersistent = true
			it.key = preferenceKey
			category.addPreference(it)
			it.isEnabled = dependencyCheckFun() && enabled
			it.isVisible = visible
			it.title = title
			it.dialogTitle = title
			val currentValue = binder.get()
			Timber.d("[OptionsItemEditText] Initial value from binder: '$currentValue'")
			Timber.d("[OptionsItemEditText] Preference title: '$title', key: '${it.key}'")
			it.text = currentValue
			it.summaryProvider = androidx.preference.Preference.SummaryProvider<EditTextPreference> { preference ->
				val value = binder.get()
				if (value.isNotEmpty()) {
					"Current: $value"
				} else {
					summary ?: "Not set"
				}
			}

			it.setOnPreferenceChangeListener { preference, newValue ->
				Timber.d("[OptionsItemEditText] Preference changed to: '$newValue'")
				// Trigger container update to refresh info item
				container()
				true
			}
		}

		container += {
			pref.isEnabled = dependencyCheckFun() && enabled
		}
	}
}

@OptionsDSL
fun OptionsCategory.text(init: OptionsItemEditText.() -> Unit) {
	this += OptionsItemEditText(context).apply { init() }
}

