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

When a Hedera entity is created, the payer account is charged enough hbars (as a rental fee) for the entity to stay active in the ledger state until consensus time passes its _expiration time_. Users can extend the expiration time of an entity by paying an extension fee via an update transaction. This HIP defines and discusses another mechanism to be implemented by Hedera Services to automatically renew expired entities using funds of linked _autorenew accounts_ or _admin accounts_; and automatically remove expired entities that lack a funded autorenew account (or are deleted).

## Motivation

For a public ledger to avoid suffering a tragedy of the commons it is important that all participants in the ledger share in the cost of ledger resources used. Auto-renewal fees are the implementation of this principal.

## Rationale

Prior to this HIP, the expiration time of a Hedera entity has not been checked or enforced. An entity remains active in the ledger even after its expiration time, without additional fees being charged. Upon implementation of this HIP, Hedera Services will __begin to charge rent__ for automatically renewed entities; and will remove from the ledger expired entities which are either deleted or have an admin/autorenew account with zero balance at the time renewal fees are due.

## Specification

### Terminologies
- Deletion - A successful delete transaction will mark an entity as deleted and that entity cannot be operated up on.
The entity will remain in the ledger, marked as deleted, until it expires.
- Removal - The entity is permanently removed from the state of the decentralized ledger.

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
[entity-removal-record](https://github.com/hashgraph/hedera-services/blob/autorenew-document/docs/autorenew-feature.md#entity-removal-record)
for the action on each entity that is automatically removed.

Crypto accounts will be prioritized for autorenewal, followed by consensus topics, tokens and smart contracts. Schedule entities
do not autorenew, and are always removed from the ledger when they expire.

## Backwards Compatibility

There is no change in existing protobufs. Account and entity owners must ensure that linked autorenew and admin accounts have sufficient balances for autorenewal fees, or risk permanent removal of their entity.

Every account will receive one free auto renewal at implementation of this feature. This will have the effect of extending the initial period for autorenewal ~92 days.

## Security Implications

A Hedera Account with zero balance at the point of renewal would be marked for deletion.

A Hedera Account with non-zero balance that is not sufficient to cover the entire cost of renewal will have its remaining balance wholly used for a shorter extension of the entity. 

If the autoRenewAccount of a topic does not have sufficient balance the topic would be deleted. The ledger cannot enforce agreements regarding funding of the topic made by participants in the topic. 

Any entity can have its expiration time extended by anyone, not just by the admin account. The expiration time is the only field that can be changed in an update without being signed by the owner or the admin.

Accounts who leverage omnibus entities for services including wallets, exchanges, and custody will need to account for the deduction of hbar from any Hedera Entities used in their system at time of autorenewal.

## How to Teach This

This feature has been documented in the initial White Paper and protobuf document.

Implementation of this feature will be referenced in release notes, supported by SDKs, as well as supported at docs.hedera.com.

Key partners operating mirror nodes, wallets, exchanges, etc. should notify users when supporting account or entity creation of both the autorenew period and anticipated cost for autorenew. 

## Reference Implementation

Use standardized properties:
ledger.autoRenewPeriod.maxDuration=8000001 seconds // ~92 days
ledger.autoRenewPeriod.minDuration=6999999 seconds // ~81 days

The proposed pricing is as follows:
- "CryptoAccountAutoRenew": $0.0014
- "ConsensusTopicAutoRenew": $0.0003
- "TokenAutoRenew": $0.026
- "ContractAutoRenew": $0.064
- "FileAutoRenew": $0.0014

https://github.com/hashgraph/hedera-services/blob/autorenew-document/docs/autorenew-feature.md#autorenewal-record 

https://github.com/hashgraph/hedera-services/blob/autorenew-document/docs/autorenew-feature.md#entity-removal-record

## Rejected Ideas

N/A

## Open Issues

New issues will be created to track implementation in the hedera-services repo: https://github.com/hashgraph/hedera-services/issues

## References

N/A

## Copyright/license

This document is licensed under the Apache License, Version 2.0 -- see [LICENSE](../LICENSE) or (https://www.apache.org/licenses/LICENSE-2.0)
