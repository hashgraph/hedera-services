# Characterization of HIP-18 fee charging

In this document we present several case studies of `CryptoTransfer`s
that involve an HTS token with a custom fee schedule. We use the 
`TransactionRecord`s of these transfers to characterize how Services 
charges custom fees as defined in HIP-18 and modified in HIP-573.

## Custom Fee Exemption Conditions

:free:&nbsp; There are three exemptions from the charging policy:
1. Token treasury accounts are never charged custom fees
2. A fee collector account is not charged any fractional fees for
   which it is itself the collector
3. A token's custom fee(s) is overridden with `allCollectorsAreExempt` 
set to `true`

## HIP-573

HIP-573 defines a new property on a custom fee that acts as an 
override exemption:`allCollectorsAreExempt`. When this property is 
set to `true` on a custom fee then that custom fee will **not** be 
assessed to any **collector** (i.e. the code responsible for 
charging a custom fee) in a token transfer. For a fungible token* 
this means that any custom fee collector will not be charged said 
custom fee for exchanging any units of the fungible token.

*Note: The only case the exemption property applies to for a non-
fungible token is a royalty fallback fee, which fallback fee is 
subject to the same three exemption conditions listed above.

# Case studies

Our case studies are as follows:
1. Zephyr sends Amelie an NFT of type `westWindArt` (with no
other transfers), where the `westWindsArt` token has a royalty 
fee of 1/100th exchange value and a fallback fee of 1USDC.
2. Amelie now sends Alice the `westWindArt` NFT, but this time in 
exchange for 1000ℏ and 200USDC.
3. Alice transfers an NFT of type `fixedHbarFeeToken` to Bob, 
where the `fixedHbarFeeToken` has a fixed 1ℏ custom fee.
4. Bob transfers 1000 units of the `fractionalFeeToken` to
Alice, where the `fractionalFeeToken` has a custom fee of
1/100th of its units transferred, up to a maximum of 5 units
and a minimum of 1 unit.
5. Claire transfers 100 units of the `simpleHtsFeeToken` to
Debbie, where the `simpleHtsFeeToken` requires 2 units of 
a `commissionPaymentToken`.
6. Debbie transfers 1 unit of the `nestedHbarFeeToken` to Edgar,
where the `nestedHbarFeeToken` has a custom fee of 1 unit of the 
`fixedHbarFeeToken`.
7. Edgar transfers 10 units of the `nestedFractionalFeeToken` to
Fern, where the `nestedFractionalFeeToken` has a custom fee of 
50 units of the `fractionalFeeToken`.
8. Fern now transfers 5000 units of the 
`fractionalCustomFeeExemptToken` to George, where the 
`fractionalCustomFeeExemptToken` token has both Fee #1, a custom 
fractional fee of 1/500th exchange value to Henry with a minimum 
of 2 units, _and_ Fee #2, a custom fractional fee of 1/100th 
exchange value to Irene with a minimum of 5 units. The 
`fractionalCustomFeeExemptToken` has `allCollectorsAreExempt` set 
to `true`.

# Royalty fees

A _royalty fee_ defines the fraction of the fungible value exchanged for 
an NFT that the ledger should collect as a royalty. 
 - Only allowed for non-fungible token types.
 - Fungible exchange value includes both ℏ _and_ units of fungible HTS tokens. 
 - A royalty fee can have a "fallback" fixed fee to be assessed when 
an NFT is transferred without the exchange of fungible value.

:warning:&nbsp;The ledger interprets **ALL** fungible value received by 
an account when it sends an NFT to be "in exchange" for the NFT! 

## Fallback royalty fees 
First we see that the fallback fee for an NFT is charged to the 
receiver of the NFT, not the sender.

In the record excerpt below, we see Zephyr (`0.0.1001`) paying to
send NFT `0.0.1006.1` to Amelie (`0.0.1002`) with no other transfers; 
and Amelie being charged a "fallback fee" of 1USDC (token `0.0.1005`) 
that goes to to the West Wind Art fee collector (`0.0.1004`). 

