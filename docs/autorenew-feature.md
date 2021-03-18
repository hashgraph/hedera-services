# Design of the autorenew feature

## Goals
-	Allow Hedera Services to automatically search for entities such as accounts, files, smart contracts, topics, tokens... that are about to expire and renew them.

## Design
- Each Hedera entity has an `expirationTime` which is the effective consensus timestamp at (and after) which the entity is set to expire.
- Introduce `entity.secondsTillExpire` setting in `application.properties`.
- A Hedera entity is `about` to expire when the current timestamp plus `entity.secondsTillExpire` >= its `expirationTime`.
