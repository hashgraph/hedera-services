/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.txns.span.ExpandHandleSpan;
import com.hedera.services.txns.span.SpanMapManager;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hedera.services.utils.accessors.SwirldsTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpandHandleSpanTest {
    @Mock private SpanMapManager handleSpanMap;
    @Mock private GlobalDynamicProperties dynamicProperties;

    private final AccessorFactory accessorFactory = new AccessorFactory(dynamicProperties);

    private final byte[] validTxnBytes =
            Transaction.newBuilder()
                    .setBodyBytes(
                            TransactionBody.newBuilder()
                                    .setTransactionID(
                                            TransactionID.newBuilder()
                                                    .setTransactionValidStart(
                                                            Timestamp.newBuilder()
                                                                    .setSeconds(1_234_567L)
                                                                    .build())
                                                    .setAccountID(IdUtils.asAccount("0.0.1234")))
                                    .build()
                                    .toByteString())
                    .build()
                    .toByteArray();
    private final com.swirlds.common.system.transaction.Transaction validTxn =
            new SwirldTransaction(validTxnBytes);
    private final com.swirlds.common.system.transaction.Transaction invalidTxn =
            new SwirldTransaction("NONSENSE".getBytes());

    private ExpandHandleSpan subject;

    @BeforeEach
    void setUp() {
        subject = new ExpandHandleSpan(handleSpanMap, accessorFactory);
    }

    @Test
    void propagatesIpbe() {
        // expect:
        assertThrows(InvalidProtocolBufferException.class, () -> subject.track(invalidTxn));
        assertThrows(InvalidProtocolBufferException.class, () -> subject.accessorFor(invalidTxn));
    }

    @Test
    void expandsOnTracking() throws InvalidProtocolBufferException {
        subject.track(validTxn);

        final SwirldsTxnAccessor accessor = validTxn.getMetadata();
        assertSame(accessor, subject.accessorFor(validTxn));
        assertNull(validTxn.getMetadata());
    }

    @Test
    void reExpandsIfNotCached() throws InvalidProtocolBufferException {
        final var endAccessor = subject.accessorFor(validTxn);

        verify(handleSpanMap).expandSpan(endAccessor.getDelegate());
    }
}
