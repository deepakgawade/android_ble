# Kotlin–Java Interoperability

Kotlin compiles to JVM bytecode, so Kotlin and Java can coexist in the same project and call each other freely.

---

## Calling Java from Kotlin

Generally seamless. Key behaviors:

**Nullability** — Java types become "platform types" (`String!`) — Kotlin trusts you on nullability. Annotate Java with `@Nullable`/`@NonNull` to get compile-time checks.

```kotlin
val list = ArrayList<String>()   // Java class, works directly
list.add("hello")
```

**Getters/setters** — Java bean properties are accessed as Kotlin properties:
```kotlin
// Java: person.getName() / person.setName("x")
person.name = "x"   // Kotlin syntax
```

**`void` → `Unit`** — Java `void` methods return `Unit` in Kotlin.

**Checked exceptions** — Kotlin ignores them; no `throws` clause needed.

---

## Calling Kotlin from Java

Requires a few annotations to smooth the edges:

| Annotation | Purpose |
|---|---|
| `@JvmStatic` | Makes companion object methods static in Java |
| `@JvmField` | Exposes a Kotlin property as a plain Java field |
| `@JvmOverloads` | Generates overloaded methods for default parameters |
| `@JvmName` | Renames the generated class/method for Java callers |

```kotlin
class Foo {
    companion object {
        @JvmStatic fun create(): Foo = Foo()   // Java: Foo.create()
    }

    @JvmOverloads fun greet(name: String = "World") { }
    // Java sees: greet() and greet(String)
}
```

**Top-level functions** — compile to a class named `<FileName>Kt` by default:
```java
// Kotlin file: Utils.kt → Java: UtilsKt.doSomething()
```
Use `@JvmName("Utils")` on the file to rename it.

---

## Common Gotchas

- **Kotlin `object`** — singleton, accessed as `Foo.INSTANCE` from Java unless `@JvmStatic` is used
- **Data class `copy()`** — not easily usable from Java (default params)
- **Extension functions** — appear as static methods in Java: `StringExtKt.myExt(str)`
- **Coroutines** — `suspend` functions become Java methods with a `Continuation` parameter; use `kotlinx-coroutines-jdk8` adapters for clean Java interop

---

In Android projects, Kotlin is the primary language and Java interop is mostly transparent — you'll mainly encounter it when using older Android/Java libraries.

---

## Examples

### 1. `@JvmStatic` — Call Kotlin companion from Java

**Kotlin (`MyFactory.kt`)**
```kotlin
class MyFactory {
    companion object {
        @JvmStatic
        fun create(): MyFactory = MyFactory()
    }
}
```

**Java**
```java
MyFactory f = MyFactory.create();  // works as static
// Without @JvmStatic: MyFactory.Companion.create()
```

---

### 2. `@JvmOverloads` — Default parameters in Java

**Kotlin**
```kotlin
class Greeter {
    @JvmOverloads
    fun greet(name: String = "World", greeting: String = "Hello") {
        println("$greeting, $name!")
    }
}
```

**Java**
```java
Greeter g = new Greeter();
g.greet();                  // Hello, World!
g.greet("Alice");           // Hello, Alice!
g.greet("Alice", "Hi");    // Hi, Alice!
```

---

### 3. `@JvmField` — Expose property as plain field

**Kotlin**
```kotlin
class Config {
    @JvmField
    val timeout = 30
}
```

**Java**
```java
Config c = new Config();
int t = c.timeout;  // direct field access, no getter needed
```

---

### 4. Extension function from Java

**Kotlin (`StringUtils.kt`)**
```kotlin
fun String.shout(): String = this.uppercase() + "!!!"
```

**Java**
```java
String result = StringUtilsKt.shout("hello");  // HELLO!!!
```

---

### 5. Kotlin `object` singleton from Java

**Kotlin**
```kotlin
object Logger {
    fun log(msg: String) = println(msg)
}
```

**Java**
```java
Logger.INSTANCE.log("hello");  // must use .INSTANCE
// Add @JvmStatic to log() to call Logger.log("hello") directly
```
