# Characterization of HIP-18 fee charging

In this document we present several case studies of `CryptoTransfer`s
that involve a HTS token with a custom fee schedule. We use the 
`TransactionRecord`s of these transfers to characterize how Services 
charges custom fees as defined in HIP-18.

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

:free:&nbsp; There are two exemptions from the charging policy. First, token 
treasury accounts are never charged custom fees. Second, a fee collector
account is not charged any fractional fees for which it is the collector.

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
