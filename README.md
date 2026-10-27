<img src="https://raw.githubusercontent.com/devlotfi/compose-vstore/master/github-assets/github-banner.png">

# Compose VStore

[![JitPack](https://jitpack.io/v/devlotfi/compose-vstore.svg)](https://jitpack.io/#devlotfi/compose-vstore)

Compose VStore (`vstore`) is a lightweight Kotlin library for keeping Jetpack Compose state and values in a `DataStore` hosted by a `ViewModel`. Entries are keyed by a screen key and Kotlin property name, so they can be looked up again after recomposition or configuration changes while the same store remains alive.

## Features

- Keep observable Compose state in a `DataStore` with `rememberStateInDataStore`.
- Keep non-observable values in the store with `rememberValueInDataStore`.
- Use `rememberValue` for local values that only need to survive recomposition.
- Derive entry keys from the supplied key and delegated property name.
- Clear stored entries by key prefix when needed.

## Installation

Compose VStore is published on [JitPack](https://jitpack.io/#devlotfi/compose-vstore).

## Documentation

- [Docs](https://github.com/devlotfi/compose-vstore/blob/main/docs/intro.md)
- [llms.txt](https://devlotfi.github.io/compose-vstore/llms.txt)

## Quick start

Keep a `DataStore` in a `ViewModel`, then declare delegated state in a composable:

```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.devlotfi.composevstore.DataStore

class MainViewModel : ViewModel() {
    val store = DataStore()
}

@Composable
fun CounterScreen(vm: MainViewModel = viewModel()) {
    var count by vm.store.rememberStateInDataStore("CounterScreen") { 0 }

    Button(onClick = { count++ }) {
        Text("Clicked $count times")
    }
}
```

The entry key is derived as `CounterScreen/count`. State survives recomposition and configuration changes as long as the same `ViewModel` (and its store) remains alive; it is not persisted across process death.

## Core concepts

### Choosing the right tool

| API | Survives recomposition | Survives config change | Triggers recomposition | Lives in |
|---|---|---|---|---|
| `rememberValue { }` | Yes | No | No | Composition (`remember`) |
| `store.rememberStateInDataStore(key) { }` | Yes | Yes | **Yes** | `DataStore` (ViewModel) |
| `store.rememberValueInDataStore(key) { }` | Yes | Yes | No | `DataStore` (ViewModel) |

```
Must it outlive the composable (rotation, navigation, ...)?
├── No  → rememberValue { }
└── Yes → Should the UI update when it changes?
          ├── Yes → rememberStateInDataStore
          └── No  → rememberValueInDataStore
```

### How keys work

Every key is `"<key you pass>/<property name>"`. The property name is added automatically from the delegated variable, so one key string per composable is enough:

```kotlin
@Composable
fun LoginScreen(vm: MainViewModel = viewModel()) {
    var email    by vm.store.rememberStateInDataStore("LoginScreen") { "" }    // LoginScreen/email
    var password by vm.store.rememberStateInDataStore("LoginScreen") { "" }    // LoginScreen/password
    var scrollY  by vm.store.rememberValueInDataStore("LoginScreen") { 0 }     // LoginScreen/scrollY (values map)
}
```

The initializer lambda only runs the first time a key is requested; afterwards the stored entry is returned.

Observable state and plain values live in two separate maps, so a state entry and a value entry with the same key never collide.

## Examples

Unless noted, the examples assume the `MainViewModel` from the quick start and these imports:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
```

### 1. A form that survives rotation

```kotlin
private const val KEY = "ContactForm"

@Composable
fun ContactForm(vm: MainViewModel = viewModel()) {
    var name    by vm.store.rememberStateInDataStore(KEY) { "" }
    var email   by vm.store.rememberStateInDataStore(KEY) { "" }
    var message by vm.store.rememberStateInDataStore(KEY) { "" }
    var agreed  by vm.store.rememberStateInDataStore(KEY) { false }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") })
        OutlinedTextField(email, { email = it }, label = { Text("Email") })
        OutlinedTextField(message, { message = it }, label = { Text("Message") })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(agreed, { agreed = it })
            Text("I agree to the terms")
        }
        Button(enabled = agreed, onClick = { /* submit */ }) { Text("Send") }
    }
}
```

Keys: `ContactForm/name`, `ContactForm/email`, `ContactForm/message`, `ContactForm/agreed`.

### 2. Immutable data classes as state

Compose only reacts when the state *reference* changes, so store immutable objects and replace them with `copy`:

```kotlin
data class Filters(
    val query: String = "",
    val onlyFavorites: Boolean = false,
    val sort: Sort = Sort.NAME,
)

enum class Sort { NAME, PRICE, RATING }

@Composable
fun ProductFilters(vm: MainViewModel = viewModel()) {
    var filters by vm.store.rememberStateInDataStore("ProductFilters") { Filters() }

    Column {
        TextField(filters.query, { filters = filters.copy(query = it) })
        Switch(
            checked = filters.onlyFavorites,
            onCheckedChange = { filters = filters.copy(onlyFavorites = it) },
        )
        Sort.entries.forEach { s ->
            FilterChip(
                selected = filters.sort == s,
                onClick = { filters = filters.copy(sort = s) },
                label = { Text(s.name) },
            )
        }
    }
}
```

The same applies to lists. Don't mutate in place; assign a new list:

```kotlin
var todos by vm.store.rememberStateInDataStore("Todos") { emptyList<String>() }

Button(onClick = { todos = todos + "New item" }) { Text("Add") }
Button(onClick = { todos = todos.dropLast(1) }) { Text("Undo") }
```

### 3. Per-item state in a `LazyColumn`

A composable used many times shares its key, so add the item id to the key. Each row then keeps its own state through scrolling, rotation, and navigating away and back:

```kotlin
@Composable
fun FaqList(items: List<Faq>, vm: MainViewModel = viewModel()) {
    LazyColumn {
        items(items, key = { it.id }) { faq -> FaqRow(faq, vm) }
    }
}

@Composable
private fun FaqRow(faq: Faq, vm: MainViewModel) {
    // Key: FaqRow/<id>/expanded
    var expanded by vm.store.rememberStateInDataStore("FaqRow/${faq.id}") { false }

    Column(Modifier.clickable { expanded = !expanded }.padding(16.dp)) {
        Text(faq.question, style = MaterialTheme.typography.titleMedium)
        AnimatedVisibility(expanded) { Text(faq.answer) }
    }
}
```

### 4. Persisting scroll position without recomposing

Scroll position changes constantly. Use a *value* so every scroll tick doesn't trigger a recomposition:

```kotlin
@Composable
fun FeedScreen(vm: MainViewModel = viewModel()) {
    var savedIndex  by vm.store.rememberValueInDataStore("FeedScreen") { 0 }
    var savedOffset by vm.store.rememberValueInDataStore("FeedScreen") { 0 }

    val listState = rememberLazyListState(savedIndex, savedOffset)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                savedIndex = index
                savedOffset = offset
            }
    }

    LazyColumn(state = listState) {
        items(200) { Text("Item $it", Modifier.padding(16.dp)) }
    }
}
```

### 5. Tabs with independent state per tab

```kotlin
@Composable
fun TabsScreen(vm: MainViewModel = viewModel()) {
    val tabs = listOf("Home", "Explore", "Profile")
    var selected by vm.store.rememberStateInDataStore("TabsScreen") { 0 }

    Column {
        TabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { i, title ->
                Tab(selected == i, { selected = i }, text = { Text(title) })
            }
        }
        TabContent(tab = tabs[selected], vm = vm)
    }
}

@Composable
private fun TabContent(tab: String, vm: MainViewModel) {
    // One entry per tab: TabContent/Home/query, TabContent/Explore/query, ...
    var query by vm.store.rememberStateInDataStore("TabContent/$tab") { "" }

    TextField(query, { query = it }, label = { Text("Search in $tab") })
}
```

Switching tabs (which removes the old tab content from composition) doesn't lose what was typed in it.

### 6. Sharing state between composables

Use the same key string and the same property name on purpose and both composables read and write one entry:

```kotlin
@Composable
fun SearchBar(vm: MainViewModel) {
    var query by vm.store.rememberStateInDataStore("Search") { "" }    // Search/query
    TextField(query, { query = it })
}

@Composable
fun SearchResults(vm: MainViewModel) {
    var query by vm.store.rememberStateInDataStore("Search") { "" }    // same entry
    Text("Results for \"$query\"")
}
```

### 7. Multi-step wizard

Keep the current step and every answer in the store so rotating mid-flow loses nothing:

```kotlin
@Composable
fun OnboardingWizard(vm: MainViewModel = viewModel()) {
    var step     by vm.store.rememberStateInDataStore("Wizard") { 0 }
    var username by vm.store.rememberStateInDataStore("Wizard") { "" }
    var plan     by vm.store.rememberStateInDataStore("Wizard") { "free" }
    var newsletter by vm.store.rememberStateInDataStore("Wizard") { true }

    Column(Modifier.padding(16.dp)) {
        LinearProgressIndicator(progress = { (step + 1) / 3f }, Modifier.fillMaxWidth())

        when (step) {
            0 -> TextField(username, { username = it }, label = { Text("Username") })
            1 -> Row {
                listOf("free", "pro").forEach {
                    RadioButton(plan == it, { plan = it })
                    Text(it)
                }
            }
            2 -> Row {
                Checkbox(newsletter, { newsletter = it })
                Text("Subscribe to the newsletter")
            }
        }

        Row {
            if (step > 0) TextButton({ step-- }) { Text("Back") }
            if (step < 2) Button({ step++ }) { Text("Next") }
            else Button({
                /* submit */
                vm.store.clearStates("Wizard/")
            }) { Text("Finish") }
        }
    }
}
```

### 8. One-time effects that must not replay

Remember that an intro animation or a snackbar has already been shown, without any UI update:

```kotlin
@Composable
fun WelcomeScreen(vm: MainViewModel = viewModel()) {
    var introPlayed by vm.store.rememberValueInDataStore("WelcomeScreen") { false }
    val alpha = remember { Animatable(if (introPlayed) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!introPlayed) {
            alpha.animateTo(1f, tween(1200))
            introPlayed = true      // rotate → the animation won't replay
        }
    }

    Text("Welcome!", Modifier.alpha(alpha.value), style = MaterialTheme.typography.headlineLarge)
}
```

### 9. Dialogs, bottom sheets and expanded sections

UI toggles are a perfect fit. They survive rotation without any `rememberSaveable` boilerplate:

```kotlin
@Composable
fun SettingsScreen(vm: MainViewModel = viewModel()) {
    var showDeleteDialog by vm.store.rememberStateInDataStore("SettingsScreen") { false }
    var showSheet        by vm.store.rememberStateInDataStore("SettingsScreen") { false }
    var advancedOpen     by vm.store.rememberStateInDataStore("SettingsScreen") { false }

    Column {
        TextButton({ advancedOpen = !advancedOpen }) { Text("Advanced") }
        if (advancedOpen) AdvancedSettings()

        Button({ showSheet = true }) { Text("Share") }
        Button({ showDeleteDialog = true }) { Text("Delete account") }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete account?") },
            confirmButton = { TextButton({ /* delete */ }) { Text("Delete") } },
            dismissButton = { TextButton({ showDeleteDialog = false }) { Text("Cancel") } },
        )
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) { Text("Share options") }
    }
}
```

### 10. Debouncing with a local `Job` (`rememberValue`)

`rememberValue` is the `useRef` of Compose: a mutable box that never triggers recomposition:

```kotlin
@Composable
fun DebouncedSearch(onSearch: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var job by rememberValue<Job?> { null }
    var text by remember { mutableStateOf("") }

    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            job?.cancel()
            job = scope.launch {
                delay(400)
                onSearch(new)
            }
        },
        label = { Text("Search") },
    )
}
```

### 11. Tracking the previous value

```kotlin
@Composable
fun PriceTag(price: Int) {
    var previous by rememberValue { price }

    val arrow = when {
        price > previous -> "▲"
        price < previous -> "▼"
        else -> "–"
    }

    SideEffect { previous = price }   // update after the frame is composed

    Text("$price $arrow")
}
```

### 12. Combining local, stored and observable data

```kotlin
@Composable
fun Dashboard(vm: MainViewModel = viewModel()) {
    // Survives rotation, drives the UI
    var period by vm.store.rememberStateInDataStore("Dashboard") { "7d" }

    // Survives rotation, never drives the UI
    var lastRefreshAt by vm.store.rememberValueInDataStore("Dashboard") { 0L }

    // Local only: a handle that doesn't need to outlive this composition
    var refreshJob by rememberValue<Job?> { null }

    val scope = rememberCoroutineScope()

    Column {
        Row {
            listOf("7d", "30d", "90d").forEach { p ->
                FilterChip(period == p, { period = p }, label = { Text(p) })
            }
        }
        Button(onClick = {
            refreshJob?.cancel()
            refreshJob = scope.launch {
                reload(period)
                lastRefreshAt = System.currentTimeMillis()
            }
        }) { Text("Refresh") }
    }
}
```

### 13. Navigation: one entry per destination argument

With Navigation Compose, the same destination composable can show different content. Include the argument in the key so each one has its own state:

```kotlin
composable("product/{id}") { entry ->
    val id = entry.arguments?.getString("id").orEmpty()
    ProductScreen(id)
}

