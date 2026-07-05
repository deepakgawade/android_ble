# Aviation Weather App — Modularization Plan

A multi-module Android project plan for integrating the [AviationWeather.gov Data API](https://aviationweather.gov/data/api/), following the modularization patterns from [Now in Android](https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md).

> **Project context:** Single-module app right now. Namespace `com.example.harry_android`. Already has Hilt, Retrofit, OkHttp, kotlinx.serialization, Compose.
>
> **Scope:** Existing code inside `app/` is **not touched at all** — no file moves, no package renames. New modules are purely additive: new folders created at the project root alongside `app/`. The aviation weather feature is built fresh in those new modules.

> **Status update (current):** Phase 1 is done and Phase 2 modules are scaffolded. Read this before following any code snippet below.
> - `core:model`, `core:network`, `core:data`, `core:common`, `core:ui` all exist and are registered in `settings.gradle.kts`.
> - **Real package names are shorter than originally planned**: `com.example.model`, `com.example.network`, `com.example.data`, `com.example.common`, `com.example.ui` — **not** `com.example.core.model` / `com.example.core.network` / etc. as drafted in sections 7–10 below. Every new snippet you write must import from the short form.
> - `AviationWeatherApi`, `NetworkClient` (`buildAviationWeatherApi()`), `MetarDto`, `MetaMapper.kt` (`toDomain()`), and `AviationWeatherRepository` (with the `WeatherResult` sealed interface) are already implemented in `core:network` / `core:data`, matching sections 8–9 almost exactly. Only the METAR endpoint exists — **no TAF/forecast call yet**, so `feature:weatherbrief` can only show METAR for now.
> - `app/di/AviationModule.kt` and the `app/build.gradle.kts` dependencies from section 10 are **already wired**. Don't redo them — Hilt already provides `AviationWeatherApi` and `AviationWeatherRepository` as singletons.
> - `feature:weatherbrief:api` and `feature:weatherbrief:impl` **directories exist** (registered in `settings.gradle.kts`, namespaces `com.example.weatherbrief.api` / `com.example.weatherbrief.impl`) but are still wizard-generated empty stubs (default `build.gradle.kts` with unused `appcompat`/`material` deps, empty `AndroidManifest.xml`, only `ExampleUnitTest.kt`/`ExampleInstrumentedTest.kt`). **Nothing feature-specific has been written in them yet** — that's what section 10.1–10.4 below walks through.
> - ⚠️ The real `core:model` classes have typos baked in — `Metar.icaId` (not `icaoId`), `devpointC` (not `dewpointC`), `visibilityStatusMi` (not `visibilityStatuteMi`), and `FlightCategory.UNKOWN` (not `UNKNOWN`). Any new code (ViewModel, Composable) must use these exact real names, not the clean names shown in sections 7–9.
> - AGP 9.1.1 in this project compiles Kotlin in library modules without an explicit `kotlin-android` plugin (confirmed: `core:network` builds fine with only `android-library` + `kotlin-serialization` applied). New feature modules can follow the same minimal-plugin pattern — just add `kotlin-compose` for Compose support.
> - This project applies `hilt` / `ksp` / `kotlin-serialization` plugins directly per-module (see `app/build.gradle.kts`) rather than declaring them `apply false` in the root `build.gradle.kts` first. Follow that same direct-apply convention in new modules.

---

## 1. Goals

- Reusable `core:*` modules so the network/data layer can be dropped into future apps (e.g. a flight-planning app) without modification.
- Clean dependency direction: `app → feature → core`, never the reverse.
- Feature isolated behind an `api` (nav contract) / `impl` (UI + logic) split.
- Respect API constraints: rate limit (100 req/min, 1 req/min/thread), custom User-Agent, 204-no-content handling.

---

## 2. Module list

| Module | Type | Responsibility |
|---|---|---|
| `core:model` | Kotlin library | Domain models: `Metar`, `Taf`, `Airport`, `FlightCategory`, etc. No Android deps. |
| `core:network` | Android library | Retrofit `AviationWeatherApi`, DTOs, mappers DTO → domain model, OkHttp client config (User-Agent, logging, rate-limit interceptor). |
| `core:data` | Android library | `AviationWeatherRepository` — orchestrates network calls, exposes suspend/Flow APIs to features, owns caching policy. |
| `core:common` | Kotlin library | Shared utilities: `Result` wrapper, dispatchers, ICAO code validation. |
| `core:ui` | Android library | Shared Compose components: flight-category badge, wind/visibility chips, loading/error states. |
| `core:designsystem` | Android library | Theme, typography, color tokens, icons (optional if reusing Material defaults). |
| `feature:weatherbrief:api` | Android library | Nav key (e.g. `AirportWeatherRoute(icao: String)`), thin public contract. |
| `feature:weatherbrief:impl` | Android library | `WeatherBriefViewModel`, `WeatherBriefScreen`, calls `core:data`. |
| `feature:airportsearch:api` / `:impl` | (Phase 2) | ICAO/airport lookup + autocomplete, uses `core:data` station/airport info endpoint. |
| `app` | Android application | `MainActivity`, `NavHost`, DI graph root, app-level theming. |

---

## 3. Dependency graph

```
app
 ├─→ feature:weatherbrief:impl
 │        ├─→ feature:weatherbrief:api
 │        ├─→ core:data
 │        ├─→ core:ui
 │        └─→ core:common
 ├─→ feature:airportsearch:impl   (phase 2)
 └─→ core:designsystem

core:data
 ├─→ core:network
 └─→ core:model

core:network
 └─→ core:model

core:ui
 └─→ core:model

Rule: core:* never depends on feature:* or app.
Rule: feature:*:api never depends on another feature.
Rule: feature:*:impl may depend on other features' :api only.
```

---

## 4. API mapping (AviationWeather.gov)

| Endpoint | Used by | Notes |
|---|---|---|
| `GET /api/data/metar?ids={ICAO}&format=json` | `core:network` → `core:data.getCurrentConditions()` | Current observation. Worldwide coverage. |
| `GET /api/data/taf?ids={ICAO}&format=json` | `core:network` → `core:data.getForecast()` | Terminal forecast. Worldwide coverage. |
| `GET /api/data/stationinfo?ids={ICAO}&format=json` | Phase 2 — `feature:airportsearch` | Station metadata. |
| `GET /api/data/pirep?...` | Phase 3 (optional) | Pilot reports, US + North Atlantic only. |
| `GET /api/data/sigmet` / `gairmet` | Phase 3 (optional) | Hazard overlays. |

**Constraints to encode in `core:network`:**
- Custom `User-Agent` header on every request.
- Max 1 request/min per thread — debounce repeated calls to the same ICAO in `core:data` (simple in-memory cache with TTL, e.g. 5 min for METAR, 30 min for TAF).
- Treat HTTP 204 as "no data" state, not an error.
- No CORS relevance (native client).

---

## 5. Build setup — one-time changes (do these FIRST)

### Step 5a — Add `android.library` plugin to `libs.versions.toml`

File: `gradle/libs.versions.toml`

In the `[plugins]` section, add:
```toml
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android  = { id = "org.jetbrains.kotlin.android",  version.ref = "kotlin" }
```

> **Why:** `android-application` is only for the `app` module. Every `core:*` and `feature:*` module uses `android-library` or plain `kotlin` instead.

### Step 5b — Register new modules in `settings.gradle.kts`

```kotlin
// Add AFTER include(":app")
include(":core:model")
include(":core:network")
include(":core:data")
include(":core:common")
```

Add more as you create them. Gradle will fail at sync if a module is listed here but the directory doesn't exist yet, so only add what you've already created.

### Step 5c — Declare plugins in root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library)     apply false   // ADD THIS
    alias(libs.plugins.kotlin.android)      apply false   // ADD THIS
    alias(libs.plugins.kotlin.compose)      apply false
}
```

---

## 6. How to physically create a module (the pattern)

Every module follows this folder structure. Example for `core:model`:

```
android_ble/
  core/
    model/
      build.gradle.kts          ← module build script
      src/
        main/
          java/
            com/example/core/model/
              Metar.kt
              Taf.kt
              FlightCategory.kt
        test/
          java/
            com/example/core/model/
              MetarTest.kt      ← optional unit tests
