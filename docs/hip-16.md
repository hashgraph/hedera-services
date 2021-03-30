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

When a Hedera entity is created, the payer account is charged enough hbars (as a rental fee) for the entity to stay active
in the ledger state until consensus time passes its _expiration time_. Users can extend the expiration time of an entity by
paying an extension fee via an update transaction. This HIP defines and discusses another mechanism to be implemented by
Hedera Services to automatically renew expired entities using funds of linked _autorenew accounts_ or _admin accounts_; and
automatically remove expired entities that lack a funded autorenew account (or are deleted).

## Motivation

Prior to this HIP, the expiration time of a Hedera entity has not been checked or enforced. An entity remains active in
the ledger even after its expiration time, without additional fees being charged. Upon implementation of this HIP,
Hedera Services will __begin to charge rent__ for automatically renewed entities; and will remove from the ledger expired
entities which are either deleted, or have an admin/autorenew account with zero balance at the time renewal fees are due.

## Rationale

This section seems to be a duplicate of the `Motivation` section above. We will add more details if required.

## Specification

All Hedera Services nodes will perform a synchronous scanning of active entities. When a node finds a non-deleted, expired
entity, it will try to renew the entity by charging its admin or autorenew account the renewal fee, for an extension
period given in seconds.

This extension period can be customized by the `autoRenewPeriod` property of a crypto account,
a topic, a smart contract, or a token. For a file, the extension period will be three months. (Future protobuf changes will
permit customizing this extension period as well.) Records of autorenew charges will appear in the record stream, and
will be available via mirror nodes. __No__ receipts or records for autorenewal actions will be available via HAPI queries.

If the linked autorenew or admin account cannot cover the fee required for the default extension period, its remaining balance
will be wholly used for a shorter extension of the entity. If the linked account already has a zero balance at the time that
renewal fees are due, the entity will be removed permanently from the ledger. Similarly, any expired entity that was already
marked deleted will be removed from the ledger.

Hedera Services will generate an [autorenewal-record](https://github.com/hashgraph/hedera-services/blob/autorenew-document/docs/autorenew-feature.md#autorenewal-record)
for the action on each entity that is automatically renewed. Hedera Services will generate an
[autoremoval-record](https://github.com/hashgraph/hedera-services/blob/autorenew-document/docs/autorenew-feature.md#autodeletion-record)
for the action on each entity that is automatically removed.

Crypto accounts will be prioritized for autorenewal, followed by consensus topics, tokens and smart contracts. Schedule entities
do not autorenew, and are always removed from the ledger when they expire.

## Backwards Compatibility

There is no change in existing protobufs. Account and entity owners must ensure that linked autorenew and admin accounts have
sufficient balances for autorenewal fees, or risk permanent removal of their entity! The Hedera Product team will set and
publicize the timeline for enabling the autorenewal and autoremoval behaviors.

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
