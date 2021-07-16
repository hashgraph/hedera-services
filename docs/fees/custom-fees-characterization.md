# Characterization of HIP-18 fee charging

In this document we give five case studies of `CryptoTransfer`s
that involve a HTS token with a custom fee schedule. We use the
`TransactionRecord`s of these transfers to characterize how 
Services charges custom fees as defined in HIP-18.

# Five case studies

Our case studies are as follows:
1. Alice transfers an NFT of type `fixedHbarFeeToken` to Bob, 
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
the custom fee collection account of the `fixedHbarFeeToken`.

In the record excerpt below, we see Alice (`0.0.1013`) paying to
transfer the NFT with serial number 1 to Bob as (`0.0.1014`); the fee 
collection account is account `0.0.1015`. Besides paying the Hedera 
fees, Alice pays the 1ℏ fee to the collection account. The final 
`assessed_custom_fees` section shows this explicitly (since no
`token_id` is set, the denomination is ℏ).
```
transferList {
  accountAmounts {
    accountID { accountNum: 3 }
    amount: 37977
  }
  accountAmounts {
    accountID { accountNum: 98 }
    amount: 797398
  }
  accountAmounts {
    accountID { accountNum: 1013 }
    amount: -100835375
  }
  accountAmounts {
    accountID { accountNum: 1015 } <<-- Fee collection account gets 1ℏ
    amount: 100000000
  }
}
tokenTransferLists {
  token { tokenNum: 1016 }
  nftTransfers {
    senderAccountID { accountNum: 1013 }
    receiverAccountID { accountNum: 1014 }
    serialNumber: 1
  }
}
assessed_custom_fees {
  amount: 100000000
  fee_collector_account_id { accountNum: 1003 }
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

In the record excerpt below, we see Claire (`0.0.1018`) paying to
transfer 100 units of token `0.0.1022` to Debbie (`0.0.1019`); the fee 
collection account is account `0.0.1020`. Besides paying the Hedera 
fees, Alice pays 2 units of the custom fee token `0.0.1021` to the
fee collection account; that is, there are two token transfer lists
in this case---one for the original token, one for its custom fee
token.
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
    accountID { accountNum: 1018 }
    amount: -1309364
  }
}
tokenTransferLists {
  token { tokenNum: 1020 } <<-- The custom fee token
  transfers {
    accountID { accountNum: 1018 }
    amount: -2
  }
  transfers {
    accountID { accountNum: 1020 }
    amount: 2
  }
}
tokenTransferLists {
  token { tokenNum: 1022 } <<-- The original token
  transfers {
    accountID { accountNum: 1018 }
    amount: -100
  }
  transfers {
    accountID { accountNum: 1019 }
    amount: 100
  }
}
assessed_custom_fees {
  amount: 2
  token_id { tokenNum: 1021 }
  fee_collector_account_id { accountNum: 1020 }
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