```

In Android Studio you can right-click the project root → **New → Module → Android Library** and name it `core.model`, then rename the directory from `core.model/` to `core/model/` manually. Or create the folder structure yourself — Gradle only cares that `include(":core:model")` maps to a directory `core/model/` with a `build.gradle.kts`.

---

## 7. Phase 1 — `core:model` (start here — no Android dependencies)

### 7.1 Create `core/model/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)   // pure Kotlin — no Android
}
```

> **Flutter analogy:** This is like a plain Dart package with no Flutter dependency — just pure Dart classes. `kotlin.jvm` = `dart` package; `android.library` = `flutter` package.

Wait — `kotlin.jvm` is not in `libs.versions.toml` yet. Add it:

```toml
# In [plugins] section of libs.versions.toml
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

And in root `build.gradle.kts`:
```kotlin
alias(libs.plugins.kotlin.jvm) apply false   // ADD THIS
```

### 7.2 Create domain models in `core/model/src/main/java/com/example/core/model/`

**`FlightCategory.kt`**
```kotlin
package com.example.core.model

enum class FlightCategory { VFR, MVFR, IFR, LIFR, UNKNOWN }
```

**`Metar.kt`**
```kotlin
package com.example.core.model

data class Metar(
    val icaoId: String,
    val observationTime: String,
    val tempC: Double?,
    val dewpointC: Double?,
    val windDirDegrees: Int?,
    val windSpeedKt: Int?,
    val visibilityStatuteMi: Double?,
    val altimeterInHg: Double?,
    val rawObservation: String,
    val flightCategory: FlightCategory,
)
```

