# 8. Recipes

Practical, copy-pasteable examples.

## Contents

1. [A form that survives rotation](#a-form-that-survives-rotation)
2. [Expandable items in a LazyColumn](#expandable-items-in-a-lazycolumn)
3. [Tab selection and per-tab scroll position](#tab-selection-and-per-tab-scroll-position)
4. [Sharing state between two composables](#sharing-state-between-two-composables)
5. [Prefilling state from the ViewModel](#prefilling-state-from-the-viewmodel)
6. [Clearing state on logout](#clearing-state-on-logout)
7. [A reusable subclass with helpers](#a-reusable-subclass-with-helpers)
8. [Combining local and stored values](#combining-local-and-stored-values)
9. [Testing](#testing)

Shared setup for all examples:

```kotlin
class MainViewModel : ViewModel() {
    val store = DataStore()
}
```

---

## A form that survives rotation

```kotlin
private const val KEY = "ContactForm"

@Composable
fun ContactForm(vm: MainViewModel = viewModel()) {
    var name    by vm.store.rememberStateInDataStore(KEY) { "" }
    var email   by vm.store.rememberStateInDataStore(KEY) { "" }
    var message by vm.store.rememberStateInDataStore(KEY) { "" }
    var sent    by vm.store.rememberStateInDataStore(KEY) { false }

    Column(Modifier.padding(16.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") })
        OutlinedTextField(email, { email = it }, label = { Text("Email") })
        OutlinedTextField(message, { message = it }, label = { Text("Message") })

        Button(
            enabled = !sent,
            onClick = {
                submit(name, email, message)
                sent = true
                vm.store.clearStates("$KEY/")   // reset form after submit
            }
        ) { Text(if (sent) "Sent" else "Send") }
    }
}
```

Keys: `ContactForm/name`, `ContactForm/email`, `ContactForm/message`, `ContactForm/sent`.

> Note: `clearStates` removes the entries from the store, but the composable's current `MutableState` objects are still held by this composition until it recomposes with fresh delegates (e.g. when it re-enters the composition). If you want the visible fields to clear immediately, also set them explicitly (`name = ""`, etc.).

---

## Expandable items in a LazyColumn

```kotlin
@Composable
fun FaqList(items: List<Faq>, vm: MainViewModel = viewModel()) {
    LazyColumn {
        items(items, key = { it.id }) { faq ->
            FaqRow(faq, vm)
        }
    }
}

@Composable
private fun FaqRow(faq: Faq, vm: MainViewModel) {
    // id in the key → each row has its own entry: FaqRow/7/expanded
    var expanded by vm.store.rememberStateInDataStore("FaqRow/${faq.id}") { false }

    Column(Modifier.clickable { expanded = !expanded }) {
        Text(faq.question)
        if (expanded) Text(faq.answer)
    }
}
```

Rows keep their expanded state through scrolling (items leaving composition), rotation, and navigation away and back.

---

## Tab selection and per-tab scroll position

```kotlin
@Composable
fun TabsScreen(vm: MainViewModel = viewModel()) {
    var selectedTab by vm.store.rememberStateInDataStore("TabsScreen") { 0 }

    Column {
        TabRow(selectedTabIndex = selectedTab) {
            listOf("Home", "Explore", "Profile").forEachIndexed { i, title ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
            }
        }
        when (selectedTab) {
            0 -> TabContent("Home", vm)
            1 -> TabContent("Explore", vm)
            2 -> TabContent("Profile", vm)
        }
    }
}

@Composable
private fun TabContent(tab: String, vm: MainViewModel) {
    // value (no recomposition on scroll) with a per-tab key
    var savedIndex by vm.store.rememberValueInDataStore("TabContent/$tab") { 0 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = savedIndex)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { savedIndex = it }
    }

    LazyColumn(state = listState) {
        items(100) { Text("$tab item $it") }
    }
}
```

Using a *value* for scroll position matters here: scroll updates fire constantly, and a *state* would recompose on every change.

---

## Sharing state between two composables

Use the same key string and property name on purpose.

```kotlin
@Composable
fun SearchBar(vm: MainViewModel) {
    var query by vm.store.rememberStateInDataStore("Search") { "" }
    TextField(query, { query = it })
}

@Composable
fun SearchResults(vm: MainViewModel) {
    var query by vm.store.rememberStateInDataStore("Search") { "" }   // same entry: Search/query
    Text("Results for: $query")
}
```

The property name must also match (`query`). Both read and write the same `MutableState`.

---

## Prefilling state from the ViewModel

```kotlin
class MainViewModel : ViewModel() {
    val store = DataStore()

    fun openComposeWithDraft(draft: String) {
        // Key format: "<key>/<propertyName>"
        store.state("ComposeScreen/body") { "" }.value = draft
    }
}

@Composable
fun ComposeScreen(vm: MainViewModel = viewModel()) {
    var body by vm.store.rememberStateInDataStore("ComposeScreen") { "" }
    TextField(body, { body = it })
}
```

---

## Clearing state on logout

```kotlin
class SessionViewModel : ViewModel() {
    val store = DataStore()

    fun logout() {
        listOf("Profile/", "Checkout/", "Orders/").forEach { prefix ->
            store.clearStates(prefix)
            store.clearValues(prefix)
        }
        // …plus your real logout logic
    }
}
```

If you scoped all UI state under a few well-chosen prefixes, one function handles it all.

---

## A reusable subclass with helpers

```kotlin
class AppStore : DataStore() {

    fun clearScreen(name: String) {
        clearStates("$name/")
        clearValues("$name/")
    }
}

class MainViewModel : ViewModel() {
    val store = AppStore()
}

// usage
vm.store.clearScreen("Checkout")
```

---

## Combining local and stored values

```kotlin
@Composable
fun Dashboard(vm: MainViewModel = viewModel()) {
    // Survives rotation, drives UI
    var period by vm.store.rememberStateInDataStore("Dashboard") { "7d" }

    // Survives rotation, never drives UI
    var lastRefreshMillis by vm.store.rememberValueInDataStore("Dashboard") { 0L }

    // Local only: gone after rotation, never drives UI
    var refreshJob by rememberValue<Job?> { null }

    val scope = rememberCoroutineScope()

    Button(onClick = {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            reload(period)
            lastRefreshMillis = System.currentTimeMillis()
        }
    }) { Text("Refresh ($period)") }
}
```

---

## Testing

`DataStore` has no Android dependencies besides Compose runtime state, so you can unit-test its behavior directly.

```kotlin
class DataStoreTest {

    @Test
    fun `initializer runs only once per key`() {
        val store = DataStore()
        var calls = 0

        store.state("Screen/x") { calls++; 1 }
        store.state("Screen/x") { calls++; 2 }

        assertEquals(1, calls)
        assertEquals(1, store.state("Screen/x") { 0 }.value)
    }

    @Test
    fun `values are separate from states`() {
        val store = DataStore()
        store.state("Screen/x") { 1 }
        store.setValue("Screen/x", "hello")

        assertEquals(1, store.state("Screen/x") { 0 }.value)
        assertEquals("hello", store.getValue("Screen/x") { "" })
    }

    @Test
    fun `clear removes only matching prefix`() {
        val store = DataStore()
        store.state("Cart/a") { 1 }
        store.state("CartSummary/a") { 2 }

        store.clearStates("Cart/")

        assertEquals(99, store.state("Cart/a") { 99 }.value)         // recreated
        assertEquals(2,  store.state("CartSummary/a") { 0 }.value)   // untouched
    }
}
```

For UI behavior (surviving rotation), use `createComposeRule()` with `StateRestorationTester` or recreate the activity, and assert that values entered before recreation are still shown.
