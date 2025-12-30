package org.jellyfin.androidtv.ui.preference.dsl

data class OptionsBinder<T>(
	val get: () -> T,
	val set: (value: T) -> Unit,
	val default: () -> T
) {
	init {
		timber.log.Timber.d("[OptionsBinder] Created binder, get function: ${get::class.java.simpleName}")
	}

	class Builder<T> {
		private var getFun: (() -> T)? = null
		private var setFun: ((value: T) -> Unit)? = null
		private var defaultFun: (() -> T)? = null

		fun get(getFun: () -> T) {
			this.getFun = getFun
		}

		fun default(defaultFun: () -> T) {
			this.defaultFun = defaultFun
		}

		fun set(setFun: (value: T) -> Unit) {
			this.setFun = setFun
		}

		fun build(): OptionsBinder<T> = OptionsBinder(
			get = {
				timber.log.Timber.d("[OptionsBinder] get() called")
				val result = getFun!!()
				timber.log.Timber.d("[OptionsBinder] get() returned: '$result'")
				result
			},
			set = { value ->
				timber.log.Timber.d("[OptionsBinder] set('$value') called")
				setFun!!(value)
				timber.log.Timber.d("[OptionsBinder] set('$value') completed")
			},
			default = {
				timber.log.Timber.d("[OptionsBinder] default() called")
				val result = defaultFun!!()
				timber.log.Timber.d("[OptionsBinder] default() returned: '$result'")
				result
			}
		)
	}
}
