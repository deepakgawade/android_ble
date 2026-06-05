kotlin# Remote API Setup Plan

## Status

| Step | File | Done? |
|------|------|-------|
| Retrofit/OkHttp versions | `gradle/libs.versions.toml` | ✅ Already added |
| Library entries | `gradle/libs.versions.toml` | ✅ Already added |
| `implementation()` lines | `app/build.gradle.kts` | ✅ Already added |
| INTERNET permission | `AndroidManifest.xml` | ⬜ |
| Domain model | `domain/model/Book.kt` | ⬜ |
| DTO | `data/remote/dto/BookDto.kt` | ⬜ |
| Retrofit interface | `data/remote/api/HarryApiService.kt` | ⬜ |
| Hilt network module | `di/NetworkModule.kt` | ⬜ |
| Update interface return type | `domain/repository/IRemoteRepository.kt` | ⬜ |
| Implement repository | `data/repository/HarryRemoteRepository.kt` | ⬜ |
| ViewModel + UI state | `ui/book/BookViewModel.kt` | 🔧 In progress |

---

## Serialization Choice: Kotlin Serialization (not Gson)

Kotlin Serialization is compile-time safe, Kotlin-idiomatic, and has no reflection overhead.
It requires a Gradle plugin and a different Retrofit converter.

---

## Dependencies

### `gradle/libs.versions.toml` — Add version

```toml
[versions]
kotlinxSerializationJson = "1.11.0"
```

> `retrofit` and `okhttp` versions are already present. No separate Gson version needed.

### `gradle/libs.versions.toml` — Add library entries

Remove the old `retrofit-converter-gson` entry and add:

```toml
[libraries]
kotlinx-serialization-json          = { group = "org.jetbrains.kotlinx",   name = "kotlinx-serialization-json",       version.ref = "kotlinxSerializationJson" }
retrofit-converter-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
```

> `converter-kotlinx-serialization` ships with Retrofit 2.11+ — same version ref, no extra version needed.

### `gradle/libs.versions.toml` — Add plugin entry

```toml
[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

> Reuses the existing `kotlin = "2.2.10"` version ref.

### `app/build.gradle.kts` — Add plugin

```kts
plugins {
    // ... existing plugins ...
    alias(libs.plugins.kotlin.serialization)   // ← add this
}
```

### `app/build.gradle.kts` — Swap dependency

Remove:
```kts
implementation(libs.retrofit.converter.gson)
```

Add:
```kts
implementation(libs.retrofit.converter.kotlinx.serialization)
implementation(libs.kotlinx.serialization.json)
```

---

## 1. AndroidManifest.xml — Add INTERNET permission

Add above the `<application>` tag:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

---

## 2. `domain/model/Book.kt` — Domain model

**Package:** `com.example.harry_android.domain.model`

```kotlin
package com.example.harry_android.domain.model

data class Book(
    val number: Int,
    val title: String,
    val originalTitle: String,
    val releaseDate: String,
    val description: String,
    val pages: Int,
    val coverUrl: String
)
```

---

## 3. `data/remote/dto/BookDto.kt` — JSON DTO

**Package:** `com.example.harry_android.data.remote.dto`

Create folder `data/remote/dto/` first.

```kotlin
package com.example.harry_android.data.remote.dto

import com.example.harry_android.domain.model.Book
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BookDto(
    @SerialName("number")        val number: Int,
    @SerialName("title")         val title: String,
    @SerialName("originalTitle") val originalTitle: String,
    @SerialName("releaseDate")   val releaseDate: String,
    @SerialName("description")   val description: String,
    @SerialName("pages")         val pages: Int,
    @SerialName("coverUrl")      val coverUrl: String
) {
    fun toDomain() = Book(
        number        = number,
        title         = title,
        originalTitle = originalTitle,
        releaseDate   = releaseDate,
        description   = description,
        pages         = pages,
        coverUrl      = coverUrl
    )
}
```

### Gson vs Kotlin Serialization — what changes in the DTO

| | Gson | Kotlin Serialization |
|---|---|---|
| Class annotation | _(none needed)_ | `@Serializable` |
| Field annotation | `@SerializedName("key")` | `@SerialName("key")` |
| Import | `com.google.gson.annotations.SerializedName` | `kotlinx.serialization.SerialName` |
| Works at compile time? | ❌ reflection at runtime | ✅ code generated at compile time |

> If your JSON key matches the property name exactly (e.g. `"title"` → `title`), `@SerialName` is optional with Kotlin Serialization. It's kept here for explicitness.

---

## 4. `data/remote/api/HarryApiService.kt` — Retrofit interface

**Package:** `com.example.harry_android.data.remote.api`

Create folder `data/remote/api/` first.

**API base URL:** `https://potterapi-fedeperin.vercel.app/`

