/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.queries.validation;

import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVING_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.jetbrains.annotations.NotNull;

@Singleton
public class QueryFeeCheck {
    private final OptionValidator validator;
    private final Supplier<AccountStorageAdapter> accounts;

    @Inject
    public QueryFeeCheck(
            final OptionValidator validator, final Supplier<AccountStorageAdapter> accounts) {
        this.accounts = accounts;
        this.validator = validator;
    }

    public ResponseCodeEnum nodePaymentValidity(
            @NotNull final List<AccountAmount> transfers,
            final long queryFee,
            final AccountID node) {
        var plausibility = transfersPlausibility(transfers);
        if (plausibility != OK) {
            return plausibility;
        }

        long netPayment = 0;
        boolean nodeReceivesSome = false;
        boolean nodeReceivesEnough = true;
        for (final var adjust : transfers) {
            final var amount = adjust.getAmount();
            if (amount < 0) {
                netPayment += -amount;
            } else if (adjust.getAccountID().equals(node)) {
                nodeReceivesSome = true;
                if (amount < queryFee) {
                    nodeReceivesEnough = false;
                }
            }
        }
        if (netPayment < queryFee) {
            return INSUFFICIENT_TX_FEE;
        }
        if (!nodeReceivesSome) {
            return INVALID_RECEIVING_NODE_ACCOUNT;
        }
        if (!nodeReceivesEnough) {
            return INSUFFICIENT_TX_FEE;
        }

        return OK;
    }

    ResponseCodeEnum transfersPlausibility(List<AccountAmount> transfers) {
        if (transfers.isEmpty()) {
            return INVALID_ACCOUNT_AMOUNTS;
        }

        long net = 0L;
        for (final var adjust : transfers) {
            final var plausibility = adjustmentPlausibility(adjust);
            if (plausibility != OK) {
                return plausibility;
            }
            try {
                net = Math.addExact(net, adjust.getAmount());
            } catch (final ArithmeticException ignore) {
                return INVALID_ACCOUNT_AMOUNTS;
            }
        }
        return (net == 0) ? OK : INVALID_ACCOUNT_AMOUNTS;
    }

    ResponseCodeEnum adjustmentPlausibility(AccountAmount adjustment) {
        var id = adjustment.getAccountID();
        var key = fromAccountId(id);
        long amount = adjustment.getAmount();

        if (amount == Long.MIN_VALUE) {
            return INVALID_ACCOUNT_AMOUNTS;
        }

        if (amount < 0) {
            return balanceCheck(accounts.get().get(key), Math.abs(amount));
        } else {
            if (!accounts.get().containsKey(key)) {
                return ACCOUNT_ID_DOES_NOT_EXIST;
            }
        }

        return OK;
    }

    /**
     * Validates query payment transfer transaction before reaching consensus. Validate each payer
     * has enough balance that is needed for transfer. If one of the payer for query is also paying
     * transactionFee validate the payer has balance to pay both
     *
     * @param txn the transaction body to validate
     * @return the corresponding {@link ResponseCodeEnum} after the validation
     */
    public ResponseCodeEnum validateQueryPaymentTransfers(TransactionBody txn) {
        AccountID transactionPayer = txn.getTransactionID().getAccountID();
        TransferList transferList = txn.getCryptoTransfer().getTransfers();
        List<AccountAmount> transfers = transferList.getAccountAmountsList();
        long transactionFee = txn.getTransactionFee();

        final var currentAccounts = accounts.get();
        ResponseCodeEnum status;
        for (AccountAmount accountAmount : transfers) {
            var id = accountAmount.getAccountID();
            long amount = accountAmount.getAmount();

            if (amount < 0) {
                amount = -1 * amount;
                if (id.equals(transactionPayer)) {
                    try {
                        amount = Math.addExact(amount, transactionFee);
                    } catch (ArithmeticException e) {
                        return INSUFFICIENT_PAYER_BALANCE;
                    }
                }
                if ((status = balanceCheck(currentAccounts.get(fromAccountId(id)), amount)) != OK) {
                    return status;
                }
            }
        }
        return OK;
    }

    private ResponseCodeEnum balanceCheck(@Nullable HederaAccount payingAccount, long req) {
        if (payingAccount == null) {
            return ACCOUNT_ID_DOES_NOT_EXIST;
        }
        final long balance = payingAccount.getBalance();
        if (balance >= req) {
            return OK;
        } else {
            final var expiryStatus =
                    validator.expiryStatusGiven(
                            balance, payingAccount.getExpiry(), payingAccount.isSmartContract());
            return (expiryStatus == OK) ? INSUFFICIENT_PAYER_BALANCE : expiryStatus;
        }
    }
}
