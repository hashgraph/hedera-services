# Metric labels

+author: @hendrikEbbers
+status: draft

## Summary

This proposal describes a new feature for the metrics module.
The feature is called "metric labels" and allows to add labels to metrics.
Labels are key-value pairs that can be used to filter and group metrics.
Why this proposal uses the name "metric labels" (like prometheus does) other libraries (like micrometer or open telemetry) use the term tags or attributes.

## Motivation

Today the metrics module only supports the definition of metrics without adding custom labels.
While each metric has a hardcoded category, name, and nodeId no other custom information can be added.
This proposal suggests adding a new concept called "metric labels" to the metrics module and
migrate the `category`, `name`, and `nodeId` information to labels.
While the category and name are defined as String values the nodeId is a class called `NodeId`.
By doing so the metrics module becomes more flexible and can be used 100% independent of the
platform module since it does not depend on the platform module's `nodeId` anymore.

## Goals

- Add a new concept called "metric labels" to the metrics module
- Migrate the `category`, `name`, and `nodeId` information to labels
- Make the metrics module independent of the platform module
- Provide a public API to define labels for metrics
- Add labels to the snapshot API
- The output of the snapshot API should contain the labels
- The output of the snapshot API must be compatible with the current output (no breaking changes)
- Create better and more dynamic dashboards in Grafana by using labels

## Non-Goals

Even with the new concept the metrics api should be only used in our (hashgraph) code.
The metrics module should not be used by the end user.
Here the preferred solution is still that we forward our metrics to a monitoring system that the custom application code uses.

## Design & Architecture

This chapter describes the changes that are needed to add labels to the metrics module.

### About labels

Before we start with the implementation we need to define what labels are.
Labels are key-value pairs that can be used to filter and group metrics.
Labels are used by monitoring systems like prometheus to create more dynamic dashboards.
For example, a metric that counts the number of requests can have a label called `method` that defines if the request was a `GET` or `POST` request.
By doing so the monitoring system can create a dashboard that shows the number of requests per method.