Please note the following:
- Amelie still had to sign this `CryptoTransfer`, even though there 
was no explicit transfer from account `0.0.1002`; otherwise it would
have resolved to `INVALID_SIGNATURE`.
- If Amelie had not been associated to the USDC token `0.0.1005`, the
result would have been `TOKEN_NOT_ASSOCIATED_TO_ACCOUNT`.
- If Amelie had zero balance of USDC, the result would have been to 
`INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE`.
```
transferList {
  accountAmounts {
    accountID { accountNum: 3 }
    amount: 76388
  }
  accountAmounts {
    accountID { accountNum: 98 }
    amount: 1907727
  }
  accountAmounts {
    accountID { accountNum: 1001 }
    amount: -1984115
  }
}
tokenTransferLists {
  token { tokenNum: 1005 }
  transfers {
    accountID { accountNum: 1002 } 
    amount: -1
  }
  transfers {
    accountID { accountNum: 1004 } <<-- The "fallback fee" of 1USDC
    amount: 1
  }
}
tokenTransferLists {
  token { tokenNum: 1006 }
  nftTransfers {
    senderAccountID { accountNum: 1001 }
    receiverAccountID { accountNum: 1002 } <<-- Now Amelie owns the NFT
    serialNumber: 1
  }
}
assessed_custom_fees {
  amount: 1
  token_id { tokenNum: 1005 }
  fee_collector_account_id { accountNum: 1004 }
}
```

## Fractional royalty fees
Next we see that when fungible value **is** exchanged for an NFT with 
a royalty fee, the royalty applies to _all_ fungible value exchanged, 
whether denominated in ℏ or a fungible HTS token.

In the record excerpt below, we see Amelie (`0.0.1032`) paying to
sell `westWindArt` NFT `0.0.1036.1` to Alice (`0.0.1031`) in exchange 
for 1000ℏ and 200USDC (token `0.0.1035`). Because of the 1/100th 
royalty on the `westWindArt` token, Amelie only receives 990ℏ and 
198USDC; the other 10ℏ and 2USDC go to the West Wind Art fee collector 
(`0.0.1034`). 

```
transferList {
  accountAmounts {
    accountID { accountNum: 3 }
    amount: 280229
  }
  accountAmounts {
    accountID { accountNum: 98 }
    amount: 5984684
  }
  accountAmounts {
    accountID { accountNum: 1031 } <<-- The 1000ℏ Alice exchanged for the NFT
    amount: -100000000000 
  }
  accountAmounts {
    accountID { accountNum: 1032 } <<-- Only 990ℏ go to Amelie
    amount: 98993735087 
  }
  accountAmounts {
    accountID { accountNum: 1034 } <<-- A 10ℏ royalty goes to West Wind Art
    amount: 1000000000 
  }
}
tokenTransferLists {
  token { tokenNum: 1035 }
  transfers {
    accountID { accountNum: 1031 } <<-- The 200USDC Alice exchanged for the NFT
    amount: -200 
  }
  transfers {
    accountID { accountNum: 1032 } <<-- Only 198USDC go to Amelie
    amount: 198
  }
  transfers {
    accountID { accountNum: 1034 } <<-- A 2USDC royalty goes to West Wind Art
    amount: 2
  }
}
tokenTransferLists {
  token { tokenNum: 1036 }
  nftTransfers {
    senderAccountID { accountNum: 1032 }
    receiverAccountID { accountNum: 1031 } <<-- Now Alice owns the NFT
    serialNumber: 1
  }
}
assessed_custom_fees {
  amount: 1000000000
  fee_collector_account_id { accountNum: 1034 }
}
assessed_custom_fees {
  amount: 2
  token_id { tokenNum: 1035 }
  fee_collector_account_id { accountNum: 1034 }
}
```

# Fixed fees

## Fees denominated in ℏ
This case study shows how fixed ℏ fees are assessed and charged. 
This is the simplest pattern; for each `AccountAmount` 
in the `CryptoTransfer` that debits units of the token with the
fixed fee, the ledger charges the fixed ℏ fee to the account 
sending those units. Hence Alice is charged 1ℏ, which goes to
the custom fee collection account of the `fixedHbarFeeToken`.

In the record excerpt below, we see Alice (`0.0.1015`) paying to
transfer an NFT with serial number 1 to Bob (`0.0.1016`); the 
unique token's id is `0.0.1018`, and its fee collection account 
is `0.0.1017`. Besides paying the Hedera fees, Alice pays the 
1ℏ fee to the collection account. The final `assessed_custom_fees` 
section shows this explicitly (since no `token_id` is set, the 
denomination is ℏ).
```
transferList {
  accountAmounts {
    accountID { accountNum: 3 }
    amount: 75779
  }
  accountAmounts {
    accountID { accountNum: 98 }
    amount: 1590886
  }
  accountAmounts {
    accountID { accountNum: 1015 }
    amount: -101666665
  }
  accountAmounts {
    accountID { accountNum: 1017 } <<-- Fee collection account gets 1ℏ
    amount: 100000000
  }
}
tokenTransferLists {
  token { tokenNum: 1018 }
  nftTransfers {
    senderAccountID { accountNum: 1015 }
    receiverAccountID { accountNum: 1016 }
    serialNumber: 1
  }
}
assessed_custom_fees {
  amount: 100000000
  fee_collector_account_id { accountNum: 1017 }
}
```

## Fees denominated in an HTS token
The third case study shows how fixed fees with a custom
denomination are assessed and charged. These fees are assessed
in a way similar to fixed ℏ fees; but note that each account to 
be charged _must be associated to the denominating token_. Here
Claire is associated to the `commissionPaymentToken` (and has a
sufficient balance), so 2 units of that token are transferred from
her account to the fee collection account of the `simpleHtsFeeToken`.

In the record excerpt below, we see Claire (`0.0.1019`) paying to
transfer 100 units of token `0.0.1023` to Debbie (`0.0.1020`); the fee 
collection account is account `0.0.1021`. Besides paying the Hedera 
fees, Alice pays 2 units of the custom fee token `0.0.1022` to the
fee collection account; that is, there are two token transfer lists
in this case---one for the original token, one for its custom fee
token.
```
transferList {
  accountAmounts {
    accountID { accountNum: 3 }
    amount: 123228
  }
  accountAmounts {
    accountID { accountNum: 98 }
    amount: 2491021
  }
  accountAmounts {
    accountID { accountNum: 1019 }
    amount: -2614249
  }
}
tokenTransferLists {
  token { tokenNum: 1022 } <<-- The custom fee token
  transfers {
    accountID { accountNum: 1019 }
    amount: -2
  }
  transfers {
    accountID { accountNum: 1021 }
    amount: 2
  }
}
tokenTransferLists {
  token { tokenNum: 1023 } <<-- The original token
  transfers {
    accountID { accountNum: 1019 }
    amount: -100
  }
  transfers {
    accountID { accountNum: 1020 }
    amount: 100
  }
}
assessed_custom_fees {
  amount: 2
  token_id { tokenNum: 1022 }
  fee_collector_account_id { accountNum: 1021 }
}
```

## Fees denominated in an HTS token with "nested" custom fees
The fourth case study shows that custom fees **also apply** to 
custom fee payments. That is, because Debbie is charged 1 unit of
the `fixedHbarFeeToken` to transfer the `nestedHbarFeeToken`, she
must pay the exact same 1ℏ fee that Alice did in our first case 
study, again to the treasury of the `fixedHbarFeeToken`.

