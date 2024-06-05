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

package com.hedera.node.app.workflows.handle.flow.infra.records;

import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ChildRecordInitializer {

    @Inject
    public ChildRecordInitializer() {}

    public void initializeUserRecord(SingleTransactionRecordBuilderImpl recordBuilder, TransactionInfo txnInfo) {
        recordBuilder
                .transaction(txnInfo.transaction())
                .transactionBytes(txnInfo.signedBytes())
                .memo(txnInfo.txBody().memo());
        // Set the transactionId if provided
        final var transactionID = txnInfo.txBody().transactionID();
        if (transactionID != null) {
            recordBuilder.transactionID(transactionID);
        }
    }
}
