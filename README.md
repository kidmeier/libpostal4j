# Java FFI wrapper for libpostal

A wrapper for [libpostal](https://github.com/openvenues/libpostal) using Java 22+'s FFM API.

## Getting started

1. Download and install libpostal and associated data files
2. Add `libpostal4j` dependency to your project
4. Initialize the library:
   ```java
   Libpostal libpostal = Libpostal.initialize();                                               // Single-threaded execution
   Libpostal libpostal = Libpostal.initialize(new Options().withArenaFactory(Arena::ofShared)) // Multi-threaded execution
   ```
5. Use the library:
   ```java
   Libpostal.Language[] detectedLanguages = libpostal.classifyLanguage("100 Queen St W, Toronto, ON M5H 2N3");
   Libpostal.Parse parse = libpostal.parse("100 Queen St W, Toronto, ON M5H 2N3");
   String[] nearDupeKeys = libpostal.nearDupeHashes(parse, Stream.of(detectedLanguages)
					.map(Language::language)
					.toArray(String[]::new)));
   ```

## Thread-safety

The underlying C library ([libpostal](https://github.com/openvenues/libpostal)), is
not thread-safe. `libpostal4j` remains agnostic about the threading model by allowing
the caller to supply the [Arena](https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/lang/foreign/Arena.html)
used to communicate with the C library as well as an [Executor](https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/util/concurrent/Executor.html)
used to initialize the library.

### Single-threaded execution on a caller supplied thread

This is useful if you are running in a single-threaded environment, but you want to
offload the relatively expensive library initialization to a different thread:

```java
  Executor serialExecutor = Executors.newSingleThreadExecutor(); // ...or a framework-based executor, eg. vertx::runOnContext
  Libpostal libpostal = Libpostal.initialize(new Options()
    .withArenaFactory(Arena::ofConfined)
    .withInitExecutor(serialExecutor));
```

After initialization all requests must be made on the same thread used by `serialExecutor`
during initialization.

### Multi-threaded execution

If you are running in a multi-threaded environment where any thread is expected to be able to
call into `Libpostal` then the library must be initialized with a shared arena:

```java
  Libpostal libpostal = Libpostal.initialize(new Options()
    .withArena(Arena.ofShared()));
```

**NOTE**: `Libpostal` provides no locking of its own, it is up the caller to synchronize access
from multiple threads.