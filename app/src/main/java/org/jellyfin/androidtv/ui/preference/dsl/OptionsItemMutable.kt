package org.jellyfin.androidtv.ui.preference.dsl

import org.jellyfin.preference.Preference
import org.jellyfin.preference.store.PreferenceStore
import timber.log.Timber

abstract class OptionsItemMutable<T : Any> : OptionsItem {
	var title: String? = null
	var enabled: Boolean = true
	var visible: Boolean = true

	protected var dependencyCheckFun: () -> Boolean = { true }
	protected lateinit var binder: OptionsBinder<T>
	protected var boundPreference: Preference<T>? = null

	open fun bind(store: PreferenceStore<*, *>, preference: Preference<T>) {
		boundPreference = preference
		bind {
			Timber.d("[OptionsItemMutable] Setting up binder for preference: ${preference.key}")
			get {
				Timber.d("[OptionsItemMutable] Reading from store for preference: ${preference.key}")
				store[preference]
			}
			set {
				Timber.d("[OptionsItemMutable] Writing to store for preference: ${preference.key}, value: '$it'")
				store[preference] = it
			}
			default { store.getDefaultValue(preference) }
		}
	}

	open fun bind(init: OptionsBinder.Builder<T>.() -> Unit) {
		this.binder = OptionsBinder.Builder<T>()
			.apply { init() }
			.build()
	}

	fun depends(dependencyCheckFun: () -> Boolean) {
		this.dependencyCheckFun = dependencyCheckFun
	}
}
