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

When a Hedera entity is created, the payer account is charged enough hbars (as a rental fee) so that the entity can stay active in the system until it reaches the expiration time. Users can extend the expiration time of an entity by paying the appropriate fee for the extension via an update transaction. This HIP defines and discusses another mechanism offered by Hedera Services to automatically renew or delete entities that are deemed to expire.

## Motivation

At the time of this writing, the expiration time of a Hedera entity is not checked or enforced. An entity continues to stay active in the system after its expiration time, without being charged. Hedera Services will __start to charge__ for an extension automatically or will delete the entity from the system if the admin account (or the autoRenewAccount) of the entity has a zero balance at the time of renewal.

## Rationale

This section seems to be a duplicate of the `Motivation` section above. We will add more details if it is required.

## Specification

All nodes in a system of Hedera Services will perform an identical (consensus) circular scanning of entities. For those entities that are found deemed to have expired, Hedera Services will try to renew them by charging their admin account (or `autoRenewAccount`) for an extension of `autoRenewPeriod` seconds. Please note that if the autoRenewAccount does not have enough balance to cover this fee, the remaining balance will be wholly used for a shorter extension of the entity, and the autoRenewAccount will have zero balance after this extension. If the autoRenewAccount already reaches a zero balance at the time of renewal, the entity will be deleted permanently from the system.

Hedera Services will generate either an [autorenewal-record](https://github.com/hashgraph/hedera-services/blob/autorenew-document/docs/autorenew-feature.md#autorenewal-record) or an [autodeletion-record](https://github.com/hashgraph/hedera-services/blob/autorenew-document/docs/autorenew-feature.md#autodeletion-record) for this action on each entity that is found deemed to have expired.

Files do not have an autoRenewAccount so they can only be renewed manually by a file update. We will extend the protobufs for files in the future.

## Backwards Compatibility

There is no change in the protobufs. Users please make sure your account which is responsible for the renewal of an entity has balance. Hedera Services will __start to charge__ for an extension automatically or will delete the entity from the system at the time of renewal. The Product team will decide and communicate on when this feature is turned on.

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