**`Taf.kt`**
```kotlin
package com.example.core.model

data class Taf(
    val icaoId: String,
    val issueTime: String,
    val validFrom: String,
    val validTo: String,
    val rawTaf: String,
)
```

### 7.3 Register in `settings.gradle.kts`

```kotlin
include(":core:model")
```

Sync Gradle. You should see `core.model` appear in the project panel.

---

## 8. Phase 1 — `core:network`

Depends on: `:core:model`

### 8.1 Create `core/network/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.core.network"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
}
```

### 8.2 Create DTOs in `core/network/src/main/java/com/example/core/network/dto/`

**`MetarDto.kt`**
```kotlin
package com.example.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MetarDto(
    @SerialName("icaoId")        val icaoId: String,
    @SerialName("obsTime")       val observationTime: String,
    @SerialName("temp")          val tempC: Double? = null,
    @SerialName("dewp")          val dewpointC: Double? = null,
    @SerialName("wdir")          val windDirDegrees: Int? = null,
    @SerialName("wspd")          val windSpeedKt: Int? = null,
    @SerialName("visib")         val visibilityStatuteMi: Double? = null,
    @SerialName("altim")         val altimeterInHg: Double? = null,
    @SerialName("rawOb")         val rawObservation: String = "",
    @SerialName("fltcat")        val flightCategory: String? = null,
)
```

### 8.3 Create mapper in `core/network/src/main/java/com/example/core/network/mapper/`

**`MetarMapper.kt`**
```kotlin
package com.example.core.network.mapper

import com.example.core.model.FlightCategory
import com.example.core.model.Metar
import com.example.core.network.dto.MetarDto

