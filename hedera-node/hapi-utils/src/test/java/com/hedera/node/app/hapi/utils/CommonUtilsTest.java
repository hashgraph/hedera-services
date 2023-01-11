/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.node.app.hapi.utils.CommonUtils.productWouldOverflow;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CommonUtilsTest {
    @Test
    void base64EncodesAsExpected() {
        final var someBytes = "abcdefg".getBytes();
        assertEquals(
                Base64.getEncoder().encodeToString(someBytes), CommonUtils.base64encode(someBytes));
    }

    @Test
    void returnsAvailableTransactionBodyBytes() throws InvalidProtocolBufferException {
        final var current =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(
                                SignedTransaction.newBuilder()
                                        .setBodyBytes(NONSENSE)
                                        .build()
                                        .toByteString())
                        .build();
        final var deprecated = Transaction.newBuilder().setBodyBytes(NONSENSE).build();

        assertEquals(NONSENSE, CommonUtils.extractTransactionBodyByteString(current));
        assertEquals(NONSENSE, CommonUtils.extractTransactionBodyByteString(deprecated));
        assertArrayEquals(NONSENSE.toByteArray(), CommonUtils.extractTransactionBodyBytes(current));
    }

    @Test
    void canExtractTransactionBody() throws InvalidProtocolBufferException {
        final var body =
                TransactionBody.newBuilder()
                        .setTransactionID(
                                TransactionID.newBuilder()
                                        .setAccountID(
                                                AccountID.newBuilder().setAccountNum(2L).build()))
                        .build();
        final var current =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(
                                SignedTransaction.newBuilder()
                                        .setBodyBytes(body.toByteString())
                                        .build()
                                        .toByteString())
                        .build();
        assertEquals(body, CommonUtils.extractTransactionBody(current));
    }

    @Test
    void returnsAvailableSigMap() throws InvalidProtocolBufferException {
        final var sigMap =
                SignatureMap.newBuilder()
                        .addSigPair(
                                SignaturePair.newBuilder()
                                        .setPubKeyPrefix(NONSENSE)
                                        .setEd25519(NONSENSE)
                                        .build())
                        .build();
        final var current =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(
                                SignedTransaction.newBuilder()
                                        .setSigMap(sigMap)
                                        .build()
                                        .toByteString())
                        .build();
        final var deprecated = Transaction.newBuilder().setSigMap(sigMap).build();

        assertEquals(sigMap, CommonUtils.extractSignatureMap(current));
        assertEquals(sigMap, CommonUtils.extractSignatureMap(deprecated));
    }

    @Test
    void failsOnUnavailableDigest() {
        final var raw = NONSENSE.toByteArray();
        assertDoesNotThrow(() -> noThrowSha384HashOf(raw));
        CommonUtils.setSha384HashTag("NOPE");
        assertThrows(IllegalStateException.class, () -> noThrowSha384HashOf(raw));
        CommonUtils.setSha384HashTag("SHA-384");
    }

    @Test
    void detectsOverflowInVariousCases() {
        final var nonZeroMultiplicand = 666L;
        final var fineMultiplier = Long.MAX_VALUE / nonZeroMultiplicand;
        final var overflowMultiplier = Long.MAX_VALUE / nonZeroMultiplicand + 1;
        assertFalse(productWouldOverflow(fineMultiplier, nonZeroMultiplicand));
        assertFalse(productWouldOverflow(fineMultiplier, 0));
        assertTrue(productWouldOverflow(overflowMultiplier, nonZeroMultiplicand));
    }

    private static final ByteString NONSENSE = ByteString.copyFromUtf8("NONSENSE");
}
