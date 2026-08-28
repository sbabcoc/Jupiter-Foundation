# INTRODUCTION

**Jupiter Foundation** is a lightweight collection of JUnit 5 (Jupiter) extensions, interfaces, and static
utility classes that supplement and augment the functionality provided by the Jupiter API. The facilities
provided by **Jupiter Foundation** include automatic retry of failed tests, test artifact capture, and
resolved-argument capture for `@TestTemplate` methods (e.g. `@ParameterizedTest`, `@RepeatedTest`).

Unlike its sibling projects — [JUnit-Foundation](https://github.com/sbabcoc/JUnit-Foundation) (built on
Byte Buddy instrumentation, since JUnit 4 has no extension model of its own) and
[TestNG-Foundation](https://github.com/sbabcoc/TestNG-Foundation) (built on TestNG's `ITestNGListener`
hierarchy) — **Jupiter Foundation** needs neither bytecode instrumentation nor a custom listener-chaining
mechanism. Jupiter's own `InvocationInterceptor`/`TestWatcher`/`ExecutionCondition` extension points
already provide first-class hooks for everything those two projects had to build by hand. What
**Jupiter Foundation** actually adds is the one place Jupiter's own model falls short: **retry**, where
the `Invocation.proceed()`/`skip()` contract (callable exactly once per interceptor invocation) rules out
simply looping the normal execution path.

**Status**: early-stage, under active development. Not yet published to Maven Central. Current version is
`1.0.0-SNAPSHOT`; the first stable release will be **1.0.0**.

## Requirements

**Jupiter Foundation** targets **Java 8**, but ships two build profiles rather than one, because its two
supported JUnit lines have different Java baselines of their own:

| Profile   | JUnit dependency        | Java baseline |
|-----------|--------------------------|---------------|
| `java8`   | JUnit Jupiter **5.14.x** (maintained LTS line) | Java 8  |
| `java17`  | JUnit **6.x** (unified Platform/Jupiter/Vintage versioning) | Java 17 |

JUnit 6.x requires Java 17 at minimum; 5.14.x is the still-maintained line for consumers on Java 8 or 11.
Both profiles compile the same shared source (`src/main/java`) against their respective dependency
version and Java toolchain — see `build.gradle`/`java8Deps.gradle`/`java17Deps.gradle` for the mechanism,
which mirrors **TestNG-Foundation**'s own `java8`/`java11` profile split.

```
./gradlew build                    # uses the default profile (java8)
./gradlew build -Pprofile=java17   # explicit java17 profile
```

## Dependency Coordinates

```groovy
implementation 'com.nordstrom.tools:jupiter-foundation:1.0.0-SNAPSHOT'
```

*(Will become `1.0.0` at first stable release.)*

## Automatic Retry of Failed Tests

[`RetryExtension`](src/main/java/com/nordstrom/automation/jupiter/RetryExtension.java) provides automatic
retry of failed `@Test` and `@TestTemplate` methods. Register it directly, or extend it to add
framework-specific behavior around each attempt (see **Using RetryExtension in another framework** below):

```java
@ExtendWith(RetryExtension.class)
public class MyTests {

    @Test
    public void flakyTest() {
        // retried automatically on failure, per the configured MAX_RETRY count
    }
}
```

**Retry is opt-in, not automatic-by-default.** Even with `MAX_RETRY > 0` configured, a failure is only
retried if at least one registered [`JupiterRetryAnalyzer`](src/main/java/com/nordstrom/automation/jupiter/JupiterRetryAnalyzer.java)
approves it — matching **TestNG Foundation**'s `TestNGRetryAnalyzer`/**JUnit Foundation**'s
`JUnitRetryAnalyzer` behavior exactly. Register an analyzer via
`META-INF/services/com.nordstrom.automation.jupiter.JupiterRetryAnalyzer`:

```java
public class AnyExceptionRetryAnalyzer implements JupiterRetryAnalyzer {
    @Override
    public boolean retry(Method method, Throwable thrown) {
        return true; // approve retry for any failure
    }
}
```

Multiple analyzers may be registered simultaneously; a retry is approved if **any** of them return `true`.

### Configuration

Retry behavior is controlled by [`JupiterConfig`](src/main/java/com/nordstrom/automation/jupiter/JupiterConfig.java),
overridable via system property or a `jupiter.properties` file on the classpath:

| Setting                          | Property key                        | Default |
|-----------------------------------|--------------------------------------|---------|
| Maximum retry attempts            | `jupiter.max.retry`                  | `0` (disabled) |
| Include exception detail in retry log | `jupiter.retry.more.info`        | `false` |

**NOTE**: If neither `jupiter.properties` nor the expected settings are found, `JupiterConfig` logs a
`DEBUG`-level message identifying exactly where it looked — this is deliberate, not a bug to suppress: it's
the only diagnostic a consumer gets when their own configuration file has a typo or landed in the wrong
place.

### Declining automatic retry

Mark a method or class `@NoRetry` to exclude it regardless of the configured `MAX_RETRY`:

```java
@Test
@NoRetry
public void testLongRunning() {
    // never retried
}
```

### Redacting sensitive values in retry log messages

Parameters of `@ParameterizedTest`/`@RepeatedTest` methods appear in retry log output by default. Mark a
parameter `@RedactValue` to replace its value with a placeholder instead:

```java
@ParameterizedTest
@CsvSource({"john.doe, secret123"})
public void testLogin(String username, @RedactValue String password) {
    // retry log shows: ...testLogin(john.doe, |:arg1:|)
}
```

### Controlling retry log verbosity

Each retry logs a `WARN`-level one-line message by default (`### RETRY ### ClassName.methodName(...)`).
The full exception (including stack trace) is additionally included whenever `RetryExtension`'s own
logger is at `DEBUG` or `jupiter.retry.more.info=true`. To keep other loggers at `DEBUG` while quieting
just the retry stack traces, target `RetryExtension`'s logger specifically in your Logback config:

```xml
<logger name="com.nordstrom.automation.jupiter.RetryExtension" level="info" />
```

### Using RetryExtension in another framework

