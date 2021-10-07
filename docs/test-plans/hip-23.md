# HIP-23 (automatic token associations)

This feature adds a new field `maxAutoAssociations` to the account entity, and a 
side-effect to any transaction that sends units/NFTs of a not-yet-associated token 
to an account with `maxAutoAssociations > 0`. 

It also adds a `TransactionRecord` field named `automatic_token_associations`.
This field lists all token associations that were created as "side-effects" of 
any transaction that is not an explicit `TokenAssociate`. (Since an existing 
`TokenCreate` _already_ auto-associates the new token treasury, and any fee 
collectors for fractional or self-denominated fixed fees, this feature also
"retrofits" the record of a `TokenCreate` with the `automatic_token_associations` 
field.)

:warning:&nbsp; Notice that right now, _only_ a `CryptoTransfer` or `TokenCreate` 
can have auto-associations as a side-effect. But after deployment of HTS precompiled 
contracts, any `ContractCreate` or `ContractCall` could also have auto-associations 
as side-effects.

## Scope

This feature has the following top-level impacts:
  - **State**: `MerkleAccountState`
  - **User-facing HAPI**: `CryptoCreate`, `CryptoUpdate`, `CryptoGetInfo`
  - **Transaction Records**: `CryptoTransfer`s that transfer tokens, `TokenCreate`
  - **`handleTransaction` Flow**: `CryptoCreate`, `CryptoUpdate`, `CryptoTransfer`

:small_blue_diamond:&nbsp;Since this feature affects token transfers, we should 
consider possible intersections with custom fees, and ensure that our tests give 
equal attention to transfers of both fungible token units _and_ NFTs.

:currency_exchange:&nbsp;Since this feature adds side-effects to parties in a 
`CryptoTransfer`, and multi-party `CryptoTransfer`s must commit or rollback 
atomically, we should validate that HIP-23 side-effects respect these semantics
across a variety of failure modes.

:paperclips:&nbsp;Since this feature has no rebuilt data structures, it has 
no special reconnect testing requirements.

So the high-level scope for this plan includes,
  1. Migration tests.
  2. Positive functional tests, especially with multiple accounts being 
  auto-associated, and accounts receiving multiple auto-associations.
  3. Negative functional tests, especially of multi-party `CryptoTransfer`s
  whose HIP-23 side-effects occur before a later problem with the flow.
  4. Record validation for all these positive and negative tests.
  5. State validation for all these positive and negative tests.
  6. Testing any possible interplay with custom fees.

## Methodology

We can now sketch what type of tests will be needed to meet the above scope.

:cactus:&nbsp;Careful unit testing should suffice for **migration** tests, as
the change to state is very small---a single field added to the 
`MerkleAccountState` leaf. 

:white_check_mark:&nbsp;**Positive functional** testing will require EET specs 
that perform all variants of `TokenCreate`s with implied auto-associations, and 
all variants of `CryptoTransfer`s that trigger one or more auto-associations for
one or more accounts.

:x:&nbsp;**Negative functional** testing will require EET specs that perform all
variants of `CryptoTransfer`s in which auto-associations are attempted, but do 
not succeed; and all variants of multi-party `CryptoTransfer`s in which 
auto-associations succeed, but must be rolled back due to a later failure in the
`CryptoTransfer` flow.

:fountain_pen:&nbsp;**Record validation** will require new EET assertions as
qualifiers on the [`TransactionRecordAsserts` class](https://github.com/hashgraph/hedera-services/blob/master/test-clients/src/main/java/com/hedera/services/bdd/spec/assertions/TransactionRecordAsserts.java#L43). Given these assertions, 
the above functional tests can be easily enhanced to validate their records.

:sparkle:&nbsp;**State validation** will require a new assert built-in for the 
EET `HapiGetAccountInfo` query, to assert on auto-association state. Given this, 
the above functional tests can be easily enhanced to validate state is 
changed (or unchanged) as appropriate.

:receipt:&nbsp;**Custom fee interplay** should provide an EET that dissociates
a custom fee collector from its denominating token; and then triggers a custom
fee payment to that collector. If the collector has open auto-association slots,
the custom fee payment should go through (by using a slot).

## Deliverables

We 
