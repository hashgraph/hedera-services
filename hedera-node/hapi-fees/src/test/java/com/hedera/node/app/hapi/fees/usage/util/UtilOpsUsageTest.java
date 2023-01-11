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
package com.hedera.node.app.hapi.fees.usage.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.node.app.hapi.fees.usage.SigUsage;
import com.hedera.node.app.hapi.fees.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.UtilPrngTransactionBody;
import org.junit.jupiter.api.Test;

class UtilOpsUsageTest {
    private static final long now = 1_234_567L;
    private final UtilOpsUsage subject = new UtilOpsUsage();

    @Test
    void estimatesAutoRenewAsExpected() {
        final var op = UtilPrngTransactionBody.newBuilder().setRange(10).build();
        final var txn =
                TransactionBody.newBuilder()
                        .setTransactionID(
                                TransactionID.newBuilder()
                                        .setTransactionValidStart(
                                                Timestamp.newBuilder().setSeconds(now)))
                        .setUtilPrng(op)
                        .build();

        final ByteString canonicalSig =
                ByteString.copyFromUtf8(
                        "0123456789012345678901234567890123456789012345678901234567890123");
        final SignatureMap onePairSigMap =
                SignatureMap.newBuilder()
                        .addSigPair(
                                SignaturePair.newBuilder()
                                        .setPubKeyPrefix(ByteString.copyFromUtf8("a"))
                                        .setEd25519(canonicalSig))
                        .build();
        final SigUsage singleSigUsage = new SigUsage(1, onePairSigMap.getSerializedSize(), 1);
        final var opMeta = new UtilPrngMeta(txn.getUtilPrng());
        final var baseMeta = new BaseTransactionMeta(0, 0);

        final var actual = new UsageAccumulator();
        final var expected = new UsageAccumulator();

        expected.resetForTransaction(baseMeta, singleSigUsage);
        expected.addBpt(4);

        subject.prngUsage(singleSigUsage, baseMeta, opMeta, actual);

        assertEquals(expected, actual);
    }
}