@Composable
fun ProductScreen(id: String, vm: MainViewModel = viewModel()) {
    // ProductScreen/<id>/quantity, ProductScreen/<id>/selectedSize
    var quantity     by vm.store.rememberStateInDataStore("ProductScreen/$id") { 1 }
    var selectedSize by vm.store.rememberStateInDataStore("ProductScreen/$id") { "M" }

    // ...
}
```

Product 1's selections don't leak into product 2, and coming back to either restores what you picked.

### 14. Prefilling state from the ViewModel

`DataStore.state` returns the very same `MutableState`, so ViewModel code can drive screens directly. Just use the full key (`<key>/<propertyName>`):

```kotlin
class MainViewModel : ViewModel() {
    val store = DataStore()

    fun openReplyWithDraft(draft: String) {
        store.state("ReplyScreen/body") { "" }.value = draft
    }
}

@Composable
fun ReplyScreen(vm: MainViewModel = viewModel()) {
    var body by vm.store.rememberStateInDataStore("ReplyScreen") { "" }
    TextField(body, { body = it })
}
```

Plain values work the same way with `store.getValue(key) { init }` and `store.setValue(key, value)`.

### 15. Clearing entries

Entries are never removed automatically. Clear them by prefix when a flow ends. Always end prefixes with `/` (`"Cart"` would also match `"CartSummary/..."`), and remember that states and values are cleared separately:

```kotlin
class SessionViewModel : ViewModel() {
    val store = DataStore()

