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
import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.PreHandleDispatcher;
import com.hedera.node.app.spi.fixtures.TestBase;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadata;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.state.ReadableStates;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.nio.charset.StandardCharsets;

@ExtendWith(MockitoExtension.class)
class ScheduleHandlerTestBase extends TestBase {
    protected Key key = ed25519("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    protected HederaKey adminKey = asHederaKey(key).get();
    protected AccountID scheduler = AccountID.newBuilder().accountNum(1001L).build();
    protected AccountID payer = AccountID.newBuilder().accountNum(2001L).build();
    protected TransactionBody scheduledTxn;
    protected SigTransactionMetadata scheduledMeta;

    @Mock protected TransactionMetadata metaToHandle;
    @Mock protected AccountKeyLookup keyLookup;
    @Mock protected HederaKey schedulerKey;
    @Mock protected PreHandleDispatcher dispatcher;
    @Mock protected ReadableStates states;

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
                .transactionID(TransactionID.newBuilder().accountID(scheduler).build())
//                .scheduleCreate(
//                        ScheduleCreateTransactionBody.newBuilder()
//                                .setScheduledTransactionBody(
//                                        SchedulableTransactionBody.newBuilder().build()))
                .build();
    }

}
