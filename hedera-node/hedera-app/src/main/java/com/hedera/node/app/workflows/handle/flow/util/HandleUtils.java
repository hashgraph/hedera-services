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

package com.hedera.node.app.workflows.handle.flow.util;

import static com.hedera.hapi.node.base.HederaFunctionality.NONE;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

public class HandleUtils {
    public static TransactionBaseData extractTransactionBaseData(@NonNull final Bytes content) {
        // This method is only called if something fatal happened. We do a best effort approach to extract the
        // type of the transaction, the TransactionBody and the payer if not known.
        if (content.length() == 0) {
            return new TransactionBaseData(NONE, Bytes.EMPTY, null, null, null);
        }

        HederaFunctionality function = NONE;
        Bytes transactionBytes = content;
        Transaction transaction = null;
        TransactionBody txBody = null;
        AccountID payer = null;
        try {
            transaction = Transaction.PROTOBUF.parseStrict(transactionBytes);

            final Bytes bodyBytes;
            if (transaction.signedTransactionBytes().length() > 0) {
                final var signedTransaction = SignedTransaction.PROTOBUF.parseStrict(
                        transaction.signedTransactionBytes().toReadableSequentialData());
                bodyBytes = signedTransaction.bodyBytes();
                transactionBytes = bodyBytes;
            } else {
                bodyBytes = transaction.bodyBytes();
            }
            txBody = TransactionBody.PROTOBUF.parseStrict(bodyBytes.toReadableSequentialData());

            payer = txBody.transactionIDOrElse(TransactionID.DEFAULT).accountID();

            function = HapiUtils.functionOf(txBody);
        } catch (Exception ex) {
            // ignore
        }
        return new TransactionBaseData(function, transactionBytes, transaction, txBody, payer);
    }
}