In prometheus a concrete metric is defined like this (see https://prometheus.io/docs/concepts/data_model/#notation):

```
api_http_requests_total{method="POST", handler="/messages"}
```

Here a metric is defined by a name (`api_http_requests_total`) and a set of labels (`method="POST"` and `handler="/messages"`).
In prometheus that defines the unique identifier of a metric.

When using labels to create dashboards in Grafana prometheus provides a rich query language to filter and group metrics: https://prometheus.io/docs/prometheus/latest/querying/examples/

We will use the same practices and definition as in prometheus regarding labels.

### Public API

The metrics module will be extended by a new data type called `Label`.
A label is a key-value pair that can be defined as a `record`:

```java
record Label(@NonNull String key, @Nullable String value) {}
``` 

> [!NOTE]  
The value of a label can be `null`.
For our prometheus endpoint a label with a `null` value will be handled as if the label would not exist.

The `Metric` interface will be extended by a new method called `getLabels()` that returns a list of labels:

```java
interface Metric {
    
    //...
    
    @NonNull
    Set<Label> getLabels();
}
```

Like the name or category, the labels are defined when a metric is created and cannot be changed later.
Based on that the MetricConfig class will be extended by a new method called `withLabels`:

```java
abstract class MetricConfig {
    
    //...
    
    @NonNull
    MetricConfig withLabels(@NonNull Label... labels) {...}
    
    @NonNull
    MetricConfig withLabels(@NonNull Set<Label> labels) {...}
}
```

By adding labels the unique identifier of a metric is the key and the labels.
Based on that we need to modify the `Metrics` interface to use the labels as the unique identifier of a metric:

```java

interface Metrics {
    
    // old methods:
    // Object getValue(@NonNull String category, @NonNull String name);
    // void remove(@NonNull String category, @NonNull String name);
    
    @Nullable
    Object getValue(@NonNull String category, @NonNull String name, @NonNull Set<Label> labels);
    
    void remove(@NonNull String category, @NonNull String name, @NonNull Set<Label> labels);
}
```

Since the old methods are only used in tests or demos today we can do that change easily.

> [!NOTE]  
Since most of the filter methods that the `Metrics` interface defines today are not used we will not introduce any additional filter methods in this proposal.
If we see that additional methods are needed we can add them later.

We will have labels that are global for a `Metrics` instance.
The best example is the `nodeId` label.
We do not want to add the `nodeId` label to the creation of each metric.
Instead of that we want to add the `nodeId` label to the `Metrics` instance.
That should happen when a `Metrics` instance is created.
The creation of a `Metrics` instance is not public api today.
With the implementation of this proposal a `MetricsFactory` interface should be introduced that allows to create a `Metrics` instance.
The `MetricsFactory` interface will be defined like this:

```java
interface MetricsFactory {
    
    @NonNull
    Metrics create(@NonNull Set<Label> labels);
}
```

The definition of the api should follow our rules regarding services as defined at https://github.com/hashgraph/hedera-services/blob/develop/platform-sdk/docs/base/service-architecture/service-architecture.md

##### Examples

Here are some examples how to define a metric with labels:

```java
final Label transactionTypeLabel = new Label("transactionType", "fileUpload");
final Counter.Config config = new Counter.Config("transactionsCategory", "transactionCount").withLabels(transactionTypeLabel);

final Metrics metrics = ...;
final Counter counter = metrics.getOrCreate(config);

counter.increment();
```

In prometheus the metric will be defined like this:

```
transactionsCategory.transactionCount{transactionType="fileUpload"}
```

#### Snapshot API

The snapshot API will be extended to return the labels of a metric for a snapshot.
By doing so any monitoring system that uses the snapshot API can use the labels.
Today we only have 2 system that uses the snapshot API: the prometheus endpoint and the CSV exporter.
Since no class of the snapshot API is public today (based on the internal usage of `nodeId`) we plan to move parts
of the snapshot API to the public API.

### Migration

Next to extending the public API we need to migrate the current metrics to the new concept.

#### Migration of the `category`, `name`, and `nodeId`

The `nodeId` information of a metric will be migrated to labels.
The key of a metric won't change and still be defined as the combination of the `category` and `name`.
The unique identifier of the metric is the combination key and the labels.
That is a breaking change since today the key is the unique identifier of a metric.
Since we do not have an api today to add labels the migration should be minimalistic.

#### Migration of the prometheus endpoint

The `nodeId` of a platform is already added as a label today when using the prometheus endpoint.
For that endpoint we just need to remove the hard coded `nodeId` label and add a general algorithm to add labels.

#### Migration of the CSV exporter

The CSV exporter use the `nodeId` today to create the file name.
Since the CSV exporter is only used by the platform module we can continue with that approach.

## Testing

The metrics module is tested by unit tests today. We need to add tests for the new concept of labels.
Next to that we need to add tests of the CSV exporter and the prometheus endpoint to ensure that the labels are added correctly.
Here we need to check that a platform metric (that has a `nodeId` label) is still working correctly and ends in the exact same output as today.

## Alternatives

Since the idea of this proposal is to make the metrics module independent of the platform module we need to remove
the usage of the `NodeId` class or extract the class from the platform and define it as general API.
Since the class does not make any sense outside the consensus node (like in block node for example) this is not a good idea.
Another option would be to change the type of the `nodeId` to a `String` but that would end in an ugly API for any
system next to the platform that uses the metrics module since `null` or any other default must always be used as the `nodeId`.
Next to that all that ideas will end in having a hardcoded `nodeId` label in the metrics that will be forwarded to the monitoring system.

### Definition of Label at measurement time

In the given concept the labels are defined when a metric is created.
This is how several other libraries (like micrometer) handle labels.
Another option would be to define the labels at the time when a metric is measured.
Both variants have their pros and cons.
Since this proposal introduces a new concept to the metrics module we should start with the simplest solution.
This does not mean that we cannot add the second option later by extending the public API.

Let's have a look at the pros and cons of both options to understand the differences:

The following sample creates a metric with labels at creation time:

```java
final Label transactionTypeLabel = new Label("transactionType", "fileUpload");
final Counter.Config config = new Counter.Config("transactionsCategory", "transactionCount").withLabels(transactionTypeLabel);
final Counter counter = metrics.getOrCreate(config);

counter.increment();
```

Let's see how that metric can be used in a filter that filter transactions and counts the number of transactions of type `fileUpload`:

```java
class TransactionFilter {
    
    private final static Counter.Config config = new Counter.Config("transactionsCategory", "transactionCount");

    private final Metrics metrics;
    
    TransactionFilter(Metrics metrics) {
        this.metrics = metrics;
    }

    void handle(Transaction transaction) {
        if(transaction.getType().equals("fileUploadTransaction")) {
            config = config.withLabels(new Label("transactionType", "fileUpload"));
        } else if(transaction.getType().equals("fileDeleteTransaction")) {
            config = config.withLabels(new Label("transactionType", "fileDelete"));
        }
        final Counter counter = metrics.getOrCreate(config);
        counter.increment();
    }
}
```

As you can see the value of the `transactionType` label depends on a runtime value.
Based on that the config of the metric must be created at runtime.
Today we often create metrics in constructors or static blocks.
This will not be possible if a label needs to be defined at measurement time.

The Open Telemetry library uses the second approach. 
Here the labels are defined at measurement time and the code would look like this:

```java
class TransactionFilter {
    
    private final static Counter.Config config = new Counter.Config("transactionsCategory", "transactionCount");

    private final Counter counter;
    
    TransactionFilter(Metrics metrics) {
        counter = metrics.getOrCreate(config);
    }

    void handle(Transaction transaction) {
        if(transaction.getType().equals("fileUploadTransaction")) {
            counter.increment(new Label("transactionType", "fileUpload"));
        } else if(transaction.getType().equals("fileDeleteTransaction")) {
            counter.increment(new Label("transactionType", "fileDelete"));
        }
    }
}
```

Since we have seen the 2 different approaches we can discuss the pros and cons of both.
While the second approach might be more flexible when labels are defined at measurement time it is not good useable when labels could already be defined at creation time.
Here you do not want to add the same label multiple times to a metric.
Based on that option 2 would need an additional api that allows you to add labels already at creation time.
This will make the API more complex and harder to understand.
Next to that different values for labels always end in a new metric and in a new time series in the monitoring system.
Based on that labels should not be overused and never used with unknown / dynamic values.
You can find some general best practices at https://prometheus.io/docs/practices/instrumentation/#things-to-watch-out-for

Based on that we should start with the first approach and add the second approach later if needed.
The first approach is simpler and easier to understand.
Next to that it will make it harder to create labels with a dynamic value.

Once the api and usage of labels is clear we can think about adding the second approach.
Here the api can be extended by a new method that allows to add labels at measurement time. 

## Open Questions

- Should we use this proposal to rename the `Metrics` interface to `MetricRegistry`?

## Future Work

As a next step we can think about labels next to `nodeId` that makes sense to have in our metrics.
For example, we can add a label called `type` that defines if a metric is a counter, gauge, or histogram.
Another option is to add labels for layers (like `base`, `consensus`, `services`, ...).
By doing so we can add more information to the metrics that can be used by the monitoring system.

## References

- https://prometheus.io/docs/practices/naming/#labels
- https://prometheus.io/docs/practices/instrumentation/#use-labels
- https://www.javadoc.io/doc/io.micrometer/micrometer-core/1.1.0/io/micrometer/core/instrument/Tag.html
- https://github.com/vert-x3/vertx-micrometer-metrics/blob/master/src/main/java/io/vertx/micrometer/Label.java
- https://geode.apache.org/docs/guide/115/tools_modules/micrometer/micrometer-meters.html
