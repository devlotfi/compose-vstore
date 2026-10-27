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

- [Docs](https://github.com/devlotfi/compose-vstore/blob/master/docs/intro.md)
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
