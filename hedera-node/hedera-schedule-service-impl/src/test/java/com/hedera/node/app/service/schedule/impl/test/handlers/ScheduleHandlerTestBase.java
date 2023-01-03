/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.schedule.impl.test.handlers;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.PreHandleDispatcher;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadata;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.state.State;
import com.hedera.node.app.spi.state.States;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ScheduleHandlerTestBase {
    protected Key key =
            Key.newBuilder()
                    .setEd25519(ByteString.copyFromUtf8("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                    .build();
    protected HederaKey adminKey = asHederaKey(key).get();
    protected AccountID scheduler = AccountID.newBuilder().setAccountNum(1001L).build();
    protected AccountID payer = AccountID.newBuilder().setAccountNum(2001L).build();
    protected TransactionBody txn;
    protected TransactionBody scheduledTxn;
    protected SigTransactionMetadata scheduledMeta;

    @Mock protected AccountKeyLookup keyLookup;
    @Mock protected HederaKey schedulerKey;
    @Mock protected PreHandleDispatcher dispatcher;
    @Mock protected States states;
    @Mock protected State schedulesById;

    protected ReadableScheduleStore scheduleStore;

    @BeforeEach
    void setUp() {
        given(states.get("SCHEDULES_BY_ID")).willReturn(schedulesById);
        scheduleStore = new ReadableScheduleStore(states);
    }

    protected void basicMetaAssertions(
            final TransactionMetadata meta,
            final int nonPayerKeysSize,
            final boolean failed,
            final ResponseCodeEnum failureStatus) {
        assertEquals(nonPayerKeysSize, meta.requiredNonPayerKeys().size());
        assertTrue(failed ? meta.failed() : !meta.failed());
        assertEquals(failureStatus, meta.status());
    }

    protected TransactionBody scheduleTxnNotRecognized() {
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder().setAccountID(scheduler))
                .setScheduleCreate(
                        ScheduleCreateTransactionBody.newBuilder()
                                .setScheduledTransactionBody(
                                        SchedulableTransactionBody.newBuilder().build()))
                .build();
    }
}
