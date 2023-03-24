### Background

There are a number of critical threads in our pipeline whose performance must be monitored closely. Investigating
performance issues often requires a lot of time to narrow down the issue.

### Problems with previous approaches

- In many places we track the average time something takes. This number by itself is not that useful, without the
  knowledge how many times it has been executed on a particular thread.
- Metrics are often updated when the work is done. This means that if a thread is blocked, we have no insight into what
  is going on. This also means that some work that takes a long time within one sampling period might be reported in a
  subsequent sampling period, giving us misleading information.
- Some attempts have been made to unify this with cycle metrics. These are a collection of metrics updated through a
  single instance.
    - Since they all need to be updated through a single class, this would mean that all classes executing on a
      particular thread need a dependency to it.
    - Since they track cyclical work, they do not handle situations where a thread does different type of work

## Busy time metric

- All thread work is tracked by percentages (or fractions)
- All subtasks of a thread are tracked individually
- The metric tracks the current status of the thread, so that accurate information can sampled even if the thread is
  stuck
- it is lock-free and garbage-free. The metric implementation that stores all relevant information within an integer
  pair that can be updated atomically.
- This can track the overall busyness of a thread
- It can also track each part of the execution to get more detailed information about where a thread is spending its
  time

### Example diagram

![](busy-time.svg)