/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.legacy.proto.utils;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionOrBuilder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** Common utilities. */
public final class CommonUtils {
    private CommonUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Encode bytes as base64.
     *
     * @param bytes to be encoded
     * @return base64 string
     */
    public static String base64encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static ByteString extractTransactionBodyByteString(TransactionOrBuilder transaction)
            throws InvalidProtocolBufferException {
        ByteString signedTransactionBytes = transaction.getSignedTransactionBytes();
        if (!signedTransactionBytes.isEmpty()) {
            return SignedTransaction.parseFrom(signedTransactionBytes).getBodyBytes();
        }

        return transaction.getBodyBytes();
    }

    public static byte[] extractTransactionBodyBytes(TransactionOrBuilder transaction)
            throws InvalidProtocolBufferException {
        return extractTransactionBodyByteString(transaction).toByteArray();
    }

    public static TransactionBody extractTransactionBody(TransactionOrBuilder transaction)
            throws InvalidProtocolBufferException {
        return TransactionBody.parseFrom(extractTransactionBodyByteString(transaction));
    }

    public static SignatureMap extractSignatureMap(TransactionOrBuilder transaction)
            throws InvalidProtocolBufferException {
        ByteString signedTransactionBytes = transaction.getSignedTransactionBytes();
        if (!signedTransactionBytes.isEmpty()) {
            return SignedTransaction.parseFrom(signedTransactionBytes).getSigMap();
        }

        return transaction.getSigMap();
    }

    public static Transaction.Builder toTransactionBuilder(
            TransactionOrBuilder transactionOrBuilder) {
        if (transactionOrBuilder instanceof Transaction transaction) {
            return transaction.toBuilder();
        }

        return (Transaction.Builder) transactionOrBuilder;
    }

    public static MessageDigest getSha384Hash() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-384");
    }

    public static byte[] noThrowSha384HashOf(byte[] byteArray) {
        try {
            return getSha384Hash().digest(byteArray);
        } catch (NoSuchAlgorithmException ignoreToReturnEmptyByteArray) {
            return new byte[0];
        }
    }

    public static boolean productWouldOverflow(final long multiplier, final long multiplicand) {
        if (multiplicand == 0) {
            return false;
        }
        final var maxMultiplier = Long.MAX_VALUE / multiplicand;
        return multiplier > maxMultiplier;
    }
}
