/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow.dispatch;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.AppKeyVerifier;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.dispatch.ChildDispatch;
import com.hedera.node.app.workflows.handle.DispatchProcessor;
import com.hedera.node.app.workflows.handle.dispatch.ChildDispatchFactory;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChildDispatchTest {
    private static final Fees FEES = new Fees(1L, 2L, 3L);
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);
    private static final AccountID PAYER_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(666).build();
    private static final TransactionBody TXN_BODY = TransactionBody.newBuilder()
            .transactionID(
                    TransactionID.newBuilder().accountID(PAYER_ACCOUNT_ID).build())
            .build();
    private static final TransactionInfo CRYPTO_TRANSFER_TXN_INFO =
            new TransactionInfo(Transaction.DEFAULT, TXN_BODY, SignatureMap.DEFAULT, Bytes.EMPTY, CRYPTO_TRANSFER);
    private static final PreHandleResult PRE_HANDLE_RESULT = new PreHandleResult(
            AccountID.DEFAULT,
            Key.DEFAULT,
            SO_FAR_SO_GOOD,
            SUCCESS,
            CRYPTO_TRANSFER_TXN_INFO,
            Set.of(Key.DEFAULT),
            Collections.emptySet(),
            Set.of(Account.DEFAULT),
            Collections.emptyMap(),
            null,
            1L);
    private static HandleContext.TransactionCategory CATEGORY = CHILD;

    @Mock
    private NodeInfo creatorInfo;

    @Mock
    private AppKeyVerifier keyVerifier;

    @Mock
    private SavepointStackImpl stack;

    @Mock
    private RecordListBuilder recordListBuilder;

    @Mock
    private SingleTransactionRecordBuilderImpl recordBuilder;

    @Mock
    private PlatformState platformState;

    @Mock
    private Configuration config;

    @Mock
    private HederaFunctionality topLevelFunction;

    @Mock
    private Authorizer authorizer;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private FeeManager feeManager;

    @Mock
    private RecordCache recordCache;

    @Mock
    private DispatchProcessor dispatchProcessor;

    @Mock
    private BlockRecordManager blockRecordManager;

    @Mock
    private ServiceScopeLookup serviceScopeLookup;

    @Mock
    private StoreMetricsService storeMetricsService;

    @Mock
    private ExchangeRateManager exchangeRateManager;

    @Mock
    private ChildDispatchFactory childDispatchFactory;

    @Mock
    private TransactionDispatcher dispatcher;

    @Mock
    private NetworkUtilizationManager networkUtilizationManager;

    private ChildDispatch subject;

    @Test
    void constructsExpectedDispatch() {
        subject = ChildDispatch.from(
                recordBuilder,
                CRYPTO_TRANSFER_TXN_INFO,
                PAYER_ACCOUNT_ID,
                CATEGORY,
                stack,
                PRE_HANDLE_RESULT,
                keyVerifier,
                CONSENSUS_NOW,
                creatorInfo,
                DEFAULT_CONFIG,
                platformState,
                recordListBuilder,
                CRYPTO_TRANSFER,
                authorizer,
                networkInfo,
                feeManager,
                recordCache,
                dispatchProcessor,
                blockRecordManager,
                serviceScopeLookup,
                storeMetricsService,
                exchangeRateManager,
                childDispatchFactory,
                dispatcher,
                networkUtilizationManager);
    }
}
