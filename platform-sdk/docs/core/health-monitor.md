# Health Monitor

## Purpose and Context

The platform's previous backpressure mechanism had very bad performance characteristics.
The health monitor was created to replace the previous backpressure implementation with something more performant.

### Backpressure Requirements

- The backpressure mechanism must prevent an OOM in the event of any arbitrary component
  becoming arbitrarily slow.
- The backpressure mechanism should not slow down critical processes when engaged.
  Specifically, transaction handling should not be impeded when backpressure is engaged.
  This includes slowdowns due to the transaction handling thread being starved of work.
- The backpressure mechanism should not be overly aggressive. That is, we shouldn't see
  it engaging under normal circumstances on nodes that are not in a state of distress.
- The implementation of the backpressure mechanism should be sufficiently clean and
  maintainable. A performant solution that is too complex is not a solution we should
  accept as a solution.

### Design Decisions

Understanding of the health monitor can be split into two distinct sub-problems: the detection of a system in duress,
and the action that is taken to apply backpressure on a distressed system.

#### Detection

The platform is built from a number of "components". A component is usually a unit of logic that runs on its
own thread (or logical thread). Each component has a queue (or logical queue) of data it is ingesting. If a component
produces output, that output is enqueued within the queues of other components.

For each component with a queue, a preferred size is defined. The queue will not apply hard backpressure even if the
size of the queue exceeds the preferred size. That is, insertion into the queue will never block. However, a queue
whose size exceeds its preferred size will be considered to be "unhealthy".

In the background, a component called the "health monitor" periodically checks the health of each queue.
Checking the health of a queue is cheap (i.e. an atomic long read). The frequency of polling is configurable, with the
default being 10hz (`healthMonitorHeartbeatPeriod`).

The health monitor records the length of time that each queue has been in an unhealthy state. If a queue has been
unhealthy for one minute, that is a much stronger signal than if a queue has been unhealthy for a few hundred
milliseconds. If a previously unhealthy queue is observed as being healthy, the timer for that queue is immediately
reset to 0.

When the health monitor reports the total health of the system, it reports that value as a duration. Specifically,
it reports the amount of time that the most unhealthy queue has been unhealthy. If there is one queue that has been
unhealthy for 10s and another that has been unhealthy for 100s, it will report that the system unhealthiness factor
is 100s. If the second queue clears its backlog and becomes healthy, it will then report that the system unhealthiness
factor is 10s.

The health monitor publishes health reports over a wire. If it sends a duration of 0s, that means that the system is in
a healthy state. As the system becomes more and more unhealthy, it will periodically publish higher and higher
durations.

#### Reaction

Various subsystems react to the health monitor's reports in different ways.

##### Event Creation

If the health monitor reports an unhealthy duration exceeding `maximumPermissibleUnhealthyDuration`, then the creation
of self events is disabled. Once the system reports an unhealthy duration below `maximumPermissibleUnhealthyDuration`
seconds, event creation may resume.

##### Gossip

The sync gossip algorithm has the concept of a "sync permit". In order to initiate a sync operation, a permit must
be acquired. When the sync is completed, the permit is released. If there are X permits available,
then at any point in time there may be at most X sync operations in progress.

When the health monitor reports that the system is becoming unhealthy, the gossip system takes no action until the
unhealthy duration exceeds the `unhealthyGracePeriod`. This is designed to prevent the gossip
subsystem from reacting too aggressively to little blips.

When the system is unhealthy for a duration that exceeds the grace period, the gossip system will begin revoking
permits. Revoking a permit does not halt a sync operation that is currently in progress, but it can prevent a new
sync operation from starting. For each second that the system is unhealthy beyond the grace period, permits are revoked
at the rate specified by `permitsRevokedPerSecond`.

When the system eventually becomes healthy again, permits are gradually returned according to the rate specified by
`permitsReturnedPerSecond`.

There is a special edge case when permits are being returned. Suppose the system has been unhealthy for a while, and
then suddenly the backlog of work is cleared and the system becomes healthy. If all permits have been revoked, then
it may take several seconds for the first permit to be returned. This means that a healthy system may have zero permits
for a time after becoming healthy. To combat this problem, there is a configurable `minimumHealthyUnrevokedPermitCount`
value. When the system is healthy, this is the minimum number of un-revoked permits that will be available. Note that
this value is not respected in an unhealthy system, which should eventually have 0 permits if the problem is not
resolved.

##### Acceptance of application transactions

If the health monitor reports an unhealthy duration exceeding `maximumPermissibleUnhealthyDuration`, then the acceptance
of application transactions is disabled. Once the system reports an unhealthy duration below
`maximumPermissibleUnhealthyDuration` seconds, application transactions are accepted again.

##### PCES Replay

PCES replay is also limited by the health monitor, but the amount of time that a queue is permitted to be unhealthy
is configured with a separate value, `replayHealthThreshold`. There is a separate config value because the rate that
events can be replayed vastly outstrips the rate that the system could receive events via gossip.

In addition to being governed by the health monitor, PCES replay is also subject to a rate limiter. The rate
limiter may be turned on or off with `limitReplayFrequency`, and tuned with `maxEventReplayFrequency`.
The additional rate limiter was added because the health monitor wasn't able to detect the influx of replayed events
quickly enough, and the system was flooded with large numbers of events. Therefore, the rate limiter must be tuned
low enough that the initial replayed events don't overwhelm the system, but high enough that the transaction handler
is fully utilized throughout the replay process.
