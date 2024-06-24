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

package com.hedera.node.app.workflows.handle.flow.txn.modules;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_DELETED;
import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.PRE_HANDLE_FAILURE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.signature.impl.SignatureVerificationFutureImpl;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import com.swirlds.state.HederaState;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UserTxnModuleTest {
    @Mock
    private ConfigProvider configProvider;

    @Mock
    private WorkingStateAccessor workingStateAccessor;

    @Mock
    private HederaState hederaState;

    private static final ConsensusTransaction PLATFORM_TXN = new SwirldTransaction();
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
    private static final Map<Key, SignatureVerificationFuture> VERIFICATION_RESULTS = Map.of(
            Key.DEFAULT,
            new SignatureVerificationFutureImpl(
                    Key.DEFAULT, Bytes.EMPTY, new TransactionSignature(new byte[384], 0, 0, 0, 0, 0, 0)));
    private static final PreHandleResult PRE_HANDLE_RESULT = new PreHandleResult(
            null,
            Key.newBuilder().ed25519(Bytes.EMPTY).build(),
            PRE_HANDLE_FAILURE,
            PAYER_ACCOUNT_DELETED,
            CRYPTO_TRANSFER_TXN_INFO,
            Collections.emptySet(),
            null,
            Collections.emptySet(),
            VERIFICATION_RESULTS,
            null,
            0);
    private static final PreHandleResult NO_KEY_PRE_HANDLE_RESULT = new PreHandleResult(
            null,
            null,
            PRE_HANDLE_FAILURE,
            PAYER_ACCOUNT_DELETED,
            CRYPTO_TRANSFER_TXN_INFO,
            Collections.emptySet(),
            null,
            Collections.emptySet(),
            VERIFICATION_RESULTS,
            null,
            0);

    @Test
    void providesTxnInfoFromPreHandleResult() {
        assertThat(UserTxnModule.provideTransactionInfo(PRE_HANDLE_RESULT)).isSameAs(CRYPTO_TRANSFER_TXN_INFO);
    }

    @Test
    void providesVerificationResultsFromPreHandleResult() {
        assertThat(UserTxnModule.provideKeyVerifications(PRE_HANDLE_RESULT)).isSameAs(VERIFICATION_RESULTS);
    }

    @Test
    void providesTxnSignaturesFromSigMapSize() {
        assertThat(UserTxnModule.provideLegacyFeeCalcNetworkVpt(CRYPTO_TRANSFER_TXN_INFO))
                .isEqualTo(2);
    }

    @Test
    void providesFunctionalityFromTxnInfo() {
        assertThat(UserTxnModule.provideFunctionality(CRYPTO_TRANSFER_TXN_INFO)).isSameAs(CRYPTO_TRANSFER);
    }

    @Test
    void providesDefaultKeyIfPreHandleHasNone() {
        assertThat(UserTxnModule.providePayerKey(NO_KEY_PRE_HANDLE_RESULT)).isSameAs(Key.DEFAULT);
    }

    @Test
    void providesPreHandleKeyIfSet() {
        assertThat(UserTxnModule.providePayerKey(PRE_HANDLE_RESULT)).isSameAs(PRE_HANDLE_RESULT.payerKey());
    }

    @Test
    void getConfigurationFromProvider() {
        final var config = new VersionedConfigImpl(DEFAULT_CONFIG, 1L);

        given(configProvider.getConfiguration()).willReturn(config);

        assertThat(UserTxnModule.provideConfiguration(configProvider)).isSameAs(config);
    }

    @Test
    void getsHederaConfig() {
        assertThat(UserTxnModule.provideHederaConfig(DEFAULT_CONFIG)).isNotNull();
    }

    @Test
    void onlyLastHandledEpochIsGenesis() {
        assertThat(UserTxnModule.provideIsGenesis(Instant.EPOCH)).isTrue();
        assertThat(UserTxnModule.provideIsGenesis(Instant.EPOCH.plusNanos(1L))).isFalse();
    }

    @Test
    void getsStateFromAccessor() {
        given(workingStateAccessor.getHederaState()).willReturn(hederaState);

        assertThat(UserTxnModule.provideHederaState(workingStateAccessor)).isSameAs(hederaState);
    }

    @Test
    void providesStackImpl() {
        assertThat(UserTxnModule.provideSavepointStackImpl(hederaState)).isNotNull();
    }

    @Test
    void providesReadableStoreFactory() {
        assertThat(UserTxnModule.provideReadableStoreFactory(UserTxnModule.provideSavepointStackImpl(hederaState)))
                .isNotNull();
    }
}