```kotlin
package com.example.harry_android.data.remote.api

import com.example.harry_android.data.remote.dto.BookDto
import retrofit2.http.GET

interface HarryApiService {

    @GET("en/books")
    suspend fun getBooks(): List<BookDto>
}
```

> No changes needed here compared to the Gson version — the Retrofit interface is converter-agnostic.

---

## 5. `di/NetworkModule.kt` — Hilt module

**Package:** `com.example.harry_android.di`

`NetworkModule` is an `abstract class` so it can mix `@Binds` (for interface → impl binding) with
`@Provides` (for external library types) in the same module via a `companion object`.

```kotlin
package com.example.harry_android.di

import com.example.harry_android.data.remote.api.HarryApiService
import com.example.harry_android.data.repository.HarryRemoteRepository
import com.example.harry_android.domain.repository.IRemoteRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindRemoteRepository(impl: HarryRemoteRepository): IRemoteRepository

    companion object {

        private const val BASE_URL = "https://potterapi-fedeperin.vercel.app/"

        private val json = Json { ignoreUnknownKeys = true }

        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
                )
                .build()

        @Provides
        @Singleton
        fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

        @Provides
        @Singleton
        fun provideHarryApiService(retrofit: Retrofit): HarryApiService =
            retrofit.create(HarryApiService::class.java)
    }
}
```

### Why `abstract class` + `companion object` instead of `object`

| | `object` module | `abstract class` module |
|---|---|---|
| `@Provides` | ✅ | ✅ (in `companion object`) |
| `@Binds` | ❌ not allowed | ✅ as abstract function |
| Repository wiring | manual `HarryRemoteRepository(apiService)` | Hilt constructs via `@Inject constructor` |

`@Binds` is preferred over `@Provides` for interface bindings — it generates less code and lets
Hilt verify the binding at compile time.

### What changed from the Gson version

| | Gson | Kotlin Serialization |
|---|---|---|
| Converter factory | `GsonConverterFactory.create()` | `json.asConverterFactory("application/json".toMediaType())` |
| Extra import | `retrofit2.converter.gson.GsonConverterFactory` | `kotlinx.serialization.json.Json` + `retrofit2.converter.kotlinx.serialization.asConverterFactory` |
| `ignoreUnknownKeys` | automatic | set via `Json { ignoreUnknownKeys = true }` |

---

## 6. `domain/repository/IRemoteRepository.kt` — Update return type

```kotlin
package com.example.harry_android.domain.repository

import com.example.harry_android.domain.model.Book

interface IRemoteRepository {
    suspend fun getBook(): Result<Book>
}
```

---

## 7. `data/repository/HarryRemoteRepository.kt` — Real implementation

```kotlin
package com.example.harry_android.data.repository

import com.example.harry_android.data.remote.api.HarryApiService
import com.example.harry_android.domain.model.Book
import com.example.harry_android.domain.repository.IRemoteRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HarryRemoteRepository @Inject constructor(
    private val apiService: HarryApiService
) : IRemoteRepository {

    override suspend fun getBook(): Result<Book> = runCatching {
        apiService.getBooks().random().toDomain()
    }
}
```

> `@Singleton` + `@Inject constructor` lets Hilt construct and scope this class automatically.
> `NetworkModule` then binds it to `IRemoteRepository` via `@Binds` — no manual wiring needed.

---

---

## 8. `ui/book/BookViewModel.kt` — UI state holder

**Package:** `com.example.harry_android.ui.book`

### `BookViewState` — sealed UI state

```kotlin
data class BookViewState(
    val book: Book? = null,
    val loading: Boolean = false,
    val error: String? = null,
)
```

A single data class carries all three states (loading / success / error).
`book == null && loading == false && error == null` means idle (initial state).

### `BookViewModel`

