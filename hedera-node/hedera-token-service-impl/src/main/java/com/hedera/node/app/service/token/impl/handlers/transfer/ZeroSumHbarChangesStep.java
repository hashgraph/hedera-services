/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.token.impl.handlers.transfer;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ZeroSumHbarChangesStep implements TransferStep{
    final CryptoTransferTransactionBody op;
    final WritableAccountStore accountStore;
    public ZeroSumHbarChangesStep(final CryptoTransferTransactionBody op,
                                  WritableAccountStore accountStore) {
        this.op = op;
        this.accountStore = accountStore;
    }
    @Override
    public Set<Key> authorizingKeysIn(final TransferContext transferContext) {
        return null;
    }

    @Override
    public void doIn(final TransferContext transferContext) {
        final Map<AccountID, Long> netHbarTransfers = new HashMap<>();
        for (var aa : op.transfers().accountAmounts()) {
            if (!netHbarTransfers.containsKey(aa.accountID())) {
                netHbarTransfers.put(aa.accountID(), aa.amount());
            } else {
                var existingChange = netHbarTransfers.get(aa.accountID());
                netHbarTransfers.put(aa.accountID(), existingChange + aa.amount());
                // TODO: allowance units ?
            }
        }

        for(final var accountId : netHbarTransfers.keySet()) {
            final var account = accountStore.get(accountId);
            final var currentBalance = account.tinybarBalance();
            final var newBalance = currentBalance + netHbarTransfers.get(account);

            account.adjustBalance(account, netHbarTransfers.get(account));
            final var change = netHbarTransfers.get(account);
            if(change != 0) {
                transferContext.netTransfers().put(account, change);
            }
        }


    }
}
