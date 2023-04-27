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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.util.UtilPrngTransactionBody;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.PreHandleDispatcher;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleHandlerTestBase {
    protected static final Key TEST_KEY = Key.newBuilder()
            .ed25519(Bytes.wrap("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            .build();
    protected Key adminKey = TEST_KEY;
    protected AccountID scheduler = AccountID.newBuilder().accountNum(1001L).build();
    protected AccountID payer = AccountID.newBuilder().accountNum(2001L).build();

    @Mock
    protected Account schedulerAccount;

    @Mock
    protected Account payerAccount;

    @Mock
    protected AccountAccess keyLookup;

    @Mock
    protected Key payerKey;

    @Mock
    protected Key schedulerKey;

    @Mock
    protected PreHandleDispatcher dispatcher;

    @Mock
    protected ReadableStates states;

    protected void basicContextAssertions(final PreHandleContext context, final int nonPayerKeysSize) {
        assertEquals(nonPayerKeysSize, context.requiredNonPayerKeys().size());
    }

    protected TransactionBody scheduleTxnNotRecognized() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(scheduler))
                .scheduleCreate(ScheduleCreateTransactionBody.newBuilder()
                        .scheduledTransactionBody(SchedulableTransactionBody.newBuilder()
                                .utilPrng(UtilPrngTransactionBody.DEFAULT)
                                .build()))
                .build();
    }
}