    fun clearScreen(name: String) {
        store.clearStates("$name/")
        store.clearValues("$name/")
    }

    fun logout() {
        listOf("Profile", "Checkout", "Orders").forEach(::clearScreen)
        // ...plus your real logout logic
    }
}
```

### 16. Extending `DataStore`

`DataStore` is `open`, so you can add app-specific helpers:

```kotlin
class AppStore : DataStore() {
    fun resetCheckout() {
        clearStates("Checkout/")
        clearValues("Checkout/")
    }
}

class MainViewModel : ViewModel() {
    val store = AppStore()
}
```

### 17. Unit-testing the store

`DataStore` is plain Kotlin on top of Compose runtime state, so it's easy to test:

```kotlin
class DataStoreTest {

    @Test
    fun initializerRunsOncePerKey() {
        val store = DataStore()
        var calls = 0

        store.state("Screen/x") { calls++; 1 }
        store.state("Screen/x") { calls++; 2 }

        assertEquals(1, calls)
        assertEquals(1, store.state("Screen/x") { 0 }.value)
    }

    @Test
    fun statesAndValuesAreSeparate() {
        val store = DataStore()
        store.state("Screen/x") { 1 }
        store.setValue("Screen/x", "hello")

        assertEquals(1, store.state("Screen/x") { 0 }.value)
        assertEquals("hello", store.getValue("Screen/x") { "" })
    }

    @Test
    fun clearRemovesOnlyMatchingPrefix() {
        val store = DataStore()
        store.state("Cart/a") { 1 }
        store.state("CartSummary/a") { 2 }

        store.clearStates("Cart/")

        assertEquals(99, store.state("Cart/a") { 99 }.value)        // recreated
        assertEquals(2, store.state("CartSummary/a") { 0 }.value)   // untouched
    }
}
```

## Things to watch out for

- **Key collisions.** Two declarations with the same key string *and* property name share one entry. That's a feature when intentional (example 6) and a bug when accidental (a composable used in a list; see example 3).
- **Imports.** `rememberStateInDataStore` returns a Compose `MutableState`, so `by` needs `androidx.compose.runtime.getValue` and `setValue`.
- **Consistent types per key.** Entries are cast unchecked, so reusing a key with a different type throws a `ClassCastException`.
- **Main thread only.** The store uses plain maps with no synchronization.
- **No automatic cleanup.** Entries live as long as the store; clear them with `clearStates` / `clearValues`.
- **Don't store lifecycle-bound objects** (`Context`, `Activity`, `View`...) because the ViewModel outlives them.
- **Not persisted.** Data is lost on process death. Combine with `SavedStateHandle` or real persistence for anything that must survive it.
- **`rememberValue` doesn't notify.** If the UI must reflect a change, use state instead.