fun MetarDto.toDomain() = Metar(
    icaoId = icaoId,
    observationTime = observationTime,
    tempC = tempC,
    dewpointC = dewpointC,
    windDirDegrees = windDirDegrees,
    windSpeedKt = windSpeedKt,
    visibilityStatuteMi = visibilityStatuteMi,
    altimeterInHg = altimeterInHg,
    rawObservation = rawObservation,
    flightCategory = when (flightCategory) {
        "VFR"  -> FlightCategory.VFR
        "MVFR" -> FlightCategory.MVFR
        "IFR"  -> FlightCategory.IFR
        "LIFR" -> FlightCategory.LIFR
        else   -> FlightCategory.UNKNOWN
    },
)
```

### 8.4 Create the Retrofit interface in `core/network/src/main/java/com/example/core/network/`

**`AviationWeatherApi.kt`**
```kotlin
package com.example.core.network

import com.example.core.network.dto.MetarDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface AviationWeatherApi {
    @GET("api/data/metar")
    suspend fun getMetar(
        @Query("ids") icao: String,
        @Query("format") format: String = "json",
    ): Response<List<MetarDto>>
}
```

> `Response<T>` (not just `T`) lets us check `code() == 204` for "no data" without throwing.

### 8.5 Create OkHttp client builder in `core/network/src/main/java/com/example/core/network/`

**`NetworkClient.kt`**
```kotlin
package com.example.core.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType

private const val BASE_URL   = "https://aviationweather.gov/"
private const val USER_AGENT = "com.example.harry_android/1.0 (contact: you@example.com)"

fun buildAviationWeatherApi(): AviationWeatherApi {
    val userAgentInterceptor = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
        )
    }
    val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }
    val client = OkHttpClient.Builder()
        .addInterceptor(userAgentInterceptor)
        .addInterceptor(logging)
        .build()

    val json = Json { ignoreUnknownKeys = true }

    return Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AviationWeatherApi::class.java)
}
```

---

## 9. Phase 1 — `core:data`

Depends on: `:core:model`, `:core:network`

### 9.1 Create `core/data/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.core.data"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(libs.androidx.core.ktx)
}
```

### 9.2 Create the repository in `core/data/src/main/java/com/example/core/data/`

**`AviationWeatherRepository.kt`**
```kotlin
package com.example.core.data

import com.example.core.model.Metar
import com.example.core.network.AviationWeatherApi
import com.example.core.network.mapper.toDomain

sealed interface WeatherResult<out T> {
    data class Success<T>(val data: T) : WeatherResult<T>
    data object NoData                 : WeatherResult<Nothing>
    data class Error(val message: String) : WeatherResult<Nothing>
}

class AviationWeatherRepository(private val api: AviationWeatherApi) {

    suspend fun getMetar(icao: String): WeatherResult<Metar> = try {
        val response = api.getMetar(icao)
        when {
            response.code() == 204      -> WeatherResult.NoData
            response.isSuccessful       -> {
                val metar = response.body()?.firstOrNull()?.toDomain()
                if (metar != null) WeatherResult.Success(metar) else WeatherResult.NoData
            }
            else -> WeatherResult.Error("HTTP ${response.code()}")
        }
    } catch (e: Exception) {
        WeatherResult.Error(e.message ?: "Unknown error")
    }
}
```

> **Flutter analogy:** `WeatherResult` is like a Dart sealed class (`Success`, `NoData`, `Error`). Same concept — you `when` on it in the ViewModel exactly like you'd pattern-match in Dart.

---

## 10. Wiring Hilt DI (in `app` module)

After creating the three core modules, wire them into your existing Hilt setup.

File: `app/src/main/java/com/example/harry_android/di/AviationModule.kt`

```kotlin
package com.example.harry_android.di

