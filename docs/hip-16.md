---
hip: 16
title: Entity Auto Renewal
author: Leemon Baird (@lbaird), Nosh Mody (@noshmody), Quan Nguyen (@qnswirlds)
type: Standards Track
category: Service
status: Draft
created: 2021-03-29
discussions-to: <a URL pointing to the official discussion thread>
updated:
requires:
replaces:
superseded-by:
---

## Abstract

When a Hedera entity is created, the payer account is charged enough hbars (as a rental fee) so that the entity can stay active in the ledger state until it reaches the expiration time. Users can extend the expiration time of an entity by paying the appropriate fee for the extension via an update transaction. This HIP defines and discusses another mechanism to be implemented by Hedera Services to automatically renew, using funds of the related accounts, or delete entities lacking such funds.

## Motivation

At the time of this writing, the expiration time of a Hedera entity is not checked or enforced. An entity continues to stay active in the ledger after its expiration time, without fees being charged. Hedera Services will __start to charge rent__ for automatically renewing entities or will delete the entity from the ledger if the admin account (or the autoRenewAccount) of the entity has a zero balance at the time of renewal.

## Rationale

This section seems to be a duplicate of the `Motivation` section above. We will add more details if it is required.

## Specification

All nodes in a system of Hedera Services will perform a synchronous scanning of entities in the ledger. For those entities that have expired, Hedera Services will try to renew them by charging their admin account (or `autoRenewAccount`) for an extension of `autoRenewPeriod`. Records will be added to the record stream for these ledger actions and will be available via mirror nodes. __No__ receipts or records for autorenewal actions will be available via HAPI queries.

Please note that if the `autoRenewAccount` does not have enough balance to cover the fee for an extension of `autoRenewPeriod`, the remaining balance will be wholly used for a shorter extension of the entity, and the autoRenewAccount will have zero balance after this extension. If the autoRenewAccount already reaches a zero balance at the time of renewal, the entity will be deleted permanently from the ledger.

Hedera Services will generate an [autorenewal-record](https://github.com/hashgraph/hedera-services/blob/autorenew-document/docs/autorenew-feature.md#autorenewal-record) for the action on an entity that is autorenewed. Hedera Services will generate an [autodeletion-record](https://github.com/hashgraph/hedera-services/blob/autorenew-document/docs/autorenew-feature.md#autodeletion-record) for the action on each entity that is found deemed to have expired.

Crypto accounts will be prioritized for autorenewal followed by consensus topics, tokens and smart contracts.

Files do not have an autoRenewAccount so they can only be renewed manually by a file update. We will extend the protobufs for files in the future.

## Backwards Compatibility

There is no change in existing protobufs. Account and entity owners must ensure that accounts responsible for the renewal of an entity have a sufficient balance or risk deletion of the entity.
Hedera Services will __start to charge__ for an extension automatically or will delete the entity from the ledger at the time of renewal. The Product team will decide and communicate on when this feature is turned on.

## Security Implications

N/A

## How to Teach This

N/A

## Reference Implementation

N/A

## Rejected Ideas

N/A

## Open Issues

N/A

## References

N/A

## Copyright/license

This document is licensed under the Apache License, Version 2.0 -- see [LICENSE](../LICENSE) or (https://www.apache.org/licenses/LICENSE-2.0)
