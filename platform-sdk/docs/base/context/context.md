[⇧ Platform Base](../base.md)

## Context

The Context API (`com.swirlds.base.context.Context`) provides a way to store metadata in key-values stores. The Context
API is not defined to be used as a middleware between module and pass data around. It is designed to provide metadata to
base services like logging or metrics. The public API does not provide a way to access metadata of a context. Metadata
can only be added. Meaningful metadata can be the ip address, the node id or platform id.

## Different context types

The context API currently provides 2 different types of contexts:

- A **global context** which is shared across all threads.
- A **thread local context** which is only accessible by the thread that created it.

Both contexts can be accessed via the `com.swirlds.base.context.Context` interface.

Example:

[@formatter:off]: # (disable the code formatter for this section)

```java

// Get the global context
Context globalContext = Context.getGlobalContext();
globalContext.add("key","value");

// Get the thread local context
Context threadLocalContext = Context.getThreadLocalContext();
threadLocalContext.add("key","value");
```

[@formatter:on]: # (enable the code formatter for this section)

### AutoCloseable support

The `com.swirlds.base.context.Context` interface provides for each 'add()' method a corresponding '
addWithRemovalOnClose()' method. The 'addWithRemovalOnClose()' method returns an `AutoCloseable` which removes the value
for the given key when the `close()` method is called. This can be used to add metadata to a context for a specific
call.

Example:

[@formatter:off]: # (disable the code formatter for this section)

```java
Context threadLocalContext = Context.getThreadLocalContext();
try (AutoCloseable closeable = threadLocalContext.addWithRemovalOnClose("key", "value")) {
    // In this code the context contains the key "key" with the value "value"
}
```

[@formatter:on]: # (enable the code formatter for this section)

### Creating a new context type

Modules can implement the `com.swirlds.base.context.Context` interface to create a context with a specific scope or
usecase. Currently the api does not provide any way to register a custom context.

## Using the context API in tests

The context API can be used in tests to add metadata to the context. If tests require a specific metadata to be present
the testfixtures of the context API can be used to add the metadata to the context. By using the testfixtures unit tests
can be executed isolated from other unit tests. By doing so any context can easily be changed in a test and will
reseted automatically before and after the test.

Example:

[@formatter:off]: # (disable the code formatter for this section)

```java
@WithContext
public class MyTest {

    @Test
    void testMethod() {
        // The global and thread local contexts are empty und will be cleared after the test
    }

}
```

[@formatter:on]: # (enable the code formatter for this section)
