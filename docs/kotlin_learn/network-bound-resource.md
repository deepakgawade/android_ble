# NetworkBoundResource using Flow

`NetworkBoundResource` is **not a built-in Kotlin class** — it's a well-known architecture pattern (popularized by Google's Android Architecture Guide) for handling the **single source of truth** problem: show cached data immediately, fetch fresh data from network, update the cache, and re-emit.

---

## The problem it solves

Without it, you have to manually orchestrate three things every time:

```
1. Show cached data from local DB immediately (fast, offline-safe)
2. Fetch fresh data from network in background
3. Save new data to DB → DB emits update → UI updates automatically
```

**Flutter analogy:** Like a repository that first yields from a local SQLite/Hive cache, then fires a network call, saves to local, and the stream from local automatically re-emits the new data.

---

## The pattern using `flow { }`

```kotlin
fun <LocalType, RemoteType> networkBoundResource(
    query: () -> Flow<LocalType>,                          // read from local DB
    fetch: suspend () -> RemoteType,                       // call network API
    saveFetchResult: suspend (RemoteType) -> Unit,         // save to local DB
    shouldFetch: (LocalType) -> Boolean = { true }         // decide if fetch needed
) = flow {
    val localData = query().first()             // get current cached value

    if (shouldFetch(localData)) {
        emit(Resource.Loading(localData))       // show cached data + loading spinner

        try {
            val remoteData = fetch()            // hit the network
            saveFetchResult(remoteData)         // write into local DB
            query().collect {                   // DB emits updated data
                emit(Resource.Success(it))
            }
        } catch (e: Exception) {
            query().collect {                   // on network failure, still show cache
                emit(Resource.Error(e, it))
            }
        }
    } else {
        query().collect { emit(Resource.Success(it)) }
    }
}
```

### The `Resource` wrapper it emits

```kotlin
sealed class Resource<T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error<T>(val exception: Throwable, val data: T? = null) : Resource<T>()
    data class Loading<T>(val data: T? = null) : Resource<T>()
}
```

---

## Timeline of emissions

```
Time ──────────────────────────────────────────────────────────►

[0ms]   Loading(cachedBook)    ← emit stale cache immediately
[300ms] ── network call in flight ──
[800ms] saveFetchResult()      ← write fresh data to Room DB
[801ms] Success(freshBook)     ← Room Flow auto-emits the update
```

UI always has something to show. No blank screen while waiting.

---

## How it would fit into this project

The Harry Potter book feature currently calls the API directly with no local cache (`HarryRemoteRepository.kt:11`):

```kotlin
// Current — no cache
override suspend fun getBook(): Result<Book> = runCatching {
    apiService.getBook().toDomain()
}
```

With `NetworkBoundResource` it would look like:

```kotlin
// With NetworkBoundResource
fun getBook(): Flow<Resource<Book>> = networkBoundResource(
    query          = { bookDao.getBook() },                  // Room Flow — local DB
    fetch          = { apiService.getBook().toDomain() },    // network call
    saveFetchResult = { book -> bookDao.insert(book) },      // save to Room
    shouldFetch    = { cached -> cached == null }            // fetch only if no cache
)
```

And `BookViewModel` would collect it:

```kotlin
repo.getBook().collect { resource ->
    when (resource) {
        is Resource.Loading -> _state.update { it.copy(loading = true, book = resource.data) }
        is Resource.Success -> _state.update { it.copy(loading = false, book = resource.data) }
        is Resource.Error   -> _state.update { it.copy(loading = false, error = resource.exception.message) }
    }
}
```

---

## Why it's not in this project yet

The Harry Potter book feature only does a **single remote fetch** with no offline requirement, so `runCatching` + `Result<T>` is enough. `NetworkBoundResource` becomes valuable when:

- App must work **offline**
- Data changes on server and UI must **auto-refresh**
- You want to avoid redundant network calls (e.g. `shouldFetch = { it.isStale() }`)

---

## Summary

| | Direct API call (current) | NetworkBoundResource |
|---|---|---|
| Shows cached data | No | Yes, immediately |
| Works offline | No | Yes (serves cache) |
| Auto-refreshes UI | No | Yes (Room Flow) |
| Complexity | Low | Medium |
| When to use | Simple one-shot fetch | Anything needing offline + freshness |

The core idea: **Room is the single source of truth. Network writes to Room. Room emits to UI.** The UI never reads from the network directly.
