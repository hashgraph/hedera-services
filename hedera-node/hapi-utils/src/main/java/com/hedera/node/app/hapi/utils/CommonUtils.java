/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.toPbj;
import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.hapi.util.UnknownHederaFunctionality;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionOrBuilder;
import com.swirlds.common.crypto.DigestType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
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

    /**
     * Extracts the {@link TransactionBody} from a {@link TransactionOrBuilder} and throws an unchecked exception if
     * the extraction fails.
     *
     * @param transaction the {@link TransactionOrBuilder} from which to extract the {@link TransactionBody}
     * @return the extracted {@link TransactionBody}
     */
    public static TransactionBody extractTransactionBodyUnchecked(final TransactionOrBuilder transaction) {
        try {
            return TransactionBody.parseFrom(extractTransactionBodyByteString(transaction));
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException(e);
        }
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

    /**
     * Returns a {@link MessageDigest} instance for the SHA-384 algorithm, throwing an unchecked exception if the
     * algorithm is not found.
     * @return a {@link MessageDigest} instance for the SHA-384 algorithm
     */
    public static MessageDigest sha384DigestOrThrow() {
        try {
            return MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
        } catch (final NoSuchAlgorithmException fatal) {
            throw new IllegalStateException(fatal);
        }
    }

    public static Bytes noThrowSha384HashOf(final Bytes bytes) {
        return Bytes.wrap(noThrowSha384HashOf(bytes.toByteArray()));
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

    /**
     * check TransactionBody and return the HederaFunctionality. This method was moved from MiscUtils.
     * NODE_STAKE_UPDATE is not checked in this method, since it is not a user transaction.
     *
     * @param txn the {@code TransactionBody}
     * @return one of HederaFunctionality
     */
    @NonNull
    public static HederaFunctionality functionOf(@NonNull final TransactionBody txn) throws UnknownHederaFunctionality {
        requireNonNull(txn);
        return fromPbj(HapiUtils.functionOf(toPbj(txn)));
    }

    /**
     * get the EVM address from the long number.
     *
     * @param num the input long number
     * @return evm address
     */
    public static byte[] asEvmAddress(final long num) {
        final byte[] evmAddress = new byte[20];
        arraycopy(Longs.toByteArray(num), 0, evmAddress, 12, 8);
        return evmAddress;
    }

    public static Instant timestampToInstant(final Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    public static Instant pbjTimestampToInstant(final com.hedera.hapi.node.base.Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.seconds(), timestamp.nanos());
    }
}
