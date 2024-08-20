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

import com.hedera.hapi.block.stream.output.CallContractOutput;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.state.SingleTransactionRecord;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class ContractCallTranslator implements TransactionRecordTranslator<SingleTransactionBlockItems> {
    private static final Logger logger = LogManager.getLogger(ContractCallTranslator.class);

    @Override
    public SingleTransactionRecord translate(
            @NotNull SingleTransactionBlockItems transaction, @Nullable StateChanges stateChanges) {
        final var recordBuilder = TransactionRecord.newBuilder();
        final var txnOutput = transaction.output();
        var contractCallOutput = CallContractOutput.DEFAULT;

        if (txnOutput != null && txnOutput.hasContractCall()) {
            contractCallOutput = txnOutput.contractCall();
            final var contractCallResult = contractCallOutput.contractCallResult();
            recordBuilder.contractCallResult(contractCallResult);
        } else {
            logger.info("Was not able to translate ContractCall operation");
        }

        return new SingleTransactionRecord(
                transaction.txn(),
                recordBuilder.build(),
                txnOutput != null ? contractCallOutput.sidecars() : List.of(),
                new SingleTransactionRecord.TransactionOutputs(null));
    }
}
