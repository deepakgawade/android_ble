# Knowledge Base — Android / Hilt DI (Flutter Developer Reference)

Flutter analogies are provided throughout since concepts map closely to `get_it` / `injectable` / `dartz`.

---

## 1. Hilt Dependency Injection Overview

In Flutter you manually register and resolve dependencies via `get_it`:
```dart
void setupDependencies() {
  getIt.registerSingleton<OkHttpClient>(OkHttpClient());
  getIt.registerSingleton<Retrofit>(Retrofit(getIt<OkHttpClient>()));
}
```

In Android/Hilt you describe dependencies with annotations — Hilt generates all the wiring at compile time.

---

## 2. `@Module`

Marks a class as a **dependency registration container** — the Hilt equivalent of your `setupDependencies()` function.

Instead of one giant setup function, you split into logical groups:

| Module | Responsibility |
|---|---|
| `AppModule` | BLE infrastructure (GattDecoder, BleSessionManager, …) |
| `NetworkModule` | HTTP infrastructure (OkHttpClient, Retrofit, ApiService, …) |
| `AviationModule` | Aviation weather DI wiring (currently empty — no providers added yet) |

**Flutter analogy:**
```dart
// AppModule equivalent
void setupBleDependencies() {
  getIt.registerSingleton<GattDecoder>(GattDecoder());
  getIt.registerSingleton<BleSessionManager>(BleSessionManager(getIt<GattDecoder>()));
}

// NetworkModule equivalent
void setupNetworkDependencies() {
  getIt.registerSingleton<OkHttpClient>(OkHttpClient());
  getIt.registerSingleton<Retrofit>(Retrofit(getIt<OkHttpClient>()));
}
```

---

## 3. `@InstallIn`

Tells Hilt **which scoped container** to register dependencies in.

In Flutter, `GetIt` is one global container. In Hilt, there are multiple containers tied to different lifecycles:

| Hilt Component | Lives as long as… | Flutter equivalent |
|---|---|---|
| `SingletonComponent` | The whole app | `getIt` global instance |
| `ActivityComponent` | One screen (Activity) | Scoped `GetIt` disposed on screen exit |
| `ViewModelComponent` | One ViewModel | State tied to a `ChangeNotifier` |

```kotlin
@Module
@InstallIn(SingletonComponent::class)   // ← app-level, created once, never destroyed
object AppModule { ... }
```

`Retrofit`, `OkHttpClient`, repositories — these all belong in `SingletonComponent` because they should be created once and reused everywhere.

---

## 4. `@Provides` vs `@Binds`

### `@Provides` — you write the construction logic

Use when Hilt cannot build something on its own (external library classes like `Retrofit`, `OkHttpClient`).

```kotlin
@Provides @Singleton
fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
    Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .build()
```

**Flutter equivalent:**
```dart
getIt.registerSingleton<Retrofit>(
  Retrofit.Builder()
    .baseUrl("https://...")
    .client(getIt<OkHttpClient>())
    .build(),
);
```

---

### `@Binds` — map an interface to an implementation

Use when Hilt already knows how to build the implementation (it has `@Inject constructor`) and you just need to say *"when someone asks for `IRemoteRepository`, give them `HarryRemoteRepository`."*

```kotlin
@Binds @Singleton
abstract fun bindRemoteRepository(impl: HarryRemoteRepository): IRemoteRepository
```

**Flutter equivalent:**
```dart
// HarryRemoteRepository already registered via @injectable
getIt.registerFactory<IRemoteRepository>(() => getIt<HarryRemoteRepository>());
```

### Side-by-side

| | `@Provides` | `@Binds` |
|---|---|---|
| You write | Full construction code | Just the function signature |
| Used for | External libs (`Retrofit`, `OkHttpClient`) | Your own classes with `@Inject constructor` |
| Method type | Regular `fun` (has body) | `abstract fun` (no body) |
| Performance | Slightly more generated code | Minimal — compile-time alias |

---

## 5. `object` vs `abstract class` for Hilt Modules

This is determined by whether the module needs `@Binds`.

### `object` — only `@Provides`

A Kotlin `object` is a concrete singleton. Every function must have a body.
Use it when all your providers are `@Provides` (no interface bindings needed).

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {                          // concrete singleton, no abstract functions
    @Provides @Singleton
    fun provideGattDecoder(): GattDecoder = GattDecoder()
}
```

**Flutter analogy** — a class with only `static` methods:
```dart
class AppModule {
  static GattDecoder provideGattDecoder() => GattDecoder();
}
```

---

### `abstract class` — has at least one `@Binds`

`@Binds` functions have **no body** — Hilt generates the wiring. Abstract functions can only exist in an `abstract class`, not an `object`. `@Provides` go into a `companion object` inside it.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds @Singleton                           // abstract — no body
    abstract fun bindRemoteRepository(impl: HarryRemoteRepository): IRemoteRepository

    companion object {                          // @Provides go here (treated as static)
        @Provides @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

        @Provides @Singleton
        fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL).client(client).build()

        @Provides @Singleton
        fun provideHarryApiService(retrofit: Retrofit): HarryApiService =
            retrofit.create(HarryApiService::class.java)
    }
}
```

