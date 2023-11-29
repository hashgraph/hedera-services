# Wiring Framework

## Goals

The goal of this framework is to provide a structure in which logical units of work can be defined in relative isolation
and connected (or wired) together with other components to form a pipeline that also allows for the threading model to
be changed with little effort. It is very important for the threading model to be controlled outside of the business
logic so that it can be adjusted to achieve optimal performance.

## Overview

At the most basic level, a component is composed of task schedulers, business logic, and wires. Each component is a unit
of business logic that performs some action on a piece of data. The component receives the data on an input wire,
performs the action, and optionally produces data it can send to another component on an output wire. The components
are connected, or "soldered", together by joining input and output wires of the same data type to form a pipeline.

The concurrency of the components is determined by the task scheduler type. Each component has a task scheduler which
schedules the task of performing the business logic operation on the data received on the input wire. Task schedulers
are described in more detail below.

### Wires

Wires are the construct on which data flows between components. Each component can have zero or more input wires (
restrictions apply based on the concurrency of the task scheduler) and zero or more output wires. Each wire transports a
specific type of data. Wires of the same type can be "soldered" or connected to determine the flow of data between
components.

### Wire Transformers

Wire transformers are simple units of logic that transform the data type on a wire to a different type. For example,
a `WireListSplitter` takes data from a wire with a `List` type and streams the contents of the list, one by one, onto
another wire. Wire transformers operate on the thread defined in the task scheduler and do not come with their own
concurrency logic.

### Task Schedulers

Task schedulers are responsible for several things, but the most important job is taking data received on the input
wire, combining it with some business logic to be performed on that data to create a task, and scheduling that task to
be executed by a thread. Other responsibilities include applying back pressure and keeping metrics on tasks.

The type of task schedule determines the concurrency of the tasks. See `TaskSchedulerType.java` for descriptions of each
task scheduler type and their restrictions.

### Backpressure

Backpressure can be configured for some task schedulers. If available and configured, threads are blocked from
adding data to a component that has reached the limit of unprocessed tasks. This feature prevents too many tasks from
building up in a given component and signals the components feeding into it to back off so that it can catch up. In
order to prevent circular data flows that could result in a deadlock of backpressure, task schedulers provide a way to
bypass the backpressure mechanism. The method `inject()` ignores the number of unprocessed tasks and the maximum
configured value in order to prevent such deadlocks. The primary path of data flow should always pass data using the
backpressure methods. Only secondary data flows should use `inject()`. Circular data flows that use backpressure are
identified by the wiring model, which logs a warning if cyclic backpressure is detected.

### Wiring Model

The wiring model tracks all task schedulers and wires that connect them, and is capable of doing useful analysis of the
resulting graph. Most notably, it can produce mermaid style diagrams that show the flow of data through wires and
components, detect cyclic backpressure that could result in a deadlock, and verify proper wiring of various task
schedulers according to their restrictions (for example, a concurrent task scheduler is not permitted to be wired to a
direct scheduler).

## Building a Pipeline

The steps below describe how simple components can be created and wired together. This is one example of the order of
steps, but some steps can be done in different orders.

### Step 1 - Create the Task Scheduler

Create the `TaskScheduler` parameterized with the type of data the component will produce. The `TaskScheduler` comes
with an `OutputWire` build in.

```TaskScheduler<String> fooTaskScheduler = WiringModel.schedulerBuilder("Foo");```

### Step 2 - Create the Input Wire