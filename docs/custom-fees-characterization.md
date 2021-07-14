# Characterization of HIP-18 fee charging

In this document we give five case studies of `CryptoTransfer`s
that involve a HTS token with a custom fee schedule. We use the
`TransactionRecord`s of these transfers to characterize how 
Services charges custom fees as defined in HIP-18.

# Five case studies

Our case studies are as follows:
1. Alice transfers 10 units of the `fixedHbarFeeToken` to Bob, 
where the `fixedHbarFeeToken` has a fixed 1ℏ custom fee.
2. Bob transfers 1000 units of the `fractionalFeeToken` to
Alice, where the `fractionalFeeToken` has a custom fee of
1/100th of its units transferred, up to a maximum of 5 units
and a minimum of 1 unit.
3. Claire transfers 100 units of the `simpleHtsFeeToken` to
Debbie, where the `simpleHtsFeeToken` requires 2 units of 
a `commissionPaymentToken`.
4. Debbie transfers 1 unit of the `nestedHbarFeeToken` to Edgar,
where the `nestedHbarFeeToken` has a custom fee of 1 unit of the 
`fixedHbarFeeToken`.
5. Edgar transfers 10 units of the `nestedFractionalFeeToken` to
Fern, where the `nestedFractionalFeeToken` has a custom fee of 
50 units of the `fractionalFeeToken`.

# Fixed fees

## Fees denominated in ℏ
The first case study shows how fixed ℏ fees are assessed 
and charged. This is the simplest pattern; for each `AccountAmount` 
in the `CryptoTransfer` that debits units of the token with the
fixed fee, the ledger charges the fixed ℏ fee to the account 
sending those units. Hence Alice is charged 1ℏ, which goes to
the treasury of the `fixedHbarFeeToken`.

__TODO: write EET with this scenario; capture the `CryptoTransfer` body here__

## Fees denominated in an HTS token
The third case study shows how fixed fees with a custom
denomination are assessed and charged. These fees are assessed
in a way similar to fixed ℏ fees; but note that each account to 
be charged _must be associated to the denominating token_. Here
Claire is associated to the `commissionPaymentToken` (and has a
sufficient balance), so 2 units of that token are transferred from
her account to the treasury of the `simpleHtsFeeToken`.

__TODO: write EET with this scenario; capture the `CryptoTransfer` body here__

## Fees denominated in an HTS token with "nested" custom fees
The fourth case study shows that custom fees **also apply** to 
custom fee payments. That is, because Debbie is charged 1 unit of
the `fixedHbarFeeToken` to transfer the `nestedHbarFeeToken`, she
must pay the exact same 1ℏ fee that Alice did in our first case 
study, again to the treasury of the `fixedHbarFeeToken`.

:shield:&nbsp;It is important to note that fee schedules cannot
be nested to any depth. In fact this example demonstrates the 
maximum allowed depth; if the `fixedHbarFeeToken` was instead
an `secondNestedHtsFeeToken`, the transfer would resolve to
`CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH` at consensus.

__TODO: write EET with this scenario; capture the `CryptoTransfer` body here__

# Fractional Fees

Fractional fees are always collected in the units of the token
whose fee schedule they belong to. (Never in ℏ or in units of a 
different HTS token.) Unlike fixed fees, they can also be set with
upper and lower bounds.

## Fractional fees from a top-level fee schedule
The second case study shows how fractional fees are assessed
and charged. Because Bob is transferring 1000 units of the 
`fractionalFeeToken`, the "initial" assessed fee is 
1000 / 100 = 10 units. But the 5 unit maximum then applies,
and reduces the fee to 5 units. These 5 units are **not** 
charged separately (leading to Bob's balance decreasing by
1005), but instead taken from the in-process transfer. 
Therefore Alice receives only 995 units of the 
`fractionalFeeToken`, and the treasury of the token gets
the remaining 5 units.

__TODO: write EET with this scenario; capture the `CryptoTransfer` body here__

## Fractional fees from a nested fee schedule
The fifth case study shows that fractional fees still apply
to custom fee payments. That is, Edgar is charged 50 units
of the `fractionalFeeToken`, but only 49 units will go to 
the treasury of the `nestedFractionalFeeToken`; the remaining
1 unit is collected by the treasury of the `fractionalFeeToken` 
itself. (Because of the 1 unit minimum in the `fractionalFeeToken` 
schedule.)

__TODO: write EET with this scenario; capture the `CryptoTransfer` body here__