import com.example.core.data.AviationWeatherRepository
import com.example.core.network.AviationWeatherApi
import com.example.core.network.buildAviationWeatherApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AviationModule {

    @Provides @Singleton
    fun provideAviationWeatherApi(): AviationWeatherApi = buildAviationWeatherApi()

    @Provides @Singleton
    fun provideAviationWeatherRepository(api: AviationWeatherApi) =
        AviationWeatherRepository(api)
}
```

And in `app/build.gradle.kts`, add the module dependencies:
```kotlin
dependencies {
    // existing deps ...
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
}
```

---

## 10.1 `feature:weatherbrief:api` — the nav contract

This module should hold nothing but the route/contract — no UI, no ViewModel, no dependency on `core:*`.

**File:** `feature/weatherbrief/api/src/main/java/com/example/weatherbrief/api/WeatherBriefRoute.kt`

```kotlin
package com.example.weatherbrief.api

object WeatherBriefRoute {
    const val ROUTE = "weatherbrief"
}
```

> **Flutter analogy:** Same idea as a route-name constant you'd share between your `GoRouter` config and the widget that navigates to it — a single source of truth for the string, owned by the feature, so `app` and other features never hardcode `"weatherbrief"` themselves.

`feature/weatherbrief/api/build.gradle.kts` needs no changes for this — the existing wizard-generated `android-library` setup already compiles a plain Kotlin `object`. (The stub's `appcompat`/`material`/`core-ktx` dependencies are unused by this file but harmless; leave them unless you want to do an unrelated cleanup pass.)

---

## 10.2 `feature:weatherbrief:impl` — ViewModel + screen

Depends on: `:feature:weatherbrief:api`, `:core:data`, `:core:model`.

### 10.2.1 Update `feature/weatherbrief/impl/build.gradle.kts`

The stub currently has plain `android-library` with View-system deps (`appcompat`, `material`). Since this module renders Compose UI and a `@HiltViewModel`, replace its contents:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.weatherbrief.impl"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":feature:weatherbrief:api"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

`libs.androidx.lifecycle.runtime.compose` and `libs.androidx.lifecycle.viewmodel.compose` don't exist yet in `gradle/libs.versions.toml` — the `app` module gets them transitively today (via `androidx-activity-compose` / `androidx-hilt-navigation-compose`), but a new module needs them declared explicitly. Add to the `[libraries]` section, reusing the existing `lifecycleRuntimeKtx` version ref:

```toml
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
```

### 10.2.2 ViewModel

**File:** `feature/weatherbrief/impl/src/main/java/com/example/weatherbrief/impl/WeatherBriefViewModel.kt`

```kotlin
package com.example.weatherbrief.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AviationWeatherRepository
import com.example.data.WeatherResult
import com.example.model.Metar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeatherBriefViewState(
    val icao: String = "",
    val loading: Boolean = false,
    val metar: Metar? = null,
    val noData: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class WeatherBriefViewModel @Inject constructor(
    private val repository: AviationWeatherRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WeatherBriefViewState())
    val state: StateFlow<WeatherBriefViewState> = _state.asStateFlow()

    fun onIcaoChanged(icao: String) {
        _state.update { it.copy(icao = icao) }
    }

    fun searchMetar() {
        val icao = _state.value.icao.trim()
        if (icao.isEmpty()) return

        _state.update { it.copy(loading = true, error = null, noData = false) }
        viewModelScope.launch {
            when (val result = repository.getMetar(icao)) {
                is WeatherResult.Success -> _state.update { it.copy(loading = false, metar = result.data) }
                is WeatherResult.NoData -> _state.update { it.copy(loading = false, metar = null, noData = true) }
                is WeatherResult.Error -> _state.update { it.copy(loading = false, metar = null, error = result.message) }
            }
        }
    }
}
```

> No `DispatcherProvider` here — that class lives in `app/core/dispatcher/` and `feature:*` must never depend on `app` (dependency rule from section 3). Retrofit's suspend functions already move the call off the main thread internally, so launching on the default `viewModelScope` (Main-immediate) dispatcher is fine, same as the plain `AviationWeatherRepository.getMetar()` call.

### 10.2.3 Screen — reuse the existing `BookScreen`/`SensorScreen` shape

The codebase already has a convention for this exact shape (Hilt-injected ViewModel, `StateFlow` view-state, `when` over loading/error/data) in `app/src/main/java/com/example/harry_android/ui/book/BookScreen.kt`. Mirror it rather than inventing a new pattern.

**File:** `feature/weatherbrief/impl/src/main/java/com/example/weatherbrief/impl/WeatherBriefScreen.kt`

```kotlin
package com.example.weatherbrief.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Metar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherBriefScreen(viewModel: WeatherBriefViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    WeatherBriefScreenContent(
        state = state,
        onIcaoChanged = viewModel::onIcaoChanged,
        onSearch = viewModel::searchMetar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherBriefScreenContent(
    state: WeatherBriefViewState,
    onIcaoChanged: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Weather Brief") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = state.icao,
                onValueChange = onIcaoChanged,
                label = { Text("ICAO code, e.g. KSFO") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                Text("Get METAR")
            }
            Spacer(Modifier.height(24.dp))

            when {
                state.loading -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator()
                }
                state.error != null -> Text("Error: ${state.error}")
                state.noData -> Text("No data available for \"${state.icao}\"")
                state.metar != null -> MetarCard(state.metar)
            }
        }
    }
}

