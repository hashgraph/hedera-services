/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import static com.hedera.hapi.node.base.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler;
import com.hedera.node.app.spi.workflows.HandleException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ZeroSumHbarChangesStep extends BaseTokenHandler implements TransferStep {
    final CryptoTransferTransactionBody op;

    public ZeroSumHbarChangesStep(final CryptoTransferTransactionBody op) {
        this.op = op;
    }

    @Override
    public void doIn(final TransferContext transferContext) {
        final var accountStore = transferContext.getHandleContext().writableStore(WritableAccountStore.class);
        final Map<AccountID, Long> netHbarTransfers = new HashMap<>();
        final Map<AccountID, Long> allowanceTransfers = new HashMap<>();
        for (var aa : op.transfersOrElse(TransferList.DEFAULT).accountAmounts()) {
            addOrUpdateAggregatedBalances(netHbarTransfers, aa);
            if (aa.isApproval() && aa.amount() < 0) {
                addOrUpdateAllowances(allowanceTransfers, aa);
            }
        }

        modifyAggregatedTransfers(netHbarTransfers, accountStore, transferContext);
        modifyAggregatedAllowances(allowanceTransfers, accountStore, transferContext);
    }


    /**
     * Aggregates all token allowances from the changes that have isApproval flag set in
     * {@link CryptoTransferTransactionBody}.
     * @param allowanceTransfers - map of aggregated token allowances to be modified
     * @param aa - account amount
     */
    private void addOrUpdateAllowances(final Map<AccountID, Long> allowanceTransfers,
                                       final AccountAmount aa) {
        if (!allowanceTransfers.containsKey(aa.accountID())) {
            allowanceTransfers.put(aa.accountID(), aa.amount());
        } else {
            final var existingChange = allowanceTransfers.get(aa.accountID());
            allowanceTransfers.put(aa.accountID(), existingChange + aa.amount());
        }
    }

    /**
     * Modifies the aggregated token balances for all the changes
     * @param netHbarTransfers - map of aggregated hbar balances to be modified
     * @param aa - account amount
     */
    private void addOrUpdateAggregatedBalances(final Map<AccountID, Long> netHbarTransfers,
                                               final AccountAmount aa) {
        if (!netHbarTransfers.containsKey(aa.accountID())) {
            netHbarTransfers.put(aa.accountID(), aa.amount());
        } else {
            final var existingChange = netHbarTransfers.get(aa.accountID());
            netHbarTransfers.put(aa.accountID(), existingChange + aa.amount());
        }

    }

    /**
     * Puts all the aggregated token allowances changes into the accountStore.
     * @param allowanceTransfers - map of aggregated token allowances to be put into state
     * @param accountStore  - account store
     * @param transferContext - transfer context
     */
    private void modifyAggregatedAllowances(final Map<AccountID, Long> allowanceTransfers,
                                            final WritableAccountStore accountStore,
                                            final TransferContext transferContext) {
        for (final var accountId : allowanceTransfers.keySet()) {
            final var account = getIfUsable(
                    accountId, accountStore, transferContext.getHandleContext().expiryValidator(), INVALID_ACCOUNT_ID);
            final var accountCopy = account.copyBuilder();

            final var cryptoAllowances = account.cryptoAllowancesOrElse(Collections.emptyList());
            for (int i = 0; i < cryptoAllowances.size(); i++) {
                final var allowance = cryptoAllowances.get(i);
                final var allowanceCopy = allowance.copyBuilder();
                if (allowance.spenderNum() == accountId.accountNum()) {
                    final var newAllowance = allowance.amount() + allowanceTransfers.get(account);
                    validateTrue(newAllowance >= 0, AMOUNT_EXCEEDS_ALLOWANCE);

                    allowanceCopy.amount(newAllowance);
                    if (newAllowance != 0) {
                        cryptoAllowances.set(i, allowanceCopy.build());
                    } else {
                        cryptoAllowances.remove(i);
                    }
                    break;
                } else if (i == cryptoAllowances.size() - 1) {
                    throw new HandleException(SPENDER_DOES_NOT_HAVE_ALLOWANCE);
                }
            }
            accountCopy.cryptoAllowances(cryptoAllowances);
            accountStore.put(accountCopy.build());
        }
    }


    /**
     * Puts all the aggregated hbar balances changes into the accountStore.
     * @param netHbarTransfers - map of aggregated hbar balances to be put into state
     * @param accountStore - account store
     * @param transferContext - transfer context
     */
    private void modifyAggregatedTransfers(final Map<AccountID, Long> netHbarTransfers,
                                           final WritableAccountStore accountStore,
                                           final TransferContext transferContext) {
        for (final var accountId : netHbarTransfers.keySet()) {
            final var account = getIfUsable(
                    accountId, accountStore, transferContext.getHandleContext().expiryValidator(), INVALID_ACCOUNT_ID);
            final var currentBalance = account.tinybarBalance();
            final var newBalance = currentBalance + netHbarTransfers.get(account);
            validateTrue(newBalance >= 0, INSUFFICIENT_ACCOUNT_BALANCE);
            final var copy = account.copyBuilder();
            accountStore.put(copy.tinybarBalance(newBalance).build());
        }
    }
}
