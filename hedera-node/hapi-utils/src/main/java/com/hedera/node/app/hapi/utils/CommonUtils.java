/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.utils;

import static com.hedera.node.app.hapi.utils.ByteStringUtils.unwrapUnsafelyIfPossible;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionOrBuilder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class CommonUtils {
    private CommonUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    private static String sha384HashTag = "SHA-384";

    public static String base64encode(final byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static ByteString extractTransactionBodyByteString(final TransactionOrBuilder transaction)
            throws InvalidProtocolBufferException {
        final var signedTransactionBytes = transaction.getSignedTransactionBytes();
        if (!signedTransactionBytes.isEmpty()) {
            return SignedTransaction.parseFrom(signedTransactionBytes).getBodyBytes();
        }
        return transaction.getBodyBytes();
    }

    public static byte[] extractTransactionBodyBytes(final TransactionOrBuilder transaction)
            throws InvalidProtocolBufferException {
        return unwrapUnsafelyIfPossible(extractTransactionBodyByteString(transaction));
    }

    public static TransactionBody extractTransactionBody(final TransactionOrBuilder transaction)
            throws InvalidProtocolBufferException {
        return TransactionBody.parseFrom(extractTransactionBodyByteString(transaction));
    }

    public static SignatureMap extractSignatureMap(final TransactionOrBuilder transaction)
            throws InvalidProtocolBufferException {
        final var signedTransactionBytes = transaction.getSignedTransactionBytes();
        if (!signedTransactionBytes.isEmpty()) {
            return SignedTransaction.parseFrom(signedTransactionBytes).getSigMap();
        }
        return transaction.getSigMap();
    }

    public static byte[] noThrowSha384HashOf(final byte[] byteArray) {
        try {
            return MessageDigest.getInstance(sha384HashTag).digest(byteArray);
        } catch (final NoSuchAlgorithmException fatal) {
            throw new IllegalStateException(fatal);
        }
    }

    public static boolean productWouldOverflow(final long multiplier, final long multiplicand) {
        if (multiplicand == 0) {
            return false;
        }
        final var maxMultiplier = Long.MAX_VALUE / multiplicand;
        return multiplier > maxMultiplier;
    }

    @VisibleForTesting
    static void setSha384HashTag(final String sha384HashTag) {
        CommonUtils.sha384HashTag = sha384HashTag;
    }
}