:shield:&nbsp;It is important to note that fee schedules cannot
be nested to any depth. In fact this example demonstrates the 
maximum allowed depth; if the `fixedHbarFeeToken` was instead
a `secondNestedHtsFeeToken`, the transfer would resolve to
`CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH` at consensus.

In the record excerpt below, we see Debbie (`0.0.1012`) paying to
transfer 1 unit of token `0.0.1017` to Edgar (`0.0.1013`); the fee 
collection account for this top-level transfer is account `0.0.1014`, 
and it receives 1 unit of the custom fee token `0.0.1016`. But 
there is **also** a "nested" fee assessed for Debbie's transfer of
this unit; that is, she also pays the 1ℏ fee to the fee collection
account of the custom fee token, which is `0.0.1015`.
```
transferList {
  accountAmounts {
    accountID { accountNum: 3 }
    amount: 61752
  }
  accountAmounts {
    accountID { accountNum: 98 }
    amount: 1248326
  }
  accountAmounts {
    accountID { accountNum: 1012 } <<-- The extra 1ℏ nested fee
    amount: -101310078
  }
  accountAmounts {
    accountID { accountNum: 1015 }
    amount: 100000000
  }
}
tokenTransferLists {
  token { tokenNum: 1016 }
  transfers {
    accountID { accountNum: 1012 }
    amount: -1
  }
  transfers {
    accountID { accountNum: 1014 }
    amount: 1
  }
}
tokenTransferLists {
  token { tokenNum: 1017 }
  transfers {
    accountID { accountNum: 1012 }
    amount: -1
  }
  transfers {
    accountID { accountNum: 1013 }
    amount: 1
  }
}
assessed_custom_fees { <<-- The "top-level" fee
  amount: 1
  token_id { tokenNum: 1016 } 
  fee_collector_account_id { accountNum: 1014 }
}
assessed_custom_fees { <<-- The "nested" fee
  amount: 100000000
  fee_collector_account_id { accountNum: 1015 }
}

```

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
`fractionalFeeToken`, and the fee collection account for 
the token gets the remaining 5 units.

In the record below, we see Bob (`0.0.1010`) paying to 
transfer 1000 units to Alice (`0.0.1009`); the fee 
collection account is account `0.0.1011`. Also note the 
`assessed_custom_fees` section includes a fee denominated
in the custom token.

```
transferList {
  accountAmounts {
    accountID { accountNum: 3 }
    amount: 61718
  }
  accountAmounts {
    accountID { accountNum: 98 }
    amount: 1247646
  }
  accountAmounts {
    accountID { accountNum: 1010 }
    amount: -1309364
  }
}
tokenTransferLists {
  token { tokenNum: 1012 }
  transfers {
    accountID { accountNum: 1009 }
    amount: 995 <<-- Alice only receives 995 units
  }
  transfers {
    accountID { accountNum: 1010 }
    amount: -1000 <<-- Even though Bob transferred 1000
  }
  transfers {
    accountID { accountNum: 1011 }
    amount: 5 <<-- The fee collection account gets the rest
  }
}
assessed_custom_fees {
  amount: 5
  token_id { tokenNum: 1012 }
  fee_collector_account_id { accountNum: 1011 }
}
```

## Fractional fees from a nested fee schedule
The fifth case study shows that fractional fees still apply
to custom fee payments. That is, Edgar is charged 50 units
of the `fractionalFeeToken`, but only 49 units will go to 
the fee collection account of the `nestedFractionalFeeToken`; 
the remaining 1 unit is collected by the fee collection account 
of the `fractionalFeeToken` itself. (Because of the 1 unit 
minimum in the `fractionalFeeToken` schedule.)


In the record excerpt below, we see Edgar (`0.0.1001`) paying to
transfer 10 units of token `0.0.1006` to Debbie (`0.0.1002`); the fee 
collection account is account `0.0.1003`, which is assessed to 
receive 50 units of token `0.0.1005`. **However**, because this 
token itself has a fractional fee schedule with a minimum charge
of 1 unit, the collection account `0.0.1003` only receives 49 
units, and the nested fee goes to a separate fee collection account,
in this case `0.0.1004`.

```
transferList {
  accountAmounts {
    accountID { accountNum: 3 }
    amount: 74651
  }
  accountAmounts {
    accountID { accountNum: 98 }
    amount: 1506323
  }
  accountAmounts {
    accountID { accountNum: 1001 }
    amount: -1580974
  }
}
tokenTransferLists {
  token { tokenNum: 1005 }
  transfers {
    accountID { accountNum: 1001 }
    amount: -50
  }
  transfers {
    accountID { accountNum: 1003 }
    amount: 49
  }
  transfers {
    accountID { accountNum: 1004 }
    amount: 1
  }
}
tokenTransferLists {
  token {
    tokenNum: 1006
  }
  transfers {
    accountID { accountNum: 1001 }
    amount: -10
  }
  transfers {
    accountID { accountNum: 1002 }
    amount: 10
  }
}
assessed_custom_fees {
  amount: 50
  token_id { tokenNum: 1005 }
  fee_collector_account_id { accountNum: 1003 }
}
assessed_custom_fees {
  amount: 1
  token_id { tokenNum: 1005 }
  fee_collector_account_id { accountNum: 1004 }
}
```

## Fractional fees with exempt collectors
The eighth case study shows that fractional fees attached to a token 
with overridden collector exemptions do not apply to collector 
accounts. When Fern (account `0.0.1020'`) transfers 5000 units of token 
`fractionalCustomFeeExemptToken` to George (account `0.0.1021`), 
collectors Henry (account `0.0.1030`) and Irene (account `0.0.1031`) 
will receive `(1/500) * 5000 = 10 units` and `(1/100) * 5000 = 50 units`, 
respectively, due to fractional custom fees Fee #1 and Fee #2 defined 
on the token. Since these are fractional custom fees, Henry––who **is** 
a collector but is **not** the receiver (George is the receiver of the 
original transfer)––would typically be charged `(1/100) * 50 units = 5 
units` additionally to Irene because of Fee #2, and Irene––who is also 
a collector but not the original receiver--likewise would be charged 
`(1/500) * 5000 = 10` additionally to Henry because of Fee #1. However, 
because `allCollectorsAreExempt` is `true` on the 
`fractionalCustomFeeExemptToken`, both Henry and Irene––as collectors 
on the original token transfer from Fern to George––are exempt from the 
fractional custom fees they also would have paid to each other.

```
tokenTransferLists {
  token { tokenNum: 1013 }
  transfers {
    accountID { accountNum: 1020 }
    amount: -5000
  }
  transfers {
    accountID { accountNum: 1021 }
    amount: 4940  <<-- George receives 5000 - (custom fees Fee #1 and Fee #2)
  }
  transfers {
    accountID { accountNum: 1030 }
    amount: 10  <<-- Henry receives (1/500) * 5000 = 10 units
  }
  transfers {
    accountId { accountNum: 1031 }
    amount: 50  <<-- Irene receives (1/100) * 5000 = 50 units
  }
}
assessed_custom_fees {
  amount: 10
  token_id { tokenNum: 1013 }
  fee_collector_account_id { accountNum: 1030 }
}
assessed_custom_fees {
  amount: 50
  token_id { tokenNum: 1013 }
  fee_collector_account_id { accountNum: 1031 }
}
```

