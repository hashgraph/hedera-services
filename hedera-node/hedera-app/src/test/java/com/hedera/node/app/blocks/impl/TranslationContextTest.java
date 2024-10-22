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

package com.hedera.node.app.blocks.impl;

import static com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandler.noThrowSha384HashOf;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.node.app.blocks.impl.contexts.BaseOpContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.Test;

class TranslationContextTest {
    @Test
    void hashIsOfSignedTransactionBytesIfSet() {
        final var signedTransactionBytes = SignedTransaction.PROTOBUF.toBytes(
                SignedTransaction.newBuilder().bodyBytes(Bytes.fromHex("0123")).build());

        final var subject = new BaseOpContext(
                "",
                ExchangeRateSet.DEFAULT,
                TransactionID.DEFAULT,
                Transaction.newBuilder()
                        .signedTransactionBytes(signedTransactionBytes)
                        .build(),
                HederaFunctionality.NONE);

        assertEquals(Bytes.wrap(noThrowSha384HashOf(signedTransactionBytes.toByteArray())), subject.transactionHash());
    }

    @Test
    void hashIsOfSerializedTransactionIfMissingSignedTransactionBytes() {
        final var transactionBytes = Transaction.PROTOBUF.toBytes(Transaction.DEFAULT);

        final var subject = new BaseOpContext(
                "", ExchangeRateSet.DEFAULT, TransactionID.DEFAULT, Transaction.DEFAULT, HederaFunctionality.NONE);

        assertEquals(Bytes.wrap(noThrowSha384HashOf(transactionBytes.toByteArray())), subject.transactionHash());
    }
}
