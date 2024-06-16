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

package com.hedera.node.app.workflows.handle.flow.dispatch.user.modules;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.FeeAccumulatorImpl;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.signature.DefaultKeyVerifier;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ServiceApiFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDispatchModuleTest {
    public static final Configuration DEFAULT_CONFIG = HederaTestConfigBuilder.createConfig();
    private static final AccountID PAYER_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(1_234).build();
    private static final TransactionBody TXN_BODY = TransactionBody.newBuilder()
            .transactionID(
                    TransactionID.newBuilder().accountID(PAYER_ACCOUNT_ID).build())
            .build();
    private static final TransactionInfo CRYPTO_TRANSFER_TXN_INFO = new TransactionInfo(
            Transaction.DEFAULT,
            TXN_BODY,
            SignatureMap.newBuilder()
                    .sigPair(SignaturePair.DEFAULT, SignaturePair.DEFAULT)
                    .build(),
            Bytes.EMPTY,
            CRYPTO_TRANSFER);
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

    @Mock
    private FeeContext feeContext;

    @Mock
    private TransactionDispatcher dispatcher;

    @Mock
    private SavepointStackImpl stack;

    @Mock
    private StoreMetricsService storeMetricsService;

    @Mock
    private ServiceApiFactory serviceApiFactory;

    @Mock
    private SingleTransactionRecordBuilderImpl recordBuilder;

    @Mock
    private TokenServiceApi tokenServiceApi;

    @Test
    void providesRequiredKeys() {
        assertThat(UserDispatchModule.provideRequiredKeys(PRE_HANDLE_RESULT))
                .isSameAs(PRE_HANDLE_RESULT.getRequiredKeys());
    }

    @Test
    void providesRequiredHollowAccounts() {
        assertThat(UserDispatchModule.provideHollowAccounts(PRE_HANDLE_RESULT))
                .isSameAs(PRE_HANDLE_RESULT.getHollowAccounts());
    }

    @Test
    void syntheticPayerIsTxnInfoPayer() {
        assertThat(UserDispatchModule.provideSyntheticPayer(CRYPTO_TRANSFER_TXN_INFO))
                .isEqualTo(PAYER_ACCOUNT_ID);
    }

    @Test
    void providesDefaultKeyVerifier() {
        final var keyVerifier = UserDispatchModule.provideKeyVerifier(
                DEFAULT_CONFIG.getConfigData(HederaConfig.class), CRYPTO_TRANSFER_TXN_INFO, PRE_HANDLE_RESULT);
        assertThat(keyVerifier).isInstanceOf(DefaultKeyVerifier.class);
        assertThat(keyVerifier.numSignaturesVerified()).isEqualTo(2);
    }

    @Test
    void providesFeesViaDispatch() {
        given(dispatcher.dispatchComputeFees(feeContext)).willReturn(Fees.FREE);
        final var fees = UserDispatchModule.provideFees(feeContext, dispatcher);
        assertThat(fees).isSameAs(Fees.FREE);
    }

    @Test
    void providesServiceApiFactory() {
        assertThat(UserDispatchModule.provideServiceApiFactory(stack, DEFAULT_CONFIG, storeMetricsService))
                .isNotNull();
    }

    @Test
    void providesFeeAccumulatorImpl() {
        given(serviceApiFactory.getApi(TokenServiceApi.class)).willReturn(tokenServiceApi);
        assertThat(UserDispatchModule.provideFeeAccumulator(recordBuilder, serviceApiFactory))
                .isInstanceOf(FeeAccumulatorImpl.class);
    }
}
