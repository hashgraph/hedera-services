# Platform Status

The platform status is represented as an enum value from `PlatformStatus.java`. It is updated via
`SwirldsPlatform::checkPlatformStatus`, which is passed around as a callback to any object that has the ability to
effect a change in platform status.

## Used Statuses

The following statuses, along with their respective triggers, are listed in order of precedence (from highest to lowest):

- `DISCONNECTED`: There is more than one node in the network, and the number of active connections is zero
- `BEHIND`: The sync manager has reported that the node has fallen behind
- `MAINTENANCE`: The freeze manager has reported that a freeze has started
- `FREEZE_COMPLETE`: The freeze manager has reported that a freeze has completed
- `ACTIVE`: None of the previously defined statuses apply

## Unused Statuses

The following statuses exist in the `PlatformStatus` enum, but are not currently used:

- `STARTING_UP`
- `REPLAYING_EVENTS`
- `READY`
