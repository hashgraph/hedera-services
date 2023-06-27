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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ApproveHbarDebitStep implements TransferStep {
    final CryptoTransferTransactionBody op;

    public ApproveHbarDebitStep(final CryptoTransferTransactionBody op) {
        this.op = op;
    }

    @Override
    public void doIn(final TransferContext transferContext) {
        final var accountStore = transferContext.getHandleContext().writableStore(WritableAccountStore.class);
        final Map<AccountID, Long> allowanceTransfers = new HashMap<>();
        for (var aa : op.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(Collections.emptyList())) {
            if (aa.isApproval() && aa.amount() < 0) {
                if (!allowanceTransfers.containsKey(aa.accountID())) {
                    allowanceTransfers.put(aa.accountID(), aa.amount());
                } else {
                    var existingChange = allowanceTransfers.get(aa.accountID());
                    allowanceTransfers.put(aa.accountID(), existingChange + aa.amount());
                }
            }
        }

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
                    allowanceCopy.amount(newAllowance);
                    if (newAllowance != 0) {
                        cryptoAllowances.set(i, allowanceCopy.build());
                    } else {
                        cryptoAllowances.remove(i);
                    }
                }
            }
            accountCopy.cryptoAllowances(cryptoAllowances);
            accountStore.put(accountCopy.build());
        }
    }
}
