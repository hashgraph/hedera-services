# Metric labels

author: @hendrikEbbers  
status: draft

## Summary

This proposal describes a new feature for the metrics module.
The feature is called "metric labels" and allows to add labels to metrics.
Labels are key-value pairs that can be used to filter and group metrics.
Why this proposal uses the name "metric labels" (like prometheus does) other libraries (like micrometer) use the term tags.

## Motivation

Today the metrics module only supports the definition of metrics without adding custom labels.
While each metric has a  hardcoded category, name, and nodeId no other custom information can be added.
This proposal suggests adding a new concept called "metric labels" to the metrics module and
migration the `category`, `name`, and `nodeId` information to labels.
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

## Non-Goals

Even with the new concept the metrics api should be only used in our internal code.
The metrics module should not be used by the end user.
Here the preferred solution is still that we forward our metrics to a monitoring system that the custom application code uses.

## Design & Architecture

This chapter describes the changes that are needed to add labels to the metrics module.

### Public API

The metrics module will be extended by a new data type called `Label`.
A label is a key-value pair that can be defined as a `record`:

```java
record Label(@NonNull String key, @Nullable String value) {}
``` 

The `Metric` interface will be extended by a new method called `getLabels()` that returns a list of labels:

```java
interface Metric {
    
    //...
    
    @NonNull
    List<Label> getLabels();
}
```

> [!IMPORTANT]  
> I currently ask myself if the method should return `Set`, `Collection` or `List`. Help is appreciated.

Like the name or category, the labels are defined when a metric is created and cannot be changed later.
Based on that the MetricConfig class will be extended by a new method called `withLabels`:

```java
abstract class MetricConfig {
    
    //...
    
    @NonNull
    MetricConfig withLabels(@NonNull Label... labels) {...}
    
    @NonNull
    MetricConfig withLabels(@NonNull Collection<Label> labels) {...}
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

#### Snapshot API

The snapshot API will be extended to return the labels of a metric for a snapshot.
By doing so any monitoring system that uses the snapshot API can use the labels.
Today we only have 2 system that uses the snapshot API: the prometheus endpoint and the CSV exporter.
Since no class of the snapshot API is public today (based on the internal usage of `nodeId`) we plan to move parts
of the snapshot API to the public API.

### Migration

Next to extending the public API we need to migrate the current metrics to the new concept.

#### Migration of the `category`, `name`, and `nodeId`

The `category`, `name`, and `nodeId` information of a metric will be migrated to labels.
The key of a metric is the combination of the `category` and `name`.
That will not be changed but next to that both values will be added as labels to the metric.
By doing so a metric can be identified by its key and labels.

Let's assume a metric with the category `myCategory`, the name `myName`.
As today the key of the metric is `myCategory.myName`.
Next to that the metric will have 2 labels: `category=myCategory` and `name=myName`.
The unique identifier of the metric is the key and the labels.
That is a breaking change since today the key is the unique identifier of a metric.
Since we do not have api today to add labels the migration should be minimalistic.

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

## Open Questions

Please help me to answer the following questions before we can start with the implementation:

- Should the `getLabels()` method return `Set`, `Collection` or `List`?
- Should we add a new methods to the `Metrics` interface to get for example all metrics with a specific label?
- How will a label with a `null` value be handled?

## Alternatives

Since the idea of this proposal is to make the metrics module independent of the platform module we need to remove
the usage of the `NodeId` class or extract the class from the platform and define it as general API.
Since the class does not make any sense outside the consensus node (like in block node for example) this is not a good idea.
Another option would be to change the type of the `nodeId` to a `String` but that would end in an ugly API for any
system next to the platform that uses the metrics module since `null` or any other default must always be used as the `nodeId`.
Next to that all that ideas will end in having a hardcoded `nodeId` label in the metrics that will be forwarded to the monitoring system.

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
