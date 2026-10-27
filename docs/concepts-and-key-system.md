# 2. Concepts and the key system

## The mental model

Compose's built-in `remember` ties a value to the **position in the composition**. If the composable leaves the composition, or the Activity is recreated, the value is gone.

`vstore` moves ownership of the value out of the composition and into a `DataStore` object. The composable only holds a **key** that points to its entry:

```
 ViewModel (long-lived)
 └── DataStore
      ├── states:  Map<String, MutableState<Any?>>
      └── values:  Map<String, Any?>
                     ▲
                     │ lookup by key
 Composable (short-lived) ── "CounterScreen/count"
```

Since the ViewModel outlives the composition, a new composition (after rotation, for example) looks up the same key and finds the old data.

## Two kinds of entries

A `DataStore` keeps two independent maps.

| | States map | Values map |
|---|---|---|
| Created by | `rememberStateInDataStore` / `DataStore.state` | `rememberValueInDataStore` / `DataStore.getValue` |
| Stored as | `MutableState<Any?>` | the raw object |
| Compose observes it | Yes, reads in composition subscribe | No |
| Writes trigger recomposition | Yes | No |
| Cleared with | `clearStates(prefix)` | `clearValues(prefix)` |

The maps are separate namespaces. A state entry and a value entry may share the same key string without interfering with each other.

## How keys are built

Every entry key is:

```
"<key you pass>/<property name>"
```

This is implemented by the `EntryKey` interface:

```kotlin
interface EntryKey {
    fun getKey(key: String, property: KProperty<*>) = "$key/${property.name}"
}
```

- `key` is the string **you** pass to `rememberStateInDataStore(key)` or `rememberValueInDataStore(key)`. By convention this is the composable's name.
- `property.name` is **added automatically** by Kotlin's delegate mechanism: it is the name of the `val`/`var` you declared.

```kotlin
@Composable
fun LoginScreen(vm: MainViewModel = viewModel()) {
    var email    by vm.store.rememberStateInDataStore("LoginScreen") { "" }   // "LoginScreen/email"
    var password by vm.store.rememberStateInDataStore("LoginScreen") { "" }   // "LoginScreen/password"
    var scrollY  by vm.store.rememberValueInDataStore("LoginScreen") { 0 }    // "LoginScreen/scrollY" (values map)
}
```

Because the property name is part of the key, you can reuse the same `"LoginScreen"` prefix for every property in a composable without collisions.

### Uniqueness rule

An entry is uniquely identified by:

> **(kind: state or value) + (key string) + (property name)**

Two declarations that produce the same final key share the same entry. This is a feature when intentional (two composables sharing state) and a bug when accidental. See [Best practices](best-practices-and-pitfalls.md#key-collisions).

### Why prefixes matter

`clearStates(prefix)` and `clearValues(prefix)` remove every entry whose key **starts with** the prefix. Choosing keys with a stable hierarchy makes bulk cleanup easy:

```
"Checkout/address"
"Checkout/promoCode"
"Checkout/Payment/cardNumber"      ← nested key: "Checkout/Payment" + "cardNumber"
```

`store.clearStates("Checkout/")` removes all three.

## Lifetimes

| Thing | Lives as long as |
|---|---|
| `rememberValue { }` value | The composable's place in the composition (`remember`) |
| `RememberStateInDataStore` / `RememberValueInDataStore` delegate objects | The composable's place in the composition, and while `key` and the store instance stay the same |
| Entries inside `DataStore` | The `DataStore` instance (normally the ViewModel), **or** until you call `clearStates` / `clearValues` |

Entries are **never** removed automatically when a composable leaves the composition. That is what lets them survive navigation and configuration changes. It also means you own cleanup (see [Recipes](recipes.md#clearing-state-on-logout)).

## Initializers run once per key

The `init` lambda is only executed when no entry exists for that key. After that, the stored data is returned and the lambda is ignored:

```kotlin
var items by store.rememberStateInDataStore("Feed") { expensiveInitialList() }
// expensiveInitialList() runs on first composition only, not on rotation
```

## What vstore is not

- It does **not** persist to disk. Data is lost on process death (and when the ViewModel is cleared).
- It does **not** integrate with `SavedStateHandle` / `rememberSaveable`.
- It is **not** thread-safe (plain `mutableMapOf`). Use it from the main thread / composition.
- It does **not** garbage-collect entries.

## Next

- [`rememberValue`](remember-value.md)
- [State in DataStore](state-in-datastore.md)
