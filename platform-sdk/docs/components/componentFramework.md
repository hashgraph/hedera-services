# Component Framework

## Goals

The goal of this framework is to provide a structure for defining software components in which:

1. Components can be defined in isolation, each with their own unit of business logic.
2. Components can be connected (or wired) together with other components to form a data pipeline.
3. Each component allows its threading model to be changed individually without modifying the business
   logic.

These goals allow software to be well organized, flexible, and easily tuned for performance.

## Overview

At the most basic level, a component in this framework is composed of a task scheduler, business logic, and wires. Each
component has a unit of business logic that performs some action on a piece of data. The component receives the data on
an input wire, performs the action, and optionally produces data on an output wire. The components are connected, or
"soldered", together by joining input and output wires of the same data type to form a data pipeline.

The concurrency of the component is determined by the task scheduler type. Each component has a task scheduler which
combines the data received on the input wire with the business logic operation to form a task. This task is then
scheduled for execution according to the task scheduler type and implementation. Task schedulers are described in more
detail [below](#TaskSchedulers).

Here is a picture of a simple component with one input wire of type `Integer`, and one output wire of type `String`:

![SimpleComponent](step4.drawio.svg)

### Business Logic

Business logic is defined in one or more classes that work together and accept data, perform an
operation, and produce optional output. This framework allows business logic to be defined completely independently
of the threading model and other business logic. Each unit of business logic does not know where its input comes from
or who consumes its output. The threading is handled by its task scheduler, and the connection of inputs and outputs is
handled externally. Changes to concurrency and wiring have no impact on the business logic.

### Wires

Wires are the construct on which data flows between components. Each component can have one or more input wires
(restrictions apply based on the concurrency of the task scheduler) and one or more output wires. Components that do not
produce any data have an output of type `Void`. Wires are strongly typed to the data they transport. Wires of the
same type can be "soldered" or connected to determine the flow of data between components.

### Task Schedulers

Task schedulers are responsible for several things, but the most important job is taking data received on the input
wire, combining it with the business logic operation to be performed on that data to create a task, and scheduling that
task to be executed by a thread. Other responsibilities include applying back pressure (if configured) and keeping
metrics on tasks.

Data coming into the task scheduler goes through a few phases:

1. Unscheduled - the data has been received by the task scheduler and a task has been created, but it has not yet been
   scheduled for execution.
2. Scheduled or Unprocessed - the task has been scheduled for execution and may have started execution, but has not
   yet completed execution.
3. Processed - the task has been executed (the method bound to the task has been called and the executing thread has
   returned).

If a component is wired to another component such that data is passed from one to the next, part of the task execution
is scheduling the next component's task. Each data item received on an input wire created one task, and that task is
executed exactly once.

The details of how a task is scheduled changes with the task scheduler type. Some examples of such scheduling include
adding the task to a concurrent queue which a dedicated thread pulls from, or submitting the task to the `ForkJoinPool`.
The Direct task scheduler does not really "schedule" at all; rather, it executes the task on the calling thread
immediately. The type of task scheduler determines the concurrency of the tasks. See `TaskSchedulerType.java` for a
complete list of scheduler types, descriptions, and limitations. Below is a diagram that illustrates the threads of
execution across components with some of the scheduler types.

![Scheduler Type Flow](schedulerTypes.drawio.svg)

Component 1 and 2 are Sequential Thread components, meaning their tasks are executed on a dedicated thread in the order
they are received. Thread A sends data to Component 1. The creation and scheduling of the task is performed on Thread A,
while the execution of the task is performed on Thread B, the component's dedicated thread. The data resulting from the
execution of this task in Component 1 is combined with the business logic of Component 2 - creating a task - which is
scheduled for Component 2 by Thread B. Component 2 executes its task and sends the resulting data to both Component 3
and 5 on Thread C, creating and scheduling their respective tasks.

Components 3 and 4 are Direct task schedulers and execute their tasks on the scheduling thread. Their tasks are both
executed on Thread C.

Component 5 is a Concurrent task scheduler which uses the `ForkJoinPool` to execute tasks. Each task is executed by some
FJP thread. The thread that executes a Component 5 task sends the resulting data to Component 6 and creates and
schedules the Component 6 task. This task may be executed on the same FJP thread or a different one.

Component 7 is another Sequential Thread task scheduler, which supports multiple threads scheduling tasks
simultaneously. Component 7's tasks are executed by a dedicated thread, and the resulting data is used to create and
execute Component 8 tasks since Component 8 is a Direct task scheduler that executes tasks immediately on the scheduling
thread.

## Building a Pipeline

The steps below describe how simple components can be created and wired together. This is one example of the order of
steps.

### Step 1 - Create the Task Scheduler

Create the `TaskScheduler` parameterized with the type of data the component will produce. The `TaskScheduler` comes
with an `OutputWire` built in.

```
TaskScheduler<String> fooTaskScheduler = WiringModel.schedulerBuilder("Foo");
```

![Step 1](step1.drawio.svg)

### Step 2 - Create the Input Wire

Create the InputWire for the TaskScheduler with the type of data the component will accept.

```
InputWire<Integer> fooInputWire = fooTaskScheduler.buildInputWire("Foo Input");
```

![Step 2](step2.drawio.svg)

### Step 3 - Create the Business Logic

Create the business logic required to operate on the input data and produce the output data. At this
point, the component is completely disconnected from the TaskScheduler.

```
BusinessLogic logic = new BusinessLogic();
```

![Step 3](step3.drawio.svg)

### Step 4 - Bind the Business Logic

Connect the business logic to the TaskScheduler by binding the InputWire to the method that operates on the input data
and produces the output data. The TaskScheduler is now connected, and will automatically forward the data produced by
the bound method to the OutputWire. The component is now built.

```
fooInputWire.bind(logic::intToString);
```

![Step 4](step4.drawio.svg)

### Step 5 - Connecting Components

Connect the component to other components by soldering input and output wires.

```
fooTaskScheduler.getOutputWire().solderTo(barInputWire);
barTaskScheduler.getOutputWire().solderTo(bazInputWire);
```

![Step 5](step5.drawio.svg)

## Features

### Wiring Model

The wiring model tracks all task schedulers and wires that connect them and is capable of doing useful analysis of the
resulting graph. Most notably, it can produce mermaid style diagrams that show the flow of data through wires and
components, detect cyclic backpressure that could result in a deadlock, and verify proper wiring of various task
schedulers according to their restrictions (for example, a concurrent task scheduler is not permitted to be wired to a
direct scheduler).

### Wire Add Ons

Wire add ons (or transformers) are simple units of logic that transform the data type on a wire to a different type. For
example, a `WireListSplitter` takes data from a wire with a `List` type and streams the contents of the list, one by
one, onto another wire. Wire transformers operate on the thread defined in the task scheduler and do not come with their
own concurrency logic. Some types of transformers are described below:

**Wire Filter:** A predicate that operates on the data type of the output wire. Data is only passed to the filter output
wire if the predicate passes.

**Wire Transformer:** A function that operates on the data type of the output wire. Data produced by the wire
transformer and forwarded on its output wire may be a different type than the input.

**Wire List Splitter:** A function that operates on a list of data and streams the individual items on the output wire.

### Backpressure

Backpressure can be configured for some task schedulers. If available and configured, threads are blocked from
adding data to a component that has reached the limit of unprocessed tasks. This feature prevents too many tasks from
building up in a given component and signals the components feeding into it to back off so that it can catch up. In
order to prevent circular data flows that could result in a deadlock of backpressure, task schedulers provide a way to
bypass the backpressure mechanism. The method `inject()` ignores the number of unprocessed tasks and the maximum
configured value in order to prevent such deadlocks. **If backpressure is desired, only a single non-cyclic data flow
should use backpressure. Any data flow that forms a cycle should use `inject()` to avoid deadlock.** Circular
data flows that use backpressure are identified by the wiring model, which logs a warning if cyclic backpressure is
detected.

### Secondary Output Wires

Each task scheduler comes with a built-in output wire, also referred to as the "primary output wire". The task
scheduler takes the data produced by each task execution and sends it on the primary output wire. Additional output
wires may be created, but they do not follow the same paradigm as primary output wires. These are referred to as
"secondary output wires". Secondary output wires must be invoked by the business logic directly. Task schedulers are
unaware of such secondary output wires. The business logic still does not know who consumes the data of these secondary
output wires, but must know that the secondary output wire exists in order to send data on it. Therefore, secondary
output wires must be provided to the business logic, ideally in the constructor. A component is the sole owner of its
output wires. Only the task scheduler of a component should be allowed to push data onto its primary output wire, and
only the business logic of a component should be allowed to push data onto its secondary output wires. It is a violation
of the framework principles to share ownership of a secondary output wire by allowing anything external to the component
to push data onto the output wire.

### Error Handling

Each task scheduler may be provided with an `UncaughtExceptionHandler`. If none is provided, a default handler is used
that logs the exception. Any `Throwable` error that gets thrown by the business logic is provided to the uncaught
exception handler as part of task execution. Exceptions do not cause the task scheduler to stop or crash unless the task
scheduler has a dedicated thread or is direct, and the uncaught exception handler kills the execution thread. In all
other cases, the task scheduler will continue with the next task.

## Comprehensive Component Diagram

The diagram below depicts a comprehensive component that utilizes a ForkJoinPool.

![Comphrehensive Component](comprehensiveComponent.drawio.svg)
