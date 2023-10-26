# Test Support

The base packages provide test support for using base components or basic Java features. The support is always defined 
as JUnit 5 extensions that follow some concrete pattern.

In general the base testfixtures provide an annotation that can be used to annotate a test or test class that needs
a specific test support feature. The name of the annotations should always start with `@With...`. By doing so, it is
easy to see which test support is used in a test class. A concrete example for such an annotation is 
`@WithSystemOut`. The annotation should be used in tests that somehow need to access the system out stream. While the 
details of functionallity of the `@WithSystemOut` annotation can be found in the corresponding chapter the following
points are a good sample to show how the general pattern works:

- A test class is annotated with `@WithSystemOut` will run in isolation. That means that no other test will be executed 
in parallel.
- A test class is annotated with `@WithSystemOut` will have access to the system out stream by additional support of the
`@WithSystemOut` annotation.

As you can see such annotation can be used to define a specific test support feature. Each of that annotations define a
JUnit extension that is added to the annotated test. In general the annotations / extensions should be compatible to
each other. That means that it should be possible to use multiple annotations on a test class.

Next to that some extensions provide additional functionalities. That is normally provides by an additional API. In most
cases an instance of a facade to that api is provided by the extension. The facade can be injected into the test class.
To do so we use the common `jakarta.inject.Inject` annotation. Whenever a test extension in the base layer provides such
an API it is documented in the corresponding javadoc / chapter. When using the `@WithSystemOut` annotation a 
`SystemOutProvider` can be injected in the test to access the system out stream. A sample of such test can look like 
that:

```java

@WithSystemOut
class MyTest {

    @Inject
    SystemOutProvider systemOutProvider;

    @Test
    void test() {
        System.out.println("Hello World");
        assertThat(systemOutProvider.getLines().toList()).contains("Hello World");
    }
}
```

## Concrete extensions

The following sections describe the concrete extensions that are provided by the base layer.

### System.out / System.error support

The `@WithSystemOut` annotation can be used to redirect the system out stream to a buffer. The buffer can be accessed
by the `SystemOutProvider` that is injected (`@Inject`) into the test class. The `SystemOutProvider` provides a `getLines()` method
that returns a `Stream<String>` that emits all lines that are written to the system out stream. All annotated tests are 
executed in isolation (see `org.junit.jupiter.api.parallel.Isolated`).

The `@WithSystemError` annotation can be used to redirect the system error stream to a buffer. The buffer can be accessed
by the `SystemErrProvider` that is injected (`@Inject`) into the test class. The `SystemErrProvider` provides a `getLines()` method
that returns a `Stream<String>` that emits all lines that are written to the system error stream. All annotated tests are 
executed in isolation (see `org.junit.jupiter.api.parallel.Isolated`).

### Context support

The `@WithContext`, `@WithGlobalContext`, `@WithThreadLocalContext` annotation are documented in the chapter of 
the [Context API](./../context/context.md).