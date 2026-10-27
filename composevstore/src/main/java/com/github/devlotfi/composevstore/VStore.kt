package com.github.devlotfi.composevstore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlin.reflect.KProperty

class RememberValue<T>(
    private val init: () -> T,
) : EntryKey {
    private var value = init()

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}

@Composable
fun <T> rememberValue(
    init: () -> T,
): RememberValue<T> = remember {
    RememberValue(init)
}

interface EntryKey {
    fun getKey(key: String, property: KProperty<*>) = "$key/${property.name}"
}

class RememberStateInDataStore<T>(
    private val key: String,
    private val dataStore: DataStore,
    private val init: () -> T,
) : EntryKey {
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): MutableState<T> =
        dataStore.state(getKey(key, property), init)
}

class RememberValueInDataStore<T>(
    private val key: String,
    private val dataStore: DataStore,
    private val init: () -> T,
) : EntryKey {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
        dataStore.getValue(getKey(key, property), init)

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) =
        dataStore.setValue(getKey(key, property), value)
}

open class DataStore {
    private val states = mutableMapOf<String, MutableState<Any?>>()
    private val values = mutableMapOf<String, Any?>()

    @Suppress("UNCHECKED_CAST")
    fun <T> state(key: String, init: () -> T): MutableState<T> =
        states.getOrPut(key) { mutableStateOf(init()) } as MutableState<T>

    @Suppress("UNCHECKED_CAST")
    fun <T> getValue(key: String, init: () -> T): T =
        values.getOrPut(key) { init() } as T

    @Suppress("UNCHECKED_CAST")
    fun <T> setValue(key: String, value: T) =
        values.set(key, value)

    fun clearStates(prefix: String) {
        states.keys.removeAll { it.startsWith(prefix) }
    }

    fun clearValues(prefix: String) {
        values.keys.removeAll { it.startsWith(prefix) }
    }

    @Composable
    fun <T> rememberStateInDataStore(
        key: String,
        init: () -> T,
    ): RememberStateInDataStore<T> = remember(key, this) {
        RememberStateInDataStore(key, this, init)
    }

    @Composable
    fun <T> rememberValueInDataStore(
        key: String,
        init: () -> T,
    ): RememberValueInDataStore<T> = remember(key, this) {
        RememberValueInDataStore(key, this, init)
    }
}
