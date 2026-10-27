# 1. Getting started

## Requirements

- Jetpack Compose (`androidx.compose.runtime`)
- A `ViewModel` (or any object whose lifetime is longer than your composables) to host a `DataStore`

## Imports

All lib types live in one package:

```kotlin
import com.devlotfi.testlol.vstore.DataStore
import com.devlotfi.testlol.vstore.rememberValue
```

`rememberStateInDataStore` returns a Compose `MutableState<T>`, so when you use it with `by` you also need the standard Compose delegate operators:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
```

> If you forget these two imports, `var x by store.rememberStateInDataStore(...)` fails to compile with an "unresolved delegate" style error. This is a Compose requirement, not a vstore one.

## Step 1: Host a DataStore in your ViewModel

`DataStore` is an `open class`. The simplest approach is to hold one in a ViewModel:

```kotlin
class MainViewModel : ViewModel() {
    val store = DataStore()
}
```

Since `DataStore` is open, you can also subclass it to add domain-specific helpers:

```kotlin
class SettingsStore : DataStore() {
    fun resetAll() {
        clearStates("SettingsScreen/")
        clearValues("SettingsScreen/")
    }
}

class MainViewModel : ViewModel() {
    val store = SettingsStore()
}
```

## Step 2: Get the ViewModel in your composable

```kotlin
@Composable
fun CounterScreen(vm: MainViewModel = viewModel()) {
    // ...
}
```

(With Hilt, use `hiltViewModel()`. With Koin, `koinViewModel()`. Any approach works as long as the same ViewModel instance is returned across configuration changes.)

## Step 3: Declare state with a delegate

```kotlin
@Composable
fun CounterScreen(vm: MainViewModel = viewModel()) {
    var count by vm.store.rememberStateInDataStore("CounterScreen") { 0 }

    Column {
        Text("Count: $count")
        Button(onClick = { count++ }) { Text("Increment") }
    }
}
```

What happens here:

1. `rememberStateInDataStore("CounterScreen") { 0 }` returns a small delegate-provider object (remembered by `key` and `store`).
2. The `by` keyword calls its `provideDelegate`, passing the Kotlin property `count`.
3. The final key becomes `"CounterScreen/count"`.
4. The store looks the key up. If missing, it runs `{ 0 }` and creates a `MutableState<Int>`. If present, it returns the existing state (the initializer does not run again).
5. You read and write `count` like a normal Compose state.

## Step 4: Add a local-only value (optional)

For things that never need to leave the composable, such as a `Job`, a previous value, or a counter used for logging, use `rememberValue`:

```kotlin
@Composable
fun SearchBox(onSearch: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var debounceJob by rememberValue<Job?> { null }
    var query by remember { mutableStateOf("") }

    TextField(
        value = query,
        onValueChange = {
            query = it
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(300)
                onSearch(it)
            }
        }
    )
}
```

## Next

- Understand how it all fits together: [Concepts and the key system](concepts-and-key-system.md)
