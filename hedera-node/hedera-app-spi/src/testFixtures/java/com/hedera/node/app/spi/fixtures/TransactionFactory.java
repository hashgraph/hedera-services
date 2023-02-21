package com.hedera.node.app.spi.fixtures;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.Bytes;
import com.hedera.pbj.runtime.io.DataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public interface TransactionFactory {
    default Transaction simpleCryptoTransfer() {
        final var cryptoTransferTx = CryptoTransferTransactionBody.newBuilder()
                .build();

        final var txBody = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().build())
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
            codec.write(r, new DataOutputStream(byteStream));
            return byteStream.toByteArray();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    default Bytes asBytes(@NonNull final Transaction tx) {
        return Bytes.wrap(asByteArray(tx));
    }

    default <R extends Record> Bytes asBytes(@NonNull final Codec<R> codec, @NonNull final R r) {
        return Bytes.wrap(asByteArray(codec, r));
    }
}