`RetryExtension` is designed to be extended: override `beforeAttempt(Object, Method)`/
`afterAttempt(Object, Method, Throwable)` to run framework-specific setup/teardown around every retry
attempt beyond the first. This matters because retries beyond the first bypass Jupiter's normal
`InvocationInterceptor` chain entirely (a consequence of `Invocation.proceed()`'s exactly-once contract —
see `RetryExtension`'s class javadoc for the full explanation), so any *other* registered interceptor
that would normally wrap the test method (e.g. a driver-lifecycle watcher) needs to be re-invoked
manually for those attempts:

```java
public class MyFrameworkRetryExtension extends RetryExtension {
    @Override
    protected void beforeAttempt(Object instance, Method method) {
        MyFrameworkManager.beforeInvocation(instance, method);
    }

    @Override
    protected void afterAttempt(Object instance, Method method, Throwable thrown) {
        MyFrameworkManager.afterInvocation(instance, method);
    }
}
```

**NOTE**: `getMaxRetry(...)` is deliberately *not* something a consuming framework should override — the
retry count is a **Jupiter Foundation** setting, not a framework-specific one, the same way
`TestNGSettings.MAX_RETRY` belongs to **TestNG Foundation** and not to any framework built on top of it.

## Artifact Capture

[`ArtifactCollector`](src/main/java/com/nordstrom/automation/jupiter/ArtifactCollector.java) captures a
provider-defined artifact (screenshot, page source, log excerpt, etc.) whenever a test fails, and can also
be invoked on demand independent of failure. Implement [`ArtifactType`](src/main/java/com/nordstrom/automation/jupiter/ArtifactType.java)
to define what gets captured:

```java
public class MyArtifact extends ArtifactType {
    @Override
    public boolean canGetArtifact(Object instance) { /* ... */ }

    @Override
    public byte[] getArtifact(Object instance, Throwable reason) { /* ... */ }

    @Override
    public String getArtifactExtension() { return "txt"; }
}
```

```java
public class MyTests {
    @RegisterExtension
    final ArtifactCollector<MyArtifact> collector = new ArtifactCollector<>(new MyArtifact());

    @Test
    public void testSomething() {
        // captured automatically on failure

        collector.captureArtifact(context, null); // or on demand, given an ExtensionContext
    }
}
```

Captured artifacts land under `<project base dir>/target/artifact-capture/` by default (via
[`java-utils`](https://github.com/sbabcoc/java-utils)'s `PathUtils.ReportsDirectory`), unless the
`ArtifactType` implementation overrides `getArtifactPath(Object)` to specify a different subfolder.

## Captured Invocation Arguments

[`ArgumentsCaptor`](src/main/java/com/nordstrom/automation/jupiter/ArgumentsCaptor.java) captures the
resolved argument values of `@TestTemplate` invocations (`@ParameterizedTest`, `@RepeatedTest`, etc.),
making them available to any other extension that needs to distinguish between invocations of the same
method — e.g. `ArtifactCollector` uses it to compute a per-invocation hash suffix for captured artifact
filenames.

> **⚠️ Registration requires two separate pieces, and it fails silently — not loudly — if either is
> missing:**
>
> 1. `META-INF/services/org.junit.jupiter.api.extension.Extension` must list
>    `com.nordstrom.automation.jupiter.ArgumentsCaptor`.
> 2. The consuming project must **also** set
>    `junit.jupiter.extensions.autodetection.enabled=true` — in its own `junit-platform.properties`, or
>    as a system property. **Automatic extension detection is OFF by default in JUnit Platform.** The
>    service-loader entry alone does nothing without it.
>
> Forget either piece and `ArgumentsCaptor` simply never runs — no error, no log message.
> `getArguments(...)` just returns an empty list, exactly as it correctly does for a plain `@Test` with
> no parameters. Anything consuming that empty list where real arguments were expected (e.g.
> `ArtifactCollector` naming a file, or a retry mechanism re-invoking a parameterized method) then fails
> downstream with an error that gives no hint the actual cause is a missing configuration property two
> files away. **This is not a hypothetical risk — it's exactly the bug that motivated this warning.**
>
> `ArtifactCollector` includes a best-effort runtime diagnostic for this specific failure mode
> (`warnIfArgumentsCaptorLikelyInactive()`): if a parameterized test produces no captured arguments, it
> checks both registration requirements above and logs a `WARN` identifying which one is actually
> missing. It can only fire from a component that's guaranteed to run regardless of `ArgumentsCaptor`'s
> own state — `ArgumentsCaptor` can't report its own failure to activate, since failing to activate means
> none of its code runs at all.

`RetryExtension` does **not** depend on `ArgumentsCaptor` — as an `InvocationInterceptor` itself, it reads
resolved arguments directly from its own `ReflectiveInvocationContext`, sidestepping this registration
requirement entirely for retry's own purposes. The requirement above applies specifically to
`ArtifactCollector`'s parameter-hash artifact naming (and any other extension that isn't itself wrapping
the invocation and therefore needs `ArgumentsCaptor`'s `Store`-based hand-off instead).

## Building and Testing

```
./gradlew build          # java8 profile
./gradlew build -Pprofile=java17
```

Tests use [JUnit Platform Test Kit](https://junit.org/junit5/docs/current/user-guide/#testkit) to run
small fixture classes under `com.nordstrom.automation.jupiter.fixtures` through a real Jupiter engine and
assert on the outcome — the same approach **JUnit Foundation**'s own `AutomaticRetryTest` uses via
`JUnitCore`, adapted to Jupiter's own test-execution API.
