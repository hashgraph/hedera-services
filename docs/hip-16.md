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

This HIP defines and discusses a way to automatically search for entities other than schedule transactions, namely accounts, files, smart contracts, topics, tokens that are expiring, then either renew or delete them from the system.

## Motivation

Each Hedera entity has an expiration time which is the effective consensus timestamp at (and after) which the entity is set to expire. At the moment, an update transaction is needed to extend the expiration time of an entity. Even after an entity has expired, it still stays in the system which costs system resources.

## Rationale

It is crucial to add an enforcement for the expiration time. When an entity expired, it should either be automatically renewed to extend its expiration time or be deleted from the system to save resources in the system.

## Specification

The technical specification should describe the syntax and semantics of any new features. The specification should be detailed enough to allow competing, interoperable implementations for at least the current Hedera ecosystem.

## Backwards Compatibility

After the implementation of this HIP, all entities that had expired will either be automatically renewed by charging their associated `autoRenewAccount` or enter their grace period of 7 days before being removed permanently from the system.

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
