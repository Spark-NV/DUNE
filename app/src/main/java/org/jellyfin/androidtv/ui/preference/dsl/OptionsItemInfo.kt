package org.jellyfin.androidtv.ui.preference.dsl

import android.content.Context
import androidx.annotation.StringRes
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import timber.log.Timber
import java.util.UUID

class OptionsItemInfo(
	private val context: Context
) : OptionsItemMutable<Unit>() {
	var content: String? = null
	private var contentProvider: (() -> String)? = null

	fun setTitle(@StringRes resId: Int) {
		title = context.getString(resId)
	}

	fun setContent(@StringRes resId: Int) {
		content = context.getString(resId)
	}

	fun setContent(provider: () -> String) {
		contentProvider = provider
	}

	override fun build(category: PreferenceCategory, container: OptionsUpdateFunContainer) {
		val pref = EditTextPreference(context).also {
			it.isPersistent = false
			it.key = UUID.randomUUID().toString()
			category.addPreference(it)
			it.isEnabled = false
			it.isSelectable = false
			it.isVisible = dependencyCheckFun() && visible
			it.title = title
			val initialContent = contentProvider?.invoke() ?: content ?: ""
			it.summary = initialContent
			Timber.d("[OptionsItemInfo] Initial content: $initialContent")
		}

		if (contentProvider != null) {
			container += {
				val updatedContent = contentProvider?.invoke() ?: content ?: ""
				Timber.d("[OptionsItemInfo] Updating content: $updatedContent")
				pref.summary = updatedContent
				pref.isVisible = dependencyCheckFun() && visible
			}
		}
	}
}

@OptionsDSL
fun OptionsCategory.info(init: OptionsItemInfo.() -> Unit) {
	this += OptionsItemInfo(context).apply { init() }
}
