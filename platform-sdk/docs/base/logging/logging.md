## Hedera Logging API

### Introduction
Our custom logging facade is crafted to meet our unique requirements, ensuring compatibility with various software integrations. It acts as a bridge, redirecting events from our facade to SLF4J, providing flexibility for projects dependent on our modules.

### Architecture and Configuration
Our architecture does not dictate the format of logging output, allowing for a wide range of output options. This flexibility permits various configurations, from console outputs to integrations with systems like Graylog or Kibana.

### Features of Our Logging Facade
Our facade goes beyond mere message logging, encapsulating rich information within each logging event. Key features include:

1. **Support for Message Placeholders**
2. **Event Origin Tracking**
3. **Timestamp Inclusion**
4. **Thread Identification**
5. **Throwable Cause Handling**
6. **Support for Various Log Levels**
7. **Marker Support**
8. **Key-Value Based Metadata**
9. **Mapped Diagnostic Context (MDC) Support**

---

## Implementing the New Logging API

### Setting Up the Logger

To integrate the new logging API within your Java application, begin by establishing a logger instance in your class. Here’s how you can set it up:

1. **Import the Logger Class**:
   Start by importing the `Loggers` class from the `com.swirlds.logging.api` package.

   ```java
   import com.swirlds.logging.api.Loggers;
   ```

2. **Create a Logger Instance**:
   In your class, declare a `private static final` logger instance. Use the `Loggers.getLogger()` method, passing your class (`MyClass.class`) as a parameter.

   ```java
   public class MyClass {
       private static final Logger LOGGER = Loggers.getLogger(MyClass.class);
   }
   ```

   This statement creates a logger specifically for `MyClass`, facilitating targeted logging within this class.

### Logging Messages

Once you have set up the logger, you can proceed to log messages. The new API provides a straightforward and flexible way to log information. Here’s how to use it:

1. **Basic Logging**:
   For a simple log message, use the `info` method with a string argument.

   ```java
   LOGGER.info("Hello, world!");
   ```

2. **Logging with Placeholders**:
   The API supports placeholder syntax for dynamic message composition. Use curly braces `{}` as placeholders and pass the dynamic values as additional arguments.

   ```java
   LOGGER.info("Hello, {}!", "world");
   ```

   In this example, the `{}` placeholder in the string will be replaced by `"world"`, resulting in the log message `"Hello, world!"`.


---


## Custom Logging Configuration

The configuration syntax is designed for clarity and ease of use. It allows setting a global default logging level and specific levels for packages or classes, with an option to introduce filters for markers.
For practicality, the `logging.properties` file should be set to reload every second, ensuring up-to-date logging configurations at all times.

The format of the logging configuration is user-friendly and self-explanatory. Here's an example representation of the syntax for setting logging levels:

```properties
# Global default logging level
logging.level = INFO

# Specific logging levels for packages or classes
logging.level.com.swirlds.common.crypto = DEBUG
logging.level.com.swirlds.common.crypto.Signature = WARN
logging.level.com.hashgraph = WARN
```

This configuration demonstrates how to set a global default logging level (`INFO`). It also illustrates how to specify logging levels for packages and classes. For example, everything under `com.swirlds.common.crypto` is set to `DEBUG`, except for `com.swirlds.common.crypto.Signature`, which is explicitly set to `WARN`. Similarly, all loggers within `com.hashgraph` default to `WARN`. This approach ensures that loggers inherit the most specific level defined in the configuration, providing both flexibility and precision in logging management.

To provide developers with more control and flexibility in logging, the configuration supports filters for markers. This feature allows developers to focus on log messages associated with specific markers, regardless of the logger's log level. Here's a sample configuration for marker filters:

```properties
# Marker filter configuration
logging.marker.CONFIG = ENABLED
logging.marker.CRYPTO = DISABLED
logging.marker.OTHER = DEFAULT
```

In this configuration, the `logging.marker.NAME` pattern is used, where `NAME` represents the marker's name, and the value can be set to `ENABLED`, `DISABLED`, or `DEFAULT`. For example, `logging.marker.CONFIG = ENABLED` would ensure that all log messages tagged with the `CONFIG` marker are displayed, irrespective of their log level.

A typical logging configuration file, incorporating these marker filters, might look like this:

```properties
# General logging level configuration
logging.level = INFO
logging.level.com.swirlds.common.crypto = DEBUG
logging.level.com.swirlds.common.crypto.Signature = WARN
logging.level.com.hashgraph = WARN

# Marker-specific logging configuration
logging.marker.CONFIG = ENABLED

# Additional handler configuration
logging.handler.NAME.level = WARN
```

To further refine our logging system, we have incorporated the concept of handlers. These handlers allow for more granular control over logging behavior. Each handler can be distinctly named and configured using the prefix `logging.handler.NAME`, where `NAME` is a unique identifier for the handler. This structure enables us to apply handler-specific settings. For instance, `logging.handler.NAME.level` can be used to set the logging level for a specific handler. All previously discussed features, such as marker filters and log level settings, are also applicable to these handlers.

A particularly useful feature of handlers is the `inheritLevels` property. This boolean property determines whether the handler should inherit the default level configurations set globally. By default, `inheritLevels` is set to `true`. However, it can be turned off to create a handler that focuses exclusively on a specific aspect of logging, such as a particular marker. For example, if you need a handler that only logs entries marked with the `CRYPTO` marker, your configuration would look like this:

```properties
# Handler specific for CRYPTO marker
logging.handler.CRYPTO_FILE.level = OFF
logging.handler.CRYPTO_FILE.marker.CRYPTO = ENABLED
```

In this configuration, the `CRYPTO_FILE` handler is set to ignore the global log level settings (`level = OFF`) but is specifically enabled to log messages tagged with the `CRYPTO` marker (`marker.CRYPTO = ENABLED`). This setup allows for the creation of dedicated log files or outputs for specific types of log messages, providing a focused view that can be particularly useful in complex systems or during specific types of analysis.

---

## Test Support with `WithLoggingMirror`

To improve testing, we've introduced the `WithLoggingMirror` annotation in JUnit 5. This annotation injects a `LoggingMirror` into test methods or classes, providing an isolated environment for logging event analysis. It ensures that tests with this annotation do not run in parallel, maintaining the integrity of each test's logging data.

A simple example of how to use this annotation is shown below:

```java
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.Loggers;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.test.fixtures.LoggingMirror;
import com.swirlds.logging.test.fixtures.WithLoggingMirror;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@WithLoggingMirror
public class LoggersTest {

    @Inject
    LoggingMirror loggingMirror;

    @Test
    void loggingMirrorTest() {
        // given
        final var clazz = LoggersTest.class;
        final var logger = Loggers.getLogger(clazz);

        // when
        logger.error("test");

        // then
        Assertions.assertEquals(1, loggingMirror.getEvents().size());
        final LogEvent event = loggingMirror.getEvents().get(0);
        Assertions.assertEquals(clazz.getName(), event.loggerName());
        Assertions.assertEquals(Thread.currentThread().getName(), event.threadName());
        Assertions.assertEquals(Level.ERROR, event.level());
    }
}
```