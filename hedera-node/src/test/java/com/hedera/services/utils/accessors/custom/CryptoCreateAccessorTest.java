/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils.accessors.custom;

import static com.hedera.services.utils.accessors.SignedTxnAccessorTest.buildTransactionFrom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willCallRealMethod;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.utils.KeyUtils;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CryptoCreateAccessorTest {
    private static final Key adminKey = KeyUtils.A_THRESHOLD_KEY;
    private static final long autoRenewPeriod = 1_234_567L;
    private static final long now = 1_234_567L;
    private static final String memo = "Eternal sunshine of the spotless mind";

    @Mock private AccessorFactory accessorFactory;

    @Test
    void setCryptoCreateUsageMetaWorks() {
        final var txn = signedCryptoCreateTxn();
        final var accessor = getAccessor(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getCryptoCreateMeta(accessor);

        assertEquals(137, expandedMeta.getBaseSize());
        assertEquals(autoRenewPeriod, expandedMeta.getLifeTime());
        assertEquals(10, expandedMeta.getMaxAutomaticAssociations());
    }

    private SignedTxnAccessor getAccessor(final Transaction txn) {
        try {
            willCallRealMethod().given(accessorFactory).constructSpecializedAccessor(any());
            return accessorFactory.constructSpecializedAccessor(txn.toByteArray());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
        }
    }

    private Transaction signedCryptoCreateTxn() {
        return buildTransactionFrom(cryptoCreateOp());
    }

    private TransactionBody cryptoCreateOp() {
        final var op =
                CryptoCreateTransactionBody.newBuilder()
                        .setMemo(memo)
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
                        .setKey(adminKey)
                        .setMaxAutomaticTokenAssociations(10);
        return TransactionBody.newBuilder()
                .setTransactionID(
                        TransactionID.newBuilder()
                                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setCryptoCreateAccount(op)
                .build();
    }
}
