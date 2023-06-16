# Platform Status

The platform status is represented as an enum value from `PlatformStatus.java`. It is updated via
`SwirldsPlatform::checkPlatformStatus`, which is passed around as a callback to any object that has the ability to
effect a change in platform status.

A diagram of expected state transitions is available [here](./platform-status-transitions.svg). Note that there are
currently no hard restrictions on state transitions: this diagram is merely a representation of the expected behavior.

## Used Statuses

The following statuses, along with their respective triggers, are listed in order of precedence (from highest to lowest):

- `DISCONNECTED`: There is more than one node in the network, and the number of active connections is zero
- `BEHIND`: The sync manager has reported that the node has fallen behind
- `FREEZING`: The freeze manager has reported that a freeze has started
- `FREEZE_COMPLETE`: The freeze manager has reported that a freeze has completed
- `ACTIVE`: None of the previously defined statuses apply

## Detailed Trigger Explanations

### `DISCONNECTED`

- `SwirldsPlatform::checkPlatformStatus` is called in `SwirldsPlatform::newConnectionOpened` and
`SwirldsPlatform::connectionClosed`, which are called each time a new connection is opened or closed, respectively
- If the number of active connections becomes zero, or ceases to be zero, the status is updated to `DISCONNECTED`, or
falls through to the status with next highest precedent

### `BEHIND`

- Each time the `ShadowGraphSynchronizer` syncs with another node, it checks whether it has fallen behind that other node
- If it has fallen behind, then `FallenBehindManagerImpl::reportFallenBehind` is called
- If this call causes the total number of fallen behind reports to exceed the threshold, then
  `SwirldsPlatform::checkPlatformStatus` is called, which results in the status transitioning to `PlatformStatus::BEHIND`
  - This only occurs when the threshold is first crossed, and not on subsequent reports
- The only way to transition out of this status is to perform a reconnect

### `FREEZING`
- Each time a round is handled with `ConsensusRoundHandler::consensusRound`,`SwirldStateManager::isInFreezePeriod`
is queried to determine whether a freeze period has started
- If `isInFreezePeriod` returns `true`, then `FreezeManager::freezeStarted` is called, which in turn calls
`SwirldsPlatform::checkPlatformStatus`, which triggers a state change to `FREEZING`
  - NOTE: this will only execute a single time, since the call to `FreezeManager::freezeStarted` is guarded by a boolean
  that flips true after the first call, and is never set back to false
- Once a round has been handled that causes the freeze (consensus) time to be reach or passed (causing the platform
to change to `FREEZING` status), no more consensus transactions will be applied to the state

### `FREEZE_COMPLETE`

- When `FreezeManager::stateToDisk` is finished executing, `SwirldsPlatform::checkPlatformStatus` is called, which
results in the status transitioning to `FREEZE_COMPLETE`
- Once the status has changed to `FREEZE_COMPLETE`, no more events will be created

### `ACTIVE`

- `SwirldsPlatform::checkPlatformStatus` is called at the end of `SwirldsPlatform::start`, which should result in the
status transitioning to `ACTIVE`
- This is the **only** status in which transactions are accepted from the application

### Special Case: Single Node Network

- `SwirldsPlatform::checkPlatformStatus` is called in a loop on the worker thread that is creating events

## Unused Statuses

The following statuses exist in the `PlatformStatus` enum, but are not currently used:

- `STARTING_UP`
- `REPLAYING_EVENTS`
- `READY`
