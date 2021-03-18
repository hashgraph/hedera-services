# The autorenew feature

## Goal
-	Allow Hedera Services to automatically search for entities such as accounts, files, smart contracts, topics, tokens... that are about to expire and renew them.

## Design
- Introduce new settings in `application.properties`:
  * `autorenew.isEnabled`
  * `autorenew.secondsTillEntityExpires`
  * `autorenew.numberOfEntitiesToCheck`
  * `autorenew.gracePeriod`
- Each Hedera entity has an `expirationTime` which is the effective consensus timestamp at (and after) which the entity is set to expire.
- Each Hedera entity also has an `autoRenewAccount` which is the account to pay for the fee at renewal. This `autoRenewAccount` could be itself if the entity is a crypto account or an account associated with the entity when it was created.
- When a Hedera entity is created, its initial lifetime is defined by its `autoRenewPeriod`. At its `expirationTime`, Hedera Services will try to extend an entity's lifetime by another `autoRenewPeriod` if the `autoRenewAccount` has enough balance to do so, or as much extension as the remaining balance permits.
- A Hedera entity is `about` to expire when the current timestamp plus `autorenew.secondsTillEntityExpires` >= its `expirationTime`.
- After handling a transaction, Hedera Services will search within the next `autorenew.numberOfEntitiesToCheck` for entities that are about to expire. For each of such entities:
  * If the entity was marked `deleted`, do nothing.
  * Otherwise, try to renew this entity.
- At the time of this writing, there is an AUTORENEW_GRACE_PERIOD of 7 days being mentioned in the HAPI document. Propose to use `autorenew.gracePeriod` in `application.properties` instead.
- After the grace period, if the `expirationTime` of an entity is not extended, it will be deleted from the system.

## Implementation
When trying to renew an entity:
1. Calculate the fee to extend the entity's `expirationTime` for another `autoRenewPeriod`.
2. If the `autoRenewAccount` of the entity has enough balance to cover this fee:
  - extend the entity's `expirationTime` for another `autoRenewPeriod`.
  - otherwise, translate the remaining balance of the `autoRenewAccount` into an extension, preferably proportional to the fee calculated in step 1, then extend accordingly.

When scanning for entities, if an entity has expired:
- If the grace period also passes, delete the entity from the system.
- If the grace period does not pass yet, try to renew the entity as above. The entity could have been up for autorenewal when its `autoRenewAccount` ran out of balance. Then its `autoRenewAccount` get refilled when the system has moved the scanning to subsequent entities.