```kotlin
@HiltViewModel
class BookViewModel @Inject constructor(
    private val harryRemoteRepository: IRemoteRepository,
    private val dispatcher: DefaultDispatcherProvider
) : ViewModel() {

    private val _bookViewState = MutableStateFlow(BookViewState())
    val bookViewState: StateFlow<BookViewState> = _bookViewState.asStateFlow()

    init {
        viewModelScope.launch(dispatcher.io) {
            // reserved for auto-load on creation if needed
        }
    }

    suspend fun getBookPreview() {
        _bookViewState.update { it.copy(loading = true) }

        viewModelScope.launch(dispatcher.io) {
            val data = harryRemoteRepository.getBook()

            when {
                data.isSuccess -> _bookViewState.update {
                    it.copy(loading = false, book = data.getOrNull(), error = null)
                }
                data.isFailure -> _bookViewState.update {
                    it.copy(loading = false, error = data.exceptionOrNull()?.message ?: "Unknown error")
                }
            }
        }
    }
}
```

> **Note:** The `when(data)` block in the current file is incomplete — the branches above show the intended implementation. `Result<Book>` from the repository drives the three `BookViewState` fields.

### Key wiring points

| Concern | Detail |
|---------|--------|
| DI injection | `IRemoteRepository` injected (not the concrete `HarryRemoteRepository`) — keeps ViewModel testable |
| Dispatcher | `DefaultDispatcherProvider.io` for network calls; swappable in tests with a test dispatcher |
| State holder | `MutableStateFlow` + `asStateFlow()` — exposes read-only `StateFlow` to the UI |
| Error surface | `error: String?` in state — UI renders it as a snackbar or inline message |
| Coroutine scope | `viewModelScope.launch` — automatically cancelled when ViewModel is cleared |

### Collecting state in a Composable

```kotlin
@Composable
fun BookScreen(viewModel: BookViewModel = hiltViewModel()) {
    val state by viewModel.bookViewState.collectAsStateWithLifecycle()

    when {
        state.loading        -> CircularProgressIndicator()
        state.error != null  -> Text("Error: ${state.error}")
        state.book != null   -> BookCard(book = state.book!!)
        else                 -> Button(onClick = { /* trigger */ }) { Text("Get Book") }
    }
}
```

---

## Key Decisions

| Choice | Why |
|--------|-----|
| **Kotlin Serialization** | Compile-time safe, no reflection, Kotlin-idiomatic |
| **`ignoreUnknownKeys = true`** | API may return extra fields in future; avoids crashes |
| **`Result<Book>`** return type | Caller decides how to handle errors; no checked exceptions |
| **`random()`** selection | `getBook()` takes no parameters — picks a random HP book each call |
| **`NetworkModule`** separate from `AppModule` | Keeps network concerns isolated and easy to swap in tests |
| **`IRemoteRepository` in ViewModel** | Injecting the interface (not the impl) keeps the ViewModel unit-testable with a fake |
| **`DefaultDispatcherProvider`** | Abstracts `Dispatchers.IO` so tests can inject `UnconfinedTestDispatcher` |
| **`BookViewState` data class** | One flat state object (not sealed class) is simpler when states aren't mutually exclusive (e.g., showing stale data while reloading) |

---

## 9. Navigation — Menu Screen

### Route map

| Route | Screen | Notes |
|-------|--------|-------|
| `"menu"` | `MenuScreen` | `startDestination` — entry point of the app |
| `"home"` | BLE scan screen | Existing; reached from menu |
| `"book"` | `BookScreen` | New route; reached from menu |
| `"sensor/{deviceAddress}"` | `SensorScreen` | Reached from home after connecting |

### `ui/menu/MenuScreen.kt`

**Package:** `com.example.harry_android.ui.menu`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToBooks: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Harry Potter App") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = onNavigateToHome, modifier = Modifier.fillMaxWidth()) {
                Text("Home — BLE Sensor")
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onNavigateToBooks, modifier = Modifier.fillMaxWidth()) {
                Text("Books")
            }
        }
    }
}
```

### `MainActivity.kt` changes

1. Change `startDestination` from `"home"` to `"menu"`.
2. Fix syntax error on the existing stub: `composable(="menu")` → `composable("menu")`.
3. Fill in the `"menu"` composable:

```kotlin
composable("menu") {
    MenuScreen(
        onNavigateToHome = { navController.navigate("home") },
        onNavigateToBooks = { navController.navigate("book") }
    )
}
```

4. Add the `"book"` composable after the menu route:

```kotlin
composable("book") {
    BookScreen()
}
```

5. Add imports:

```kotlin
import com.example.harry_android.ui.menu.MenuScreen
import com.example.harry_android.ui.book.BookScreen
```

### Back-stack behaviour

- Menu → Home: user can press back to return to menu.
- Menu → Books: user can press back to return to menu.
- Home → Sensor: existing `navController.navigate("sensor/$address")` is unchanged.
- `SensorScreen` back button calls `navController.popBackStack()` — unchanged.
