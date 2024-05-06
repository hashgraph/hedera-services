[⇧ Platform Base](../base.md)

# Base executor

The platform base modules contain some functionalities that need background tasks.
An example of this the flushing of logging files.
Instead of flushing a log file after each log line we have a background task that flushes the log file periodically.
For all that tasks the base executor can be used.
The API of the base executor is, against the other base module APIs, not designed to be used outside of the base modules.
Based on that the package of the API is only exported to the base modules.

## Usage

The base executor service can be accessed via the `com.swirlds.base.internal.BaseExecutorFactory` class.
Here we use the pattern that is defined in the [base service architecture documentation](../service-architecture/service-architecture.md).
The factory is defined as a singleton and the api / implementation is separated.
The factory provides the functionality to get a `ScheduledExecutorService` that can be used to schedule tasks.
Next to that the factory provides some convenience methods to schedule tasks directly.
The following code shows how you can access the base executor service:

```java

BaseExecutorFactory baseExecutorFactory = BaseExecutorFactory.getInstance();
ScheduledExecutorService executorService = baseExecutorFactory.getScheduledExecutor();
executorService.scheduleAtFixedRate(() -> {
    // do something
}, 0, 1, TimeUnit.SECONDS);

```

The code above shows how you can get the `ScheduledExecutorService` from the factory and how you can schedule a task that runs every second.

## Implementation

The base executor service is only used by the base modules for background tasks.
All that tasks are non-critical and can be stopped at any time.
That means that the base executor service is not designed to be used for critical tasks.

The base executor service internally only uses 1 single `Thread` that is used to execute all tasks.
Based on that an endless running task can block the full executor service.
We might add functionality to kill endless running tasks in future.
The thread is defined as a daemon thread to avoid that the JVM is not stopped because of the running thread.
Next to that the thread has a low priority to avoid that the executor service blocks other tasks.

## Observation

The base executor service provides functionalities for observation.
That is used to add [metrics](../metrics/metrics.md) to the service.
By doing so we can monitor the executor service.
All metrics start with the prefix `base_executor`.