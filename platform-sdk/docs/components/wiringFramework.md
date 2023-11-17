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

The concurrency of the components is determined by the task scheduler. Each component has a task scheduler which
schedules the task of performing the business logic operation on the data received on the input wire. Task schedulers
are described in more detail below.

### Wires


### Wire Transformers


### Task Schedulers


### Backpressure


### Wiring Model


## Building a Pipeline