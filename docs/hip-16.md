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

When a Hedera entity is created (e.g., an account, file, smart contract, HCS topic, etc.), the payer account is charged enough hbars (as a rental fee) for the entity to stay active in the ledger state until consensus time passes its _expiration time_. Users can extend the expiration time of an entity by paying an extension fee via an update transaction. This HIP defines and discusses another mechanism to be implemented by Hedera Services to automatically renew expired entities using funds of linked _autorenew accounts_ or _admin accounts_; and automatically remove expired entities that have not been renewed by either update or auto-renewal.

## Motivation

For a public ledger to avoid suffering a tragedy of the commons it is important that all participants in the ledger share in the cost of ledger resources used. Renewal and auto-renewal fees are the implementation of this principal.

## Rationale

Prior to this HIP, the expiration time of a Hedera entity has not been checked or enforced. An entity remains active in the ledger even after its expiration time, without additional fees being charged. Upon implementation of this HIP, Hedera Services will __begin to charge rent__ for entities; and will eventually remove from the ledger expired entities that have not been renewed, either manually or by autorenewal from a funded admin/autorenew account at the time renewal fees are due.

The expiration time of an entity still can be extended via an update transaction, as it is currently supported. Anyone can initiate this update, not just the owner or the admin of the entity. Users will not be overcharged for the extension fee.

## Specification

### Terminology
- Deletion - A successful delete transaction will mark an entity as deleted and that entity cannot be operated upon.
The entity will remain in the ledger, marked as deleted, until it expires.
- Expiration - the entity has passed its expiration date and has not been renewed, so it is temporarily disabled.
- Renewal - the extension of an entity's expiration date, either by an update transaction, or by autorenewal.
- Removal - The entity is permanently removed from the state of the decentralized ledger.
- Grace period - The time during which an expired entity is disabled, but not yet removed, and can still be renewed.
- Action - An operation performed by the network that isn't during the processing of its transaction, such as an autorenewal, or the execution of a scheduled transaction.

All Hedera Services nodes will perform a synchronous scanning of active entities. When a node finds a non-deleted, expired
entity, it will try to renew the entity by charging its admin or autorenew account the renewal fee, for an extension
period given in seconds. A crypto account always acts as its own autorenewal account (there is not a separate account attached to it).

This extension period can be customized by the `autoRenewPeriod` property of the entity (e.g., a crypto account,
a topic, a smart contract, or a token type). For a file, the extension period will be three months. (Future protobuf changes will
permit customizing this extension period as well.) Records of autorenew charges will appear in the record stream, and
will be available via mirror nodes. __No__ receipts or records for autorenewal actions will be available via HAPI queries.

If the linked autorenew or admin account cannot cover the fee required for the default extension period, its remaining balance
will be wholly used for a shorter extension of the entity. If the linked account already has a zero balance at the time that
renewal fees are due, the entity will be marked as expired. 

An expired entity will still have a grace period before it is deleted. During that period, it is inactive, and all transactions involving it will fail, except for an update transaction to extend its expiration date. If it is not manually extended during the grace period, and if its autorenewal account still has a zero balance at the end of the grace period, then at the end of the grace period it will be permanently removed from the ledger. Its entity ID number will not be reused. The length of the grace period is a single, global setting for the entire ledger, such as 7 days. If it is renewed during the grace period (by a transaction, or by autorenewal at the end of the grace period, then the renewal must include payment for the portion of the grace period that has already passed.

If an entity was marked as deleted, then it cannot have its expiration date extended. Neither an update transaction nor an autorenew will be able to extend it.

Hedera Services will generate an [autorenewal-record](https://github.com/hashgraph/hedera-services/blob/autorenew-document/docs/autorenew-feature.md#autorenewal-record)
for the action on each entity that is automatically renewed. Hedera Services will generate an
[entity-removal-record](https://github.com/hashgraph/hedera-services/blob/autorenew-document/docs/autorenew-feature.md#entity-removal-record)
for the action on each entity that is automatically removed.

Crypto accounts will be prioritized for implementation of the autorenewal feature, followed by consensus topics, tokens and smart contracts. Schedule entities
do not autorenew, and cannot be manually renewed with a transaction, and are always removed from the ledger when they expire.

## Backwards Compatibility

There is no change in existing protobufs. Account and entity owners must ensure that linked autorenew and admin accounts have sufficient balances for autorenewal fees, or risk permanent removal of their entity.

Every entity will receive one free auto renewal at implementation of this feature. This will have the effect of extending the initial period for autorenewal ~92 days. Entities that are already past their expiration date will have it set to ~92 days after the date the feature is first deployed.

## Security Implications

A Hedera Account with zero balance at the point of renewal would become expired, and be removed after the grace period, if not renewed before then.

A Hedera Account with non-zero balance that is not sufficient to cover the entire cost of renewal will have its remaining balance wholly used for a shorter extension of the entity.

If the autoRenewAccount of a topic does not have sufficient balance the topic would be deleted. The ledger cannot enforce agreements regarding funding of the topic made by participants in the topic. 

Any entity can have its expiration time extended by anyone, not just by the admin account. The expiration time is the only field that can be changed in an update without being signed by the owner or the admin. (One exception: scheduled transactions cannot be renewed).

Users who leverage omnibus entities for services including wallets, exchanges, and custody will need to account for the deduction of hbar from any Hedera autorenewal accounts used in their system at the time of autorenewal.

## How to Teach This

This feature has been documented in the initial White Paper and protobuf document.

Implementation of this feature will be referenced in release notes, supported by SDKs, as well as supported at docs.hedera.com.

Key partners operating mirror nodes, wallets, exchanges, etc. should notify users when supporting account or entity creation of both the autorenew period and anticipated cost for autorenew.

## Reference Implementation

ledger.autoRenewPeriod.maxDuration=8000001 seconds // ~92 days
ledger.autoRenewPeriod.minDuration=6999999 seconds // ~81 days

The proposed pricing is as follows (assumes exchange rate as of April 6, 2020 and the maxDuration):
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
