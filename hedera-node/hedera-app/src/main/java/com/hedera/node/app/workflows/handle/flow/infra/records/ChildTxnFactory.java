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

import static com.hedera.hapi.util.HapiUtils.functionOf;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.util.UnknownHederaFunctionality;
import com.hedera.node.app.workflows.TransactionInfo;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ChildTxnFactory {

    @Inject
    public ChildTxnFactory() {}

    public TransactionInfo getTxnInfoFrom(TransactionBody txBody) {
        final var bodyBytes = TransactionBody.PROTOBUF.toBytes(txBody);
        final var signedTransaction =
                SignedTransaction.newBuilder().bodyBytes(bodyBytes).build();
        final var signedTransactionBytes = SignedTransaction.PROTOBUF.toBytes(signedTransaction);
        final var transaction = Transaction.newBuilder()
                .signedTransactionBytes(signedTransactionBytes)
                .build();

        return new TransactionInfo(
                transaction, txBody, SignatureMap.DEFAULT, signedTransactionBytes, functionOfTxn(txBody));
    }

    private static HederaFunctionality functionOfTxn(final TransactionBody txBody) {
        try {
            return functionOf(txBody);
        } catch (final UnknownHederaFunctionality e) {
            throw new IllegalArgumentException("Unknown Hedera Functionality", e);
        }
    }
}
