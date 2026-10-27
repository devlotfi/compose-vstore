# 4. State in DataStore: `rememberStateInDataStore`

Observable Compose state that lives in a `DataStore`, so it survives recomposition **and** configuration changes (for as long as the store lives).

```kotlin
@Composable
fun <T> DataStore.rememberStateInDataStore(
    key: String,
    init: () -> T,
): RememberStateInDataStore<T>
```

## Basic usage

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun NameScreen(vm: MainViewModel = viewModel()) {
    var name by vm.store.rememberStateInDataStore("NameScreen") { "" }

    TextField(value = name, onValueChange = { name = it })
    Text("Hello, $name")
}
```

- Entry key: `"NameScreen/name"`.
- Reading `name` in composition subscribes the composable. Writing it triggers recomposition.
- The initializer `{ "" }` only runs the first time that key is requested.

## How it works

1. `rememberStateInDataStore` returns a `RememberStateInDataStore<T>`, remembered with `remember(key, this)`.
2. `RememberStateInDataStore` exposes `operator fun provideDelegate(...)`, which Kotlin calls at the point of the `by` declaration.
3. `provideDelegate` computes `"$key/${property.name}"` and returns `dataStore.state(finalKey, init)`.
4. `DataStore.state` does `states.getOrPut(key) { mutableStateOf(init()) }`.
5. The result is a real Compose `MutableState<T>`, which is why `getValue`/`setValue` must be imported from `androidx.compose.runtime`.

Because `provideDelegate` returns the `MutableState` directly, there is no extra wrapper at runtime. You get standard Compose snapshot-state behavior.

## Using the state without `by`

You can call `DataStore.state` directly to get the `MutableState<T>` without delegation. Here you build the full key yourself:

```kotlin
val countState: MutableState<Int> = vm.store.state("CounterScreen/count") { 0 }
countState.value++
```

This is handy in non-composable code, such as a ViewModel method that pre-fills a screen's state:

```kotlin
class MainViewModel : ViewModel() {
    val store = DataStore()

    fun prefillEmail(email: String) {
        store.state("LoginScreen/email") { "" }.value = email
    }
}
```

> When using the direct API, you must match the key format `"<key>/<propertyName>"` exactly so the composable's delegate finds the same entry.

## Multiple properties in one composable

Reuse the same key string. The property name keeps them apart:

```kotlin
@Composable
fun SignUpForm(vm: MainViewModel = viewModel()) {
    var email    by vm.store.rememberStateInDataStore("SignUpForm") { "" }
    var password by vm.store.rememberStateInDataStore("SignUpForm") { "" }
    var accepted by vm.store.rememberStateInDataStore("SignUpForm") { false }
    // keys: SignUpForm/email, SignUpForm/password, SignUpForm/accepted
}
```

## Complex types

Any type works, but follow normal Compose rules: prefer immutable data and replace the value on change.

```kotlin
data class Filters(val query: String = "", val onlyFavorites: Boolean = false)

@Composable
fun ProductList(vm: MainViewModel = viewModel()) {
    var filters by vm.store.rememberStateInDataStore("ProductList") { Filters() }

    Switch(
        checked = filters.onlyFavorites,
        onCheckedChange = { filters = filters.copy(onlyFavorites = it) }
    )
}
```

Mutating a plain `MutableList` stored in state will **not** notify Compose. Use `mutableStateListOf`, or store an immutable `List` and assign a new one:

```kotlin
var todos by vm.store.rememberStateInDataStore("Todos") { emptyList<String>() }
todos = todos + "New item"
```

## Per-item state in lists

Different list items running the same composable would share a key. Include the item id in the key:

```kotlin
@Composable
fun ExpandableRow(id: Long, vm: MainViewModel = viewModel()) {
    var expanded by vm.store.rememberStateInDataStore("ExpandableRow/$id") { false }
    // key: ExpandableRow/42/expanded
}
```

## Reading from outside composition

Since the entry is a `MutableState`, anything with access to the store can read it:

```kotlin
val email = store.state("LoginScreen/email") { "" }.value
```

Reads outside composition do not subscribe anything, and are subject to normal snapshot rules.

## Gotchas

- **Type must stay consistent per key.** The store casts unchecked (`as MutableState<T>`). Declaring the same key with a different type will fail at use time (`ClassCastException`).
- **The initializer is ignored after first creation.** Changing the lambda later does not reset the state.
- **Not saved on process death.** See [Best practices](best-practices-and-pitfalls.md).
- **Don't store lifecycle-bound objects** (`Context`, `Activity`, `View`, `FocusRequester` tied to a view) in the store; the ViewModel outlives them and they will leak.

Next: [Values in DataStore](value-in-datastore.md)
