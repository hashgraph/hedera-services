/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.fixtures;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

/**
 * A utility for making various types of test transactions available to test classes. Rather than
 * extending from some base class, just implement this interface with your test class to get
 * access to these goodies.
 */
public interface TransactionFactory {

    default Transaction simpleCryptoTransfer() {
        return simpleCryptoTransfer(TransactionID.newBuilder().build());
    }

    default Transaction simpleCryptoTransferWithNonce(final TransactionID txnId, final int nonce) {
        return simpleCryptoTransfer(TransactionID.newBuilder()
                .accountID(txnId.accountID())
                .transactionValidStart(txnId.transactionValidStart())
                .nonce(nonce)
                .build());
    }

    default Transaction simpleCryptoTransfer(@NonNull final TransactionID transactionID) {
        final var cryptoTransferTx = CryptoTransferTransactionBody.newBuilder().build();

        final var txBody = TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoTransfer(cryptoTransferTx)
                .build();

        final var signedTx = SignedTransaction.newBuilder()
                .bodyBytes(asBytes(TransactionBody.PROTOBUF, txBody))
                .build();

        return Transaction.newBuilder()
                .signedTransactionBytes(asBytes(SignedTransaction.PROTOBUF, signedTx))
                .build();
    }

    default byte[] asByteArray(@NonNull final Transaction tx) {
        return asByteArray(Transaction.PROTOBUF, tx);
    }

    default <R extends Record> byte[] asByteArray(@NonNull final Codec<R> codec, @NonNull final R r) {
        try {
            final var byteStream = new ByteArrayOutputStream();
            codec.write(r, new WritableStreamingData(byteStream));
            return byteStream.toByteArray();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    default <R extends Record> Bytes asBytes(@NonNull final Codec<R> codec, @NonNull final R r) {
        return Bytes.wrap(asByteArray(codec, r));
    }

    default AccountID asAccount(@NonNull final String id) {
        final var parts = id.split("\\.");
        return AccountID.newBuilder()
                .shardNum(Long.parseLong(parts[0]))
                .realmNum(Long.parseLong(parts[1]))
                .accountNum(Long.parseLong(parts[2]))
                .build();
    }

    default ContractID asContract(String id) {
        final var parts = id.split("\\.");
        return ContractID.newBuilder()
                .shardNum(Long.parseLong(parts[0]))
                .realmNum(Long.parseLong(parts[1]))
                .contractNum(Long.parseLong(parts[2]))
                .build();
    }

    default Timestamp asTimestamp(final Instant when) {
        return Timestamp.newBuilder()
                .seconds(when.getEpochSecond())
                .nanos(when.getNano())
                .build();
    }
}