@Composable
private fun MetarCard(metar: Metar) {
    Column {
        Text(metar.icaId, style = MaterialTheme.typography.titleLarge)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Flight category: ${metar.flightCategory}")
        Text("Observed: ${metar.observationTime}")
        metar.tempC?.let { Text("Temp: $it °C") }
        if (metar.windDirDegrees != null && metar.windSpeedKt != null) {
            Text("Wind: ${metar.windDirDegrees}° at ${metar.windSpeedKt} kt")
        }
        metar.visibilityStatusMi?.let { Text("Visibility: $it sm") }
        metar.altimeterInHg?.let { Text("Altimeter: $it inHg") }
        Spacer(Modifier.height(8.dp))
        Text(metar.rawObservation, style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true)
@Composable
private fun WeatherBriefScreenPreview() {
    WeatherBriefScreenContent(state = WeatherBriefViewState(icao = "KSFO"), onIcaoChanged = {}, onSearch = {})
}
```

Note the field names match the **real** (typo'd) `Metar` class: `icaId`, `visibilityStatusMi` — not the clean names from section 7.2.

---

## 10.3 Wiring `feature:weatherbrief` into `app`

Phase 1's Hilt wiring (`AviationModule`, `core:*` deps in `app/build.gradle.kts`) is already done — this step only adds the feature module and its nav entry.

### 10.3.1 `app/build.gradle.kts`

```kotlin
dependencies {
    // existing deps ...
    implementation(project(":feature:weatherbrief:api"))
    implementation(project(":feature:weatherbrief:impl"))
}
```

### 10.3.2 `MenuScreen.kt` — add a third entry point

`app/src/main/java/com/example/harry_android/ui/common/MenuScreen.kt` currently takes `onNavigationToHome` and `onNavigationToBook`. Add a third callback and button, following the existing pattern:

```kotlin
fun MenuScreen(
    onNavigationToHome: () -> Unit,
    onNavigationToBook: () -> Unit,
    onNavigationToWeatherBrief: () -> Unit,
) {
    // ... existing buttons ...
    Spacer(Modifier.height(16.dp))
    Button(onClick = onNavigationToWeatherBrief, modifier = Modifier.fillMaxWidth()) {
        Text("Weather Brief")
    }
}
```

(Update the `@Preview` composable's call site to pass a no-op lambda for the new parameter too.)

### 10.3.3 `MainActivity.kt` — register the route

Add the import and a `composable` entry to the existing `NavHost` in `app/src/main/java/com/example/harry_android/MainActivity.kt`:

```kotlin
import com.example.weatherbrief.api.WeatherBriefRoute
import com.example.weatherbrief.impl.WeatherBriefScreen

// inside NavHost(...) { ... }
composable(route = "menu") {
    MenuScreen(
        onNavigationToBook = { navController.navigate("book") },
        onNavigationToHome = { navController.navigate("home") },
        onNavigationToWeatherBrief = { navController.navigate(WeatherBriefRoute.ROUTE) },
    )
}
composable(WeatherBriefRoute.ROUTE) {
    WeatherBriefScreen()
}
```

No DI changes needed — `WeatherBriefViewModel`'s `@Inject constructor(repository: AviationWeatherRepository)` resolves against the `@Provides` binding already in `app/di/AviationModule.kt`.

### 10.3.4 Manual verification

Run the app, navigate Menu → Weather Brief, type a known ICAO (`KSFO`, `KJFK`, `EGLL`) and confirm METAR renders; also try a bogus code to confirm the "No data" branch and airplane-mode/no-network to confirm the error branch.

---

## 11. Implementation phases — updated status

### Phase 1 — Core plumbing
- [x] **Step 5** — One-time build setup (libs.versions.toml + root build.gradle.kts)
- [x] **Step 7** — Create `core:model` with `Metar`, `Taf`, `FlightCategory` (real package: `com.example.model`, with typos — see status callout above)
- [x] **Step 8** — Create `core:network` with DTOs + mapper + Retrofit interface + OkHttp client (real package: `com.example.network`; METAR only, no TAF endpoint)
- [x] **Step 9** — Create `core:data` with `AviationWeatherRepository` (real package: `com.example.data`)
- [x] **Step 10** — Wire Hilt in `app` (`app/di/AviationModule.kt`, `app/build.gradle.kts` deps)
- [ ] Unit test mappers and repository (mock API responses, including 204 case)

### Phase 2 — First feature
- [x] `feature:weatherbrief:api` / `feature:weatherbrief:impl` module directories scaffolded + registered in `settings.gradle.kts`
- [ ] **Section 10.1** — Add `WeatherBriefRoute` nav key to `feature:weatherbrief:api`
- [ ] **Section 10.2** — Implement `feature:weatherbrief:impl`: `WeatherBriefViewModel` + `WeatherBriefScreen` (ICAO input → METAR display; TAF deferred until `core:network`/`core:data` add a forecast call)
- [ ] **Section 10.3** — Wire into `app`'s `NavHost` + `MenuScreen`
- [ ] Manual test against live API with a few known ICAO codes (e.g. `KSFO`, `KJFK`, `EGLL`)

### Phase 3 — Polish & reuse validation
- [ ] Add `core:ui` shared components (flight category badge, loading/error UI)
- [ ] Add simple caching/TTL in `core:data` to respect rate limits
- [ ] Validate reuse: add `core:network` + `core:data` to a throwaway second app module to confirm zero-change portability
- [ ] (Optional) Airport search feature using `stationinfo` endpoint

---

## 12. Open questions / decisions to make

- [x] Networking format: **kotlinx.serialization** (already in project)
- [x] DI approach: **Hilt** (already in project)
- [ ] Caching strategy: in-memory only, or persist last-known METAR/TAF via `core:datastore` for offline viewing
- [ ] Whether G-AIRMET/SIGMET hazard data is in scope for v1 or deferred

---

## 13. Gradle sync checklist

After each module creation, sync fails are usually one of:
1. Module listed in `settings.gradle.kts` but directory doesn't exist → create the directory
2. Plugin alias not found → add it to `libs.versions.toml` AND root `build.gradle.kts`
3. `project(":core:model")` dependency but namespace typo → check the `namespace` in that module's `build.gradle.kts`

---

## 14. References

- [Android modularization guide](https://developer.android.com/topic/modularization)
- [Now in Android — Modularization Learning Journey](https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md)
- [AviationWeather.gov Data API docs](https://aviationweather.gov/data/api/)
- [AviationWeather.gov OpenAPI spec](https://aviationweather.gov/data/schema/openapi.yaml)
