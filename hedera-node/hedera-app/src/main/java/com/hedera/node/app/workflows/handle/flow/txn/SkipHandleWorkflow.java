/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow.txn;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.workflows.TransactionInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SkipHandleWorkflow {
    private final ExchangeRateManager exchangeRateManager;

    @Inject
    public SkipHandleWorkflow(@NonNull final ExchangeRateManager exchangeRateManager) {
        this.exchangeRateManager = exchangeRateManager;
    }

    public void execute(UserTransactionComponent userTxn) {
        final TransactionInfo transactionInfo = userTxn.txnInfo();
        // Initialize record builder list and place a BUSY record in the cache
        userTxn.recordListBuilder()
                .userTransactionRecordBuilder()
                .transaction(transactionInfo.transaction())
                .transactionBytes(transactionInfo.signedBytes())
                .transactionID(transactionInfo.transactionID())
                .exchangeRate(exchangeRateManager.exchangeRates())
                .memo(transactionInfo.txBody().memo())
                .status(ResponseCodeEnum.BUSY);
    }
}
