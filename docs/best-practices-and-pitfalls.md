# 7. Best practices and pitfalls

## Naming keys

**Use the composable's name as the key, and define it once.**

```kotlin
private const val KEY = "LoginScreen"

@Composable
fun LoginScreen(vm: MainViewModel = viewModel()) {
    var email by vm.store.rememberStateInDataStore(KEY) { "" }
    var pass  by vm.store.rememberStateInDataStore(KEY) { "" }
}
```

Keep a stable hierarchy with `/` so bulk clearing works:

```
Checkout/address
Checkout/Payment/cardNumber
Profile/avatarUri
```

## Key collisions

The final key is `"<key>/<propertyName>"`. These situations produce the **same entry**:

### 1. Same composable rendered more than once

```kotlin
@Composable
fun Counter(vm: MainViewModel) {
    var n by vm.store.rememberStateInDataStore("Counter") { 0 }
}

Column {
    Counter(vm)   // Counter/n
    Counter(vm)   // Counter/n  ← shares state with the first one
}
```

Fix: include an identifier.

```kotlin
@Composable
fun Counter(id: String, vm: MainViewModel) {
    var n by vm.store.rememberStateInDataStore("Counter/$id") { 0 }
}
```

This applies to list items (`LazyColumn { items(...) }`), pager pages, and tabs that reuse a composable.

### 2. Two composables using the same key string and property name

```kotlin
// FileA.kt
var query by store.rememberStateInDataStore("Search") { "" }
// FileB.kt, different screen, accidentally same key
var query by store.rememberStateInDataStore("Search") { "" }
```

They now share state. Sometimes that is exactly what you want (a shared filter), so do it deliberately.

### 3. Same property name twice within one composable and key

Kotlin won't let you declare two locals with the same name in one scope, but you can in different branches or helper lambdas. Watch for it.

### 4. State and value maps are separate

`rememberStateInDataStore("X")` + `var a` and `rememberValueInDataStore("X")` + `var a` do **not** collide. They live in different maps.

## Prefix matching in `clear*`

`clearStates(prefix)` uses `startsWith`. The prefix `"Cart"` also matches `"CartSummary/..."`. End prefixes with `/`:

```kotlin
store.clearStates("Cart/")   // good: only Cart/…
store.clearStates("Cart")    // also removes CartSummary/…
```

## Cleanup

Nothing is removed automatically. Entries stay until the store dies or you clear them.

- **Screen done for good?** Clear its entries when the user finishes (submit, cancel, logout).
- **Per-item keys** (`"Row/$id"`) can accumulate for large or changing lists. Clear them when the data set is replaced.
- **Always clear both maps** if the screen used both state and values:

```kotlin
fun clearScreen(prefix: String) {
    store.clearStates(prefix)
    store.clearValues(prefix)
}
```

## Type consistency

The store casts with `as MutableState<T>` / `as T` unchecked. If the same key is used with a different type (for example `Int` first and `String` later), you get a `ClassCastException` at runtime, possibly far from the cause. Avoid sharing a key across declarations with different types, and avoid re-using a key after changing a property's type without clearing the entry (e.g. after a refactor during development, with a retained process).

## Threading

The maps are plain `mutableMapOf` with no synchronization.

- Read and write from the main thread / composition.
- If you update entries from coroutines on other dispatchers, switch to `Dispatchers.Main` first, or add your own synchronization in a `DataStore` subclass.

## Memory and leaks

The store lives with the ViewModel, which outlives Activities and composables.

- Do **not** store `Context`, `Activity`, `View`, `Bitmap` views, `FocusRequester`s bound to a composition, or lambdas capturing them.
- Large objects stay in memory until cleared.

## Process death

`vstore` is in-memory only. If Android kills your process, everything is lost. For data that must survive that (a draft the user cannot afford to lose), combine with `SavedStateHandle`, `rememberSaveable`, or real persistence.

## Which ViewModel scope?

The store lasts as long as the ViewModel you hold it in.

| ViewModel scope | Entries survive |
|---|---|
| Activity-scoped | Rotation, navigation within the Activity |
| Navigation back-stack entry-scoped | While the destination is on the back stack |
| Application-scoped holder | Until process death (use with care) |

Pick the narrowest scope that gives you the behavior you need.

## Mutable objects inside state

Compose only notices when the state **reference** changes. Mutating an object in place does not recompose.

```kotlin
// ✗ no recomposition
var list by store.rememberStateInDataStore("Todos") { mutableListOf<String>() }
list.add("x")

// ✓ immutable + reassign
var list by store.rememberStateInDataStore("Todos") { emptyList<String>() }
list = list + "x"
```

## `rememberValue` specifics

- Despite being "local only", it **does** survive recomposition because it uses `remember`. It is re-created when the composable leaves composition or the Activity is recreated.
- Writes never recompose. If the UI must reflect the value, use `mutableStateOf`.
- Don't write to it directly in the composition body; use `SideEffect`, `LaunchedEffect`, or callbacks.

## Checklist

- [ ] Key includes the composable name
- [ ] Key includes an id when the composable can appear multiple times
- [ ] Imported `androidx.compose.runtime.getValue` and `setValue` when using `rememberStateInDataStore`
- [ ] Prefixes passed to `clear*` end with `/`
- [ ] Both maps cleared when needed
- [ ] No Android context or view objects in the store
- [ ] Store updated from the main thread
