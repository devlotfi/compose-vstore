# 6. API reference

Package: `com.devlotfi.testlol.vstore`

---

## `interface EntryKey`

Shared key-building behavior.

```kotlin
interface EntryKey {
    fun getKey(key: String, property: KProperty<*>) = "$key/${property.name}"
}
```

| Member | Description |
|---|---|
| `getKey(key, property)` | Returns `"<key>/<property.name>"`. Default implementation, usable by any class that implements `EntryKey`. |

Implemented by `RememberValue`, `RememberStateInDataStore`, and `RememberValueInDataStore`.

---

## `rememberValue`

```kotlin
@Composable
fun <T> rememberValue(init: () -> T): RememberValue<T>
```

Creates a local, non-observable mutable holder tied to the composition via `remember`. Use with `by`.

## `class RememberValue<T>`

```kotlin
class RememberValue<T>(private val init: () -> T) : EntryKey {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
}
```

| Member | Description |
|---|---|
| constructor `init` | Evaluated once at construction to seed the value. |
| `getValue` | Returns the current value. |
| `setValue` | Replaces the value. Does not notify Compose. |

---

## `open class DataStore`

Container for persisted-in-memory entries. Typically held by a `ViewModel`.

### Functions

#### `state`

```kotlin
fun <T> state(key: String, init: () -> T): MutableState<T>
```

Returns the `MutableState` stored under `key`, creating it with `mutableStateOf(init())` if missing. The key is used **as is** (no property name is appended).

#### `getValue`

```kotlin
fun <T> getValue(key: String, init: () -> T): T
```

Returns the plain value stored under `key`, creating it with `init()` if missing. Does not create observable state.

#### `setValue`

```kotlin
fun <T> setValue(key: String, value: T)
```

Stores `value` under `key` in the values map, replacing any existing value.

#### `clearStates`

```kotlin
fun clearStates(prefix: String)
```

Removes every **state** entry whose key starts with `prefix`.

#### `clearValues`

```kotlin
fun clearValues(prefix: String)
```

Removes every **value** entry whose key starts with `prefix`.

> `clearStates` and `clearValues` operate on separate maps. To fully clear a screen, call both.

### Composable factories

#### `rememberStateInDataStore`

```kotlin
@Composable
fun <T> rememberStateInDataStore(key: String, init: () -> T): RememberStateInDataStore<T>
```

Returns a remembered (`remember(key, this)`) delegate provider for observable state.

#### `rememberValueInDataStore`

```kotlin
@Composable
fun <T> rememberValueInDataStore(key: String, init: () -> T): RememberValueInDataStore<T>
```

Returns a remembered (`remember(key, this)`) delegate for non-observable values.

---

## `class RememberStateInDataStore<T>`

```kotlin
class RememberStateInDataStore<T>(
    private val key: String,
    private val dataStore: DataStore,
    private val init: () -> T,
) : EntryKey {
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): MutableState<T>
}
```

`provideDelegate` is invoked by Kotlin at the `by` site and returns `dataStore.state(getKey(key, property), init)`. The property's delegate **is** the `MutableState<T>`, so you need `androidx.compose.runtime.getValue` / `setValue` imports.

## `class RememberValueInDataStore<T>`

```kotlin
class RememberValueInDataStore<T>(
    private val key: String,
    private val dataStore: DataStore,
    private val init: () -> T,
) : EntryKey {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
}
```

Reads via `dataStore.getValue(getKey(key, property), init)` and writes via `dataStore.setValue(getKey(key, property), value)`.

---

## Key format cheat sheet

| Declaration | Final key |
|---|---|
| `var a by store.rememberStateInDataStore("Home") { 0 }` | `Home/a` |
| `var a by store.rememberValueInDataStore("Home") { 0 }` | `Home/a` (values map) |
| `var a by store.rememberStateInDataStore("Row/$id") { 0 }` | `Row/42/a` |
| `store.state("Home/a") { 0 }` | `Home/a` |
| `store.getValue("Home/a") { 0 }` | `Home/a` (values map) |
