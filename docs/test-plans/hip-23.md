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

## Scope of test plan

This feature 

This feature has the following top-level impacts:
  1. **User-facing HAPI**: `CryptoCreate`, `CryptoUpdate`, `CryptoGetInfo`
  2. **Transaction Records**: `CryptoTransfer`, `TokenCreate`
  3. **`handleTransaction` Flow**: `CryptoCreate`, `CryptoUpdate`, `CryptoTransfer`

