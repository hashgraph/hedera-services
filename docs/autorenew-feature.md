# Design of the autorenew feature

## Goals
-	Allow Hedera Services to automatically search for entities such as accounts, files, smart contracts, topics, tokens... that are about to expire and renew them.

## Design
- Introduce new settings in `application.properties`:
  * `autorenew.isEnabled`
  * `autorenew.secondsTillEntityExpires`
  * `autorenew.numberOfEntitiesToCheck`
- Each Hedera entity has an `expirationTime` which is the effective consensus timestamp at (and after) which the entity is set to expire.
- A Hedera entity is `about` to expire when the current timestamp plus `autorenew.secondsTillEntityExpires` >= its `expirationTime`.
- After handling a transaction, Hedera Services will search within the next `autorenew.numberOfEntitiesToCheck` for entities that are about to expire. For each of such entities:
  * If the entity was marked `deleted`, do nothing.
  * Otherwise, try to renew this entity.
    + Calculate the fee to extend the entity's `expirationTime` for another `autoRenewPeriod`.
    + If the `autoRenewAccount` of the entity