**Flutter analogy** — abstract class with both abstract and static methods:
```dart
abstract class NetworkModule {
  IRemoteRepository bindRepo(HarryRemoteRepository impl); // abstract
  static Retrofit provideRetrofit() => Retrofit(...);     // static
}
```

### When to use which

| Situation | Use |
|---|---|
| Only `@Provides` (external libs, builders) | `object` |
| Has at least one `@Binds` (interface → impl) | `abstract class` + `companion object` |
| No providers yet (scaffold) | `object` — an empty module is still valid; switch to `abstract class` the moment a `@Binds` is added |

**Real example in this codebase — `AviationModule`:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AviationModule   // empty for now — no @Provides, no @Binds
```
It's `object`, not `abstract class`, purely because it has *no* members yet. The choice isn't locked in — it only needs to flip to `abstract class` if/when a `@Binds` (e.g. binding an `AviationWeatherRepository` interface to its impl) is added. Until then, `object` is correct and simplest.

---

## 6. `@Inject constructor` and `@Singleton`

Adding `@Inject constructor` to your own class tells Hilt *"you know how to build me — just look at my constructor parameters."* No `@Provides` needed.

```kotlin
@Singleton
class HarryRemoteRepository @Inject constructor(
    private val apiService: HarryApiService   // Hilt resolves this automatically
) : IRemoteRepository { ... }
```

`@Singleton` scopes the instance to `SingletonComponent` — only one instance ever created.

**Flutter equivalent** — `@injectable` + `@singleton` annotations from the `injectable` package:
```dart
@singleton
@Injectable(as: IRemoteRepository)
class HarryRemoteRepository implements IRemoteRepository {
  final HarryApiService apiService;
  HarryRemoteRepository(this.apiService);
}
```

---

## 7. `runCatching` and `Result<T>`

### `Result<T>`

A Kotlin built-in wrapper with two states:

```kotlin
Result.success(book)       // call succeeded, holds the Book value
Result.failure(exception)  // call failed, holds the Throwable
```

**Flutter analogy** — `Either<Failure, Book>` from `dartz`, or a custom sealed class:
```dart
sealed class Result<T> {}
class Success<T> extends Result<T> { final T data; }
class Failure<T> extends Result<T> { final Exception error; }
```

---

### `runCatching { }`

A try-catch block that automatically wraps the outcome into `Result`. The last expression in the block becomes the success value.

```kotlin
// What runCatching expands to:
override suspend fun getBook(): Result<Book> {
    return try {
        Result.success(apiService.getBook().toDomain())
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

// runCatching shorthand:
override suspend fun getBook(): Result<Book> = runCatching {
    apiService.getBook().toDomain()
}
```

**Flutter equivalent:**
```dart
Future<Result<Book>> getBook() async {
  try {
    final book = await apiService.getBook().toDomain();
    return Success(book);
  } catch (e) {
    return Failure(e);
  }
}
```

---

### Why return `Result` instead of throwing

Without `Result`, the ViewModel must catch raw exceptions:
```kotlin
// ViewModel — messy
try {
    val book = repository.getBook()
} catch (e: IOException) { ... }
  catch (e: HttpException) { ... }
```

With `Result`, the repository owns error handling and the ViewModel gets two clean states:
```kotlin
// ViewModel — clean
repository.getBook()
    .onSuccess { book -> /* update UI */ }
    .onFailure { error -> /* show error */ }
```

**Flutter analogy** — same reason you return `Either<Failure, Book>` from a repository in clean architecture instead of letting exceptions propagate to the UI layer.

---

### Full `Result` reference

| | Flutter (`dartz`) | Kotlin |
|---|---|---|
| Wrapper type | `Either<Failure, T>` | `Result<T>` |
| Auto try-catch | manual try/catch | `runCatching { }` |
| Success | `Right(value)` | `Result.success(value)` |
| Failure | `Left(error)` | `Result.failure(exception)` |
| Handle in UI | `fold(onLeft, onRight)` | `.onSuccess { }.onFailure { }` |

---

## 8. Dependency graph — NetworkModule

```
OkHttpClient
     │
     ▼
  Retrofit
     │
     ▼
HarryApiService
     │
     ▼
HarryRemoteRepository  ──(@Binds)──▶  IRemoteRepository
                                              │
                                              ▼
                                         ViewModel
```

Each arrow = Hilt injects the dependency automatically. You never call `new` or `getIt<>()` manually anywhere in the chain.