Notably absent are the transactions that also would have applied 
to Henry and Irene in the case where collectors were not exempt
from custom fees. _The following transactions did not 
happen_ because `fractionalCustomFeeExemptToken`'s 
`allCollectorsAreExempt` field was set to `true`, but _would 
have_ applied if `allCollectorsAreExempt` was set to `false`:
```
tokenTransferLists {
  token { tokenNum: 1013 }
  transfers {
    accountID { accountNum: 1020 }
    amount: -5000  <<-- Fern still transfers 5000 units
  }
  transfers {
    accountID { accountNum: 1021 }
    amount: 4940  <<-- George still receives 5000 minus (Fee #1 and Fee #2) units
  }
  transfers {
    accountID { accountNum: 1030 }
    amount: 13  <<-- see net transfer calculation below
  }
  transfers {
    accountId { accountNum: 1031 }
    amount: 47  <<-- see net transfer calculation below
  }
}
assessed_custom_fees {
  amount: 13
  token_id { tokenNum: 1013 }
  fee_collector_account_id { accountNum: 1030 }
}
assessed_custom_fees {
  amount: 47
  token_id { tokenNum: 1013 }
  fee_collector_account_id { accountNum: 1031 }
}
```
To explain the net calculation, let's define the following variables:
* let `h = 1/500`, the fee fraction of any transfer that goes to Henry
* let `i = 1/100`, the fee fraction of any transfer that goes to Irene
* let `t_0` represent the _original_ transfer of 5000 units from Fern 
to George
* let `hf1 = h * 5000 = 10 units`, i.e. the portion of the _original_ 
5000 units that will go to Henry as a result of transfer `t_0`
* let `if1 = i * 5000 = 50 units`, i.e. the portion of the _original_ 
5000 units that will go to Irene as a result of transfer `t_0` 
* let `t_h->i` represent the _collector_ transfer of the amount (i.e. 
amount `hf1`) 
Henry owes Irene as a result of `t_0`
* let `t_i->h` represent the _collector_ transfer of the amount (i.e. 
amount `if1`)
Irene owes Henry as a result of `t_0`
* let `hf2 = h * if1`, representing the transfer due to Irene _as a 
result of Henry's second transfer to Irene_ (this will be a fraction 
of a fraction)
* let `if2 = i * hf1`, representing the transfer due to Henry _as a 
result of Irene's second transfer to Henry_ (this will also be a 
fraction of a fraction)

(Whew!) 

The net transfer amount to Henry as a result of all three 
transactions––the original transaction of 5000; the transfers to 
Henry and Irene as a result of the _original_event; and the third 
transfer, which is the transfers to Henry and Irene as a result 
of the _second_ transfer event; would have then been calculated 
as follows:
```
( h * original transfer amount ) - max(2, amount TO Irene as a result of t_0) + max(5, amount FROM Irene as a result of t_h->i) =
+hf1 - max(2, i * hf1)      + max(5, h * if1)      = 
+10  - max(2, (1/100) * 10) + max(5, (1/500) * 50) =
+10  - max(2, 0.1)          + max(5, 0.2)          =
+10  - 2                    + 5                    = 13 units
```
The net transfer amount to Irene as a result of all three 
transactions would have been:
```
( i * original transfer amount ) - max(5, amount TO Henry as a result of t_0) + max(2, amount FROM Henry as a result of t_h->i) =
+if1 - max(5, h * if1)      + max(2, i * hf1)      = 
+50  - max(5, (1/500) * 50) + max(2, (1/100) * 10) = 
+50  - max(5, 0.1)          + max(2, 0.1)          =
+50  - 5                    + 2                    = 47 units
```
As expected, adding the net transfer results of Henry and Irene 
together is `13 + 47 = 60 units`, which is the same fee amount 
collected from George originally, but split out to Henry and 
Irene according to fractions `h` and `i`, and fractions (of 
fractions) `h * if1`, and `i * hf1`. Note that the calculation 
would have continued with fractions of fractions...of fractions 
if the third transfer amount had been greater than the minimum 
required amount for both Henry and Irene, which demonstrates 
how painful fractional amounts charged to collectors can be 
(and why HIP 573 was created). In general, a fractional fee on 
a token whose collectors are _not_ exempt will tunnel into 
fractions of fractions until the minimum required amount for 
all involved collectors drops to the minimum threshold units 
required by each fractional fee.