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

package com.hedera.services.bdd.junit.support.translators;

import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.state.SingleTransactionRecord;
import org.jetbrains.annotations.NotNull;

public class ContractCreateTranslator implements TransactionRecordTranslator<SingleTransactionBlockItems> {
    @Override
    public SingleTransactionRecord translate(
            @NotNull SingleTransactionBlockItems transaction, @NotNull StateChanges stateChanges) {

        final var receiptBuilder = TransactionReceipt.newBuilder();
        final var recordBuilder = TransactionRecord.newBuilder();

        final var txnOutput = transaction.output();
        if (txnOutput != null && txnOutput.hasContractCreate()) {
            final var contractCreateResult = txnOutput.contractCreate().contractCreateResult();
            receiptBuilder.contractID(contractCreateResult.contractID());
            recordBuilder
                    .receipt(receiptBuilder.build())
                    .contractCreateResult(contractCreateResult)
                    .evmAddress(contractCreateResult.evmAddress());
        }

        return new SingleTransactionRecord(
                transaction.txn(),
                recordBuilder.build(),
                txnOutput.contractCreate().sidecars(),
                new SingleTransactionRecord.TransactionOutputs(null));
    }
}
