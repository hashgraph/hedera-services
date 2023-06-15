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

package com.hedera.node.app.workflows.dispatcher;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_ABORT;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.hedera.node.app.workflows.dispatcher.MonoTransactionDispatcher.TYPE_NOT_SUPPORTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusDeleteTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusSubmitMessageTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusUpdateTopicTransactionBody;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractDeleteTransactionBody;
import com.hedera.hapi.node.contract.ContractUpdateTransactionBody;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.file.FileAppendTransactionBody;
import com.hedera.hapi.node.file.FileCreateTransactionBody;
import com.hedera.hapi.node.file.FileDeleteTransactionBody;
import com.hedera.hapi.node.file.FileUpdateTransactionBody;
import com.hedera.hapi.node.file.SystemDeleteTransactionBody;
import com.hedera.hapi.node.file.SystemUndeleteTransactionBody;
import com.hedera.hapi.node.freeze.FreezeTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleDeleteTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleSignTransactionBody;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoAddLiveHashTransactionBody;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.token.CryptoDeleteAllowanceTransactionBody;
import com.hedera.hapi.node.token.CryptoDeleteLiveHashTransactionBody;
import com.hedera.hapi.node.token.CryptoDeleteTransactionBody;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.token.TokenAssociateTransactionBody;
import com.hedera.hapi.node.token.TokenBurnTransactionBody;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.token.TokenDeleteTransactionBody;
import com.hedera.hapi.node.token.TokenDissociateTransactionBody;
import com.hedera.hapi.node.token.TokenFeeScheduleUpdateTransactionBody;
import com.hedera.hapi.node.token.TokenFreezeAccountTransactionBody;
import com.hedera.hapi.node.token.TokenGrantKycTransactionBody;
import com.hedera.hapi.node.token.TokenMintTransactionBody;
import com.hedera.hapi.node.token.TokenPauseTransactionBody;
import com.hedera.hapi.node.token.TokenRevokeKycTransactionBody;
import com.hedera.hapi.node.token.TokenUnfreezeAccountTransactionBody;
import com.hedera.hapi.node.token.TokenUnpauseTransactionBody;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.token.TokenWipeAccountTransactionBody;
import com.hedera.hapi.node.transaction.NodeStakeUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionRecord.EntropyOneOfType;
import com.hedera.hapi.node.transaction.UncheckedSubmitBody;
import com.hedera.hapi.node.util.UtilPrngTransactionBody;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusDeleteTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusUpdateTopicHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractCallHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractCreateHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractDeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractSystemDeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractSystemUndeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractUpdateHandler;
import com.hedera.node.app.service.contract.impl.handlers.EtherumTransactionHandler;
import com.hedera.node.app.service.file.impl.handlers.FileAppendHandler;
import com.hedera.node.app.service.file.impl.handlers.FileCreateHandler;
import com.hedera.node.app.service.file.impl.handlers.FileDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemUndeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileUpdateHandler;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.networkadmin.impl.handlers.FreezeHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkUncheckedSubmitHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleCreateHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleSignHandler;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.CryptoAddLiveHashHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoApproveAllowanceHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoCreateHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteAllowanceHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteLiveHashHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoUpdateHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenAccountWipeHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenAssociateToAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenBurnHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenCreateHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenDeleteHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenDissociateFromAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenFeeScheduleUpdateHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenFreezeAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGrantKycToAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenMintHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenPauseHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenRevokeKycFromAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUnfreezeAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUnpauseHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUpdateHandler;
import com.hedera.node.app.service.util.impl.handlers.UtilPrngHandler;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableFreezeStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonoTransactionDispatcherTest {

    @Mock(strictness = LENIENT)
    private HederaState state;

    @Mock(strictness = LENIENT)
    private ReadableAccountStore readableAccountStore;

    @Mock(strictness = LENIENT)
    private ReadableStoreFactory readableStoreFactory;

    @Mock
    private ConsensusCreateTopicHandler consensusCreateTopicHandler;

    @Mock
    private ConsensusUpdateTopicHandler consensusUpdateTopicHandler;

    @Mock
    private ConsensusDeleteTopicHandler consensusDeleteTopicHandler;

    @Mock
    private ConsensusSubmitMessageHandler consensusSubmitMessageHandler;

    @Mock
    private ContractCreateHandler contractCreateHandler;

    @Mock
    private ContractUpdateHandler contractUpdateHandler;

    @Mock
    private ContractCallHandler contractCallHandler;

    @Mock
    private ContractDeleteHandler contractDeleteHandler;

    @Mock
    private ContractSystemDeleteHandler contractSystemDeleteHandler;

    @Mock
    private ContractSystemUndeleteHandler contractSystemUndeleteHandler;

    @Mock
    private EtherumTransactionHandler etherumTransactionHandler;

    @Mock
    private CryptoCreateHandler cryptoCreateHandler;

    @Mock
    private CryptoUpdateHandler cryptoUpdateHandler;

    @Mock
    private CryptoTransferHandler cryptoTransferHandler;

    @Mock
    private CryptoDeleteHandler cryptoDeleteHandler;

    @Mock
    private CryptoApproveAllowanceHandler cryptoApproveAllowanceHandler;

    @Mock
    private CryptoDeleteAllowanceHandler cryptoDeleteAllowanceHandler;

    @Mock
    private CryptoAddLiveHashHandler cryptoAddLiveHashHandler;

    @Mock
    private CryptoDeleteLiveHashHandler cryptoDeleteLiveHashHandler;

    @Mock
    private FileCreateHandler fileCreateHandler;

    @Mock
    private FileUpdateHandler fileUpdateHandler;

    @Mock
    private FileDeleteHandler fileDeleteHandler;

    @Mock
    private FileAppendHandler fileAppendHandler;

    @Mock
    private FileSystemDeleteHandler fileSystemDeleteHandler;

    @Mock
    private FileSystemUndeleteHandler fileSystemUndeleteHandler;

    @Mock
    private FreezeHandler freezeHandler;

    @Mock
    private NetworkUncheckedSubmitHandler networkUncheckedSubmitHandler;

    @Mock
    private ScheduleCreateHandler scheduleCreateHandler;

    @Mock
    private ScheduleSignHandler scheduleSignHandler;

    @Mock
    private ScheduleDeleteHandler scheduleDeleteHandler;

    @Mock
    private TokenCreateHandler tokenCreateHandler;

    @Mock
    private TokenUpdateHandler tokenUpdateHandler;

    @Mock
    private TokenMintHandler tokenMintHandler;

    @Mock
    private TokenBurnHandler tokenBurnHandler;

    @Mock
    private TokenDeleteHandler tokenDeleteHandler;

    @Mock
    private TokenAccountWipeHandler tokenAccountWipeHandler;

    @Mock
    private TokenFreezeAccountHandler tokenFreezeAccountHandler;

    @Mock
    private TokenUnfreezeAccountHandler tokenUnfreezeAccountHandler;

    @Mock
    private TokenGrantKycToAccountHandler tokenGrantKycToAccountHandler;

    @Mock
    private TokenRevokeKycFromAccountHandler tokenRevokeKycFromAccountHandler;

    @Mock
    private TokenAssociateToAccountHandler tokenAssociateToAccountHandler;

    @Mock
    private TokenDissociateFromAccountHandler tokenDissociateFromAccountHandler;

    @Mock
    private TokenFeeScheduleUpdateHandler tokenFeeScheduleUpdateHandler;

    @Mock
    private TokenPauseHandler tokenPauseHandler;

    @Mock
    private TokenUnpauseHandler tokenUnpauseHandler;

    @Mock
    private UtilPrngHandler utilPrngHandler;

    @Mock
    private GlobalDynamicProperties dynamicProperties;

    @Mock
    private WritableStoreFactory writableStoreFactory;

    @Mock
    private WritableTopicStore writableTopicStore;

    @Mock
    private WritableTokenStore writableTokenStore;

    @Mock
    private WritableAccountStore writableAccountStore;

    @Mock
    private WritableTokenRelationStore writableTokenRelStore;

    @Mock
    private WritableFreezeStore writableFreezeStore;

    @Mock
    private UsageLimits usageLimits;

    @Mock
    private HandleContext handleContext;

    @Mock
    private TransactionContext txnCtx;

    @Mock
    private Account account;

    @Mock
    Configuration configuration;

    private SideEffectsTracker sideEffectsTracker = new SideEffectsTracker();

    private TransactionHandlers handlers;
    private TransactionDispatcher dispatcher;

    @BeforeEach
    void setup(@Mock final ReadableStates readableStates, @Mock Key payerKey) {
        when(state.createReadableStates(any())).thenReturn(readableStates);
        when(readableAccountStore.getAccountById(any(AccountID.class))).thenReturn(account);
        lenient().when(account.key()).thenReturn(payerKey);
        when(readableStoreFactory.getStore(ReadableAccountStore.class)).thenReturn(readableAccountStore);

        handlers = new TransactionHandlers(
                consensusCreateTopicHandler,
                consensusUpdateTopicHandler,
                consensusDeleteTopicHandler,
                consensusSubmitMessageHandler,
                contractCreateHandler,
                contractUpdateHandler,
                contractCallHandler,
                contractDeleteHandler,
                contractSystemDeleteHandler,
                contractSystemUndeleteHandler,
                etherumTransactionHandler,
                cryptoCreateHandler,
                cryptoUpdateHandler,
                cryptoTransferHandler,
                cryptoDeleteHandler,
                cryptoApproveAllowanceHandler,
                cryptoDeleteAllowanceHandler,
                cryptoAddLiveHashHandler,
                cryptoDeleteLiveHashHandler,
                fileCreateHandler,
                fileUpdateHandler,
                fileDeleteHandler,
                fileAppendHandler,
                fileSystemDeleteHandler,
                fileSystemUndeleteHandler,
                freezeHandler,
                networkUncheckedSubmitHandler,
                scheduleCreateHandler,
                scheduleSignHandler,
                scheduleDeleteHandler,
                tokenCreateHandler,
                tokenUpdateHandler,
                tokenMintHandler,
                tokenBurnHandler,
                tokenDeleteHandler,
                tokenAccountWipeHandler,
                tokenFreezeAccountHandler,
                tokenUnfreezeAccountHandler,
                tokenGrantKycToAccountHandler,
                tokenRevokeKycFromAccountHandler,
                tokenAssociateToAccountHandler,
                tokenDissociateFromAccountHandler,
                tokenFeeScheduleUpdateHandler,
                tokenPauseHandler,
                tokenUnpauseHandler,
                utilPrngHandler);

        dispatcher = new MonoTransactionDispatcher(txnCtx, handlers, usageLimits, sideEffectsTracker);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new MonoTransactionDispatcher(null, handlers, usageLimits, sideEffectsTracker))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MonoTransactionDispatcher(txnCtx, null, usageLimits, sideEffectsTracker))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MonoTransactionDispatcher(txnCtx, handlers, null, sideEffectsTracker))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MonoTransactionDispatcher(txnCtx, handlers, usageLimits, null))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testDispatchWithIllegalParameters() throws PreCheckException {
        // given
        final var invalidSystemDelete = new PreHandleContextImpl(
                readableStoreFactory,
                TransactionBody.newBuilder()
                        .systemDelete(SystemDeleteTransactionBody.newBuilder().build())
                        .build(),
                configuration);
        final var invalidSystemUndelete = new PreHandleContextImpl(
                readableStoreFactory,
                TransactionBody.newBuilder()
                        .systemUndelete(
                                SystemUndeleteTransactionBody.newBuilder().build())
                        .build(),
                configuration);

        // then
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(null)).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(invalidSystemDelete))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_TRANSACTION_BODY));
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(invalidSystemUndelete))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_TRANSACTION_BODY));
    }

    @Test
    void testDataNotSetFails() throws PreCheckException {
        // given
        final var txBody = TransactionBody.newBuilder().build();
        final var context = new PreHandleContextImpl(readableStoreFactory, txBody, configuration);

        // then
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(context))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_TRANSACTION_BODY));
    }

    @Test
    void testNodeStakeUpdateFails() throws PreCheckException {
        // given
        final var txBody = TransactionBody.newBuilder()
                .nodeStakeUpdate(NodeStakeUpdateTransactionBody.newBuilder())
                .build();
        final var context = new PreHandleContextImpl(readableStoreFactory, txBody, configuration);

        // then
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(context))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_TRANSACTION_BODY));
    }

    @Test
    void dispatchesCreateTopicAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .consensusCreateTopic(ConsensusCreateTopicTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        final var topicID = TopicID.newBuilder().topicNum(666L).build();
        final var recordBuilder = mock(SingleTransactionRecordBuilder.class);
        given(recordBuilder.topicID()).willReturn(topicID);
        given(handleContext.recordBuilder(any())).willReturn(recordBuilder);

        dispatcher.dispatchHandle(handleContext);

        verify(txnCtx).setCreated(PbjConverter.fromPbj(topicID));
    }

    @Test
    void dispatchesUpdateTopicAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .consensusUpdateTopic(ConsensusUpdateTopicTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        dispatcher.dispatchHandle(handleContext);

        verifyNoInteractions(txnCtx);
    }

    @Test
    void dispatchesDeleteTopicAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .consensusDeleteTopic(ConsensusDeleteTopicTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);
        dispatcher.dispatchHandle(handleContext);

        verifyNoInteractions(txnCtx);
    }

    @Test
    void dispatchesSubmitMessageAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        final var newRunningHash = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        final var recordBuilder = mock(SingleTransactionRecordBuilder.class);
        given(recordBuilder.topicRunningHash()).willReturn(Bytes.wrap(newRunningHash));
        given(recordBuilder.topicSequenceNumber()).willReturn(2L);
        given(handleContext.recordBuilder(any())).willReturn(recordBuilder);

        dispatcher.dispatchHandle(handleContext);
        verify(txnCtx).setTopicRunningHash(newRunningHash, 2);
    }

    @Test
    void dispatchesTokenGrantKycAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .tokenGrantKyc(TokenGrantKycTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        assertDoesNotThrow(() -> dispatcher.dispatchHandle(handleContext));
    }

    @Test
    void dispatchesTokenRevokeKycAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .tokenRevokeKyc(TokenRevokeKycTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        assertDoesNotThrow(() -> dispatcher.dispatchHandle(handleContext));
    }

    @Test
    void dispatchesTokenAssociateAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .tokenAssociate(TokenAssociateTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        dispatcher.dispatchHandle(handleContext);

        verify(handleContext).body();
    }

    @Test
    void dispatchesTokenDissociateAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .tokenDissociate(TokenDissociateTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        dispatcher.dispatchHandle(handleContext);

        verify(handleContext).body();
    }

    @Test
    void dispatchesTokenFreezeAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .tokenFreeze(TokenFreezeAccountTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        assertDoesNotThrow(() -> dispatcher.dispatchHandle(handleContext));
    }

    @Test
    void dispatchesTokenUnfreezeAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .tokenUnfreeze(TokenUnfreezeAccountTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        assertDoesNotThrow(() -> dispatcher.dispatchHandle(handleContext));
    }

    @Test
    void dispatchesTokenFeeScheduleUpdateAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .tokenFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        assertDoesNotThrow(() -> dispatcher.dispatchHandle(handleContext));
    }

    @Test
    void dispatchesTokenPauseAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .tokenPause(TokenPauseTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        assertDoesNotThrow(() -> dispatcher.dispatchHandle(handleContext));
    }

    @Test
    void dispatchesTokenUnpauseAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .tokenUnpause(TokenUnpauseTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        assertDoesNotThrow(() -> dispatcher.dispatchHandle(handleContext));
    }

    @Test
    void dispatchesTokenDeleteAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .tokenDeletion(TokenDeleteTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        dispatcher.dispatchHandle(handleContext);

        verify(handleContext).body();
    }

    @Test
    void dispatchesTokenBurnAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .tokenBurn(TokenBurnTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        dispatcher.dispatchHandle(handleContext);

        verify(handleContext).body();
    }

    @Test
    void dispatchesTokenCreateAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .tokenCreation(TokenCreateTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        dispatcher.dispatchHandle(handleContext);

        verify(handleContext).body();
    }

    @Test
    void dispatchesCryptoCreateAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .cryptoCreateAccount(CryptoCreateTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        final var accountID = AccountID.newBuilder().accountNum(666L).build();
        final var recordBuilder = mock(SingleTransactionRecordBuilder.class);
        given(recordBuilder.accountID()).willReturn(accountID);
        given(handleContext.recordBuilder(any())).willReturn(recordBuilder);
        given(usageLimits.areCreatableAccounts(1)).willReturn(true);

        dispatcher.dispatchHandle(handleContext);

        verify(txnCtx).setCreated(PbjConverter.fromPbj(accountID));
    }

    @Test
    void dispatchesCryptoUpdateAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .cryptoUpdateAccount(CryptoUpdateTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        assertDoesNotThrow(() -> dispatcher.dispatchHandle(handleContext));
    }

    @Test
    void dispatchesCryptoDeleteAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .cryptoDelete(CryptoDeleteTransactionBody.DEFAULT)
                .build();

        given(handleContext.body()).willReturn(txnBody);

        assertDoesNotThrow(() -> dispatcher.dispatchHandle(handleContext));
    }

    @Test
    void dispatchesCryptoApproveAllowanceAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .cryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);
        dispatcher.dispatchHandle(handleContext);
    }

    @Test
    void dispatchesCryptoDeleteAllowanceAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .cryptoDeleteAllowance(CryptoDeleteAllowanceTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);
        dispatcher.dispatchHandle(handleContext);
    }

    @Test
    void dispatchesFreezeAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .transactionValidStart(
                                Timestamp.newBuilder().seconds(1000).build()))
                .freeze(FreezeTransactionBody.newBuilder()
                        .freezeType(FREEZE_ABORT)
                        .build())
                .build();
        given(handleContext.body()).willReturn(txnBody);

        dispatcher.dispatchHandle(handleContext);

        verifyNoInteractions(txnCtx);
    }

    @Test
    void dispatchesNetworkUncheckedSubmitAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder())
                .uncheckedSubmit(UncheckedSubmitBody.newBuilder().build())
                .build();
        given(handleContext.body()).willReturn(txnBody);

        assertThatThrownBy(() -> dispatcher.dispatchHandle(handleContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(TYPE_NOT_SUPPORTED);

        verifyNoInteractions(txnCtx);
    }

    @Test
    void dispatchesCryptoAddLiveHashAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder())
                .cryptoAddLiveHash(CryptoAddLiveHashTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        assertThatThrownBy(() -> dispatcher.dispatchHandle(handleContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(TYPE_NOT_SUPPORTED);

        verifyNoInteractions(txnCtx);
    }

    @Test
    void dispatchesCryptoDeleteLiveHashAsExpected() {
        final var txnBody = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder())
                .cryptoDeleteLiveHash(CryptoDeleteLiveHashTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        assertThatThrownBy(() -> dispatcher.dispatchHandle(handleContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(TYPE_NOT_SUPPORTED);

        verifyNoInteractions(txnCtx);
    }

    @Test
    void doesntCommitWhenUsageLimitsExceeded() {
        final var txnBody = TransactionBody.newBuilder()
                .cryptoCreateAccount(CryptoCreateTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);
        given(usageLimits.areCreatableAccounts(1)).willReturn(false);

        assertThatThrownBy(() -> dispatcher.dispatchHandle(handleContext)).isInstanceOf(HandleException.class);

        verify(txnCtx, never()).setCreated(any(com.hederahashgraph.api.proto.java.AccountID.class));
    }

    @Test
    void dispatchesUtilPrngAsExpectedWithPrngBytes() {
        final var txnBody = TransactionBody.newBuilder()
                .utilPrng(UtilPrngTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        final var recordBuilder = mock(SingleTransactionRecordBuilder.class);
        final var entropy = new OneOf<>(EntropyOneOfType.PRNG_BYTES, Bytes.wrap("test".getBytes()));
        given(recordBuilder.entropy()).willReturn(entropy);
        given(handleContext.recordBuilder(any())).willReturn(recordBuilder);

        dispatcher.dispatchHandle(handleContext);

        assertThat(sideEffectsTracker.getPseudorandomNumber()).isEqualTo(-1);
        assertThat(sideEffectsTracker.getPseudorandomBytes()).isEqualTo("test".getBytes());
    }

    @Test
    void dispatchesUtilPrngAsExpectedWithPrngNumber() {
        final var txnBody = TransactionBody.newBuilder()
                .utilPrng(UtilPrngTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);

        final var recordBuilder = mock(SingleTransactionRecordBuilder.class);
        final var entropy = new OneOf<>(EntropyOneOfType.PRNG_NUMBER, 123);
        given(recordBuilder.entropy()).willReturn(entropy);
        given(handleContext.recordBuilder(any())).willReturn(recordBuilder);

        dispatcher.dispatchHandle(handleContext);

        assertThat(sideEffectsTracker.getPseudorandomNumber()).isEqualTo(123);
        assertThat(sideEffectsTracker.getPseudorandomBytes()).isNull();
    }

    @Test
    void cannotDispatchUnsupportedOperations() {
        final var txnBody = TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.DEFAULT)
                .build();
        given(handleContext.body()).willReturn(txnBody);
        assertThatThrownBy(() -> dispatcher.dispatchHandle(handleContext)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("getDispatchParameters")
    void testPreHandleDispatch(
            final TransactionBody txBody, final Function<TransactionHandlers, TransactionHandler> handlerProvider)
            throws PreCheckException {
        // given
        final var context = new PreHandleContextImpl(readableStoreFactory, txBody, configuration);
        final var handler = handlerProvider.apply(handlers);

        // when
        dispatcher.dispatchPreHandle(context);

        // then
        verify(handler).preHandle(context);
    }

    private static Stream<Arguments> getDispatchParameters() {
        return Stream.of(
                // consensus
                createArgs(
                        b -> b.consensusCreateTopic(ConsensusCreateTopicTransactionBody.DEFAULT),
                        TransactionHandlers::consensusCreateTopicHandler),
                createArgs(
                        b -> b.consensusUpdateTopic(ConsensusUpdateTopicTransactionBody.DEFAULT),
                        TransactionHandlers::consensusUpdateTopicHandler),
                createArgs(
                        b -> b.consensusDeleteTopic(ConsensusDeleteTopicTransactionBody.DEFAULT),
                        TransactionHandlers::consensusDeleteTopicHandler),
                createArgs(
                        b -> b.consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.DEFAULT),
                        TransactionHandlers::consensusSubmitMessageHandler),

                // crypto
                createArgs(
                        b -> b.cryptoCreateAccount(CryptoCreateTransactionBody.DEFAULT),
                        TransactionHandlers::cryptoCreateHandler),
                createArgs(
                        b -> b.cryptoUpdateAccount(CryptoUpdateTransactionBody.DEFAULT),
                        TransactionHandlers::cryptoUpdateHandler),
                createArgs(
                        b -> b.cryptoTransfer(CryptoTransferTransactionBody.DEFAULT),
                        TransactionHandlers::cryptoTransferHandler),
                createArgs(
                        b -> b.cryptoDelete(CryptoDeleteTransactionBody.DEFAULT),
                        TransactionHandlers::cryptoDeleteHandler),
                createArgs(
                        b -> b.cryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.DEFAULT),
                        TransactionHandlers::cryptoApproveAllowanceHandler),
                createArgs(
                        b -> b.cryptoDeleteAllowance(CryptoDeleteAllowanceTransactionBody.DEFAULT),
                        TransactionHandlers::cryptoDeleteAllowanceHandler),
                createArgs(
                        b -> b.cryptoAddLiveHash(CryptoAddLiveHashTransactionBody.DEFAULT),
                        TransactionHandlers::cryptoAddLiveHashHandler),
                createArgs(
                        b -> b.cryptoDeleteLiveHash(CryptoDeleteLiveHashTransactionBody.DEFAULT),
                        TransactionHandlers::cryptoDeleteLiveHashHandler),

                // file
                createArgs(
                        b -> b.fileCreate(FileCreateTransactionBody.DEFAULT), TransactionHandlers::fileCreateHandler),
                createArgs(
                        b -> b.fileUpdate(FileUpdateTransactionBody.DEFAULT), TransactionHandlers::fileUpdateHandler),
                createArgs(
                        b -> b.fileDelete(FileDeleteTransactionBody.DEFAULT), TransactionHandlers::fileDeleteHandler),
                createArgs(
                        b -> b.fileAppend(FileAppendTransactionBody.DEFAULT), TransactionHandlers::fileAppendHandler),

                // freeze
                createArgs(b -> b.freeze(FreezeTransactionBody.DEFAULT), TransactionHandlers::freezeHandler),

                // network
                createArgs(
                        b -> b.uncheckedSubmit(UncheckedSubmitBody.DEFAULT),
                        TransactionHandlers::networkUncheckedSubmitHandler),

                // schedule
                createArgs(
                        b -> b.scheduleCreate(ScheduleCreateTransactionBody.DEFAULT),
                        TransactionHandlers::scheduleCreateHandler),
                createArgs(
                        b -> b.scheduleDelete(ScheduleDeleteTransactionBody.DEFAULT),
                        TransactionHandlers::scheduleDeleteHandler),
                createArgs(
                        b -> b.scheduleSign(ScheduleSignTransactionBody.DEFAULT),
                        TransactionHandlers::scheduleSignHandler),

                // smart-contract
                createArgs(
                        b -> b.contractCreateInstance(ContractCreateTransactionBody.DEFAULT),
                        TransactionHandlers::contractCreateHandler),
                createArgs(
                        b -> b.contractUpdateInstance(ContractUpdateTransactionBody.DEFAULT),
                        TransactionHandlers::contractUpdateHandler),
                createArgs(
                        b -> b.contractCall(ContractCallTransactionBody.DEFAULT),
                        TransactionHandlers::contractCallHandler),
                createArgs(
                        b -> b.contractDeleteInstance(ContractDeleteTransactionBody.DEFAULT),
                        TransactionHandlers::contractDeleteHandler),
                createArgs(
                        b -> b.ethereumTransaction(EthereumTransactionBody.DEFAULT),
                        TransactionHandlers::etherumTransactionHandler),

                // token
                createArgs(
                        b -> b.tokenCreation(TokenCreateTransactionBody.DEFAULT),
                        TransactionHandlers::tokenCreateHandler),
                createArgs(
                        b -> b.tokenUpdate(TokenUpdateTransactionBody.DEFAULT),
                        TransactionHandlers::tokenUpdateHandler),
                createArgs(b -> b.tokenMint(TokenMintTransactionBody.DEFAULT), TransactionHandlers::tokenMintHandler),
                createArgs(b -> b.tokenBurn(TokenBurnTransactionBody.DEFAULT), TransactionHandlers::tokenBurnHandler),
                createArgs(
                        b -> b.tokenDeletion(TokenDeleteTransactionBody.DEFAULT),
                        TransactionHandlers::tokenDeleteHandler),
                createArgs(
                        b -> b.tokenWipe(TokenWipeAccountTransactionBody.DEFAULT),
                        TransactionHandlers::tokenAccountWipeHandler),
                createArgs(
                        b -> b.tokenFreeze(TokenFreezeAccountTransactionBody.DEFAULT),
                        TransactionHandlers::tokenFreezeAccountHandler),
                createArgs(
                        b -> b.tokenUnfreeze(TokenUnfreezeAccountTransactionBody.DEFAULT),
                        TransactionHandlers::tokenUnfreezeAccountHandler),
                createArgs(
                        b -> b.tokenGrantKyc(TokenGrantKycTransactionBody.DEFAULT),
                        TransactionHandlers::tokenGrantKycToAccountHandler),
                createArgs(
                        b -> b.tokenRevokeKyc(TokenRevokeKycTransactionBody.DEFAULT),
                        TransactionHandlers::tokenRevokeKycFromAccountHandler),
                createArgs(
                        b -> b.tokenAssociate(TokenAssociateTransactionBody.DEFAULT),
                        TransactionHandlers::tokenAssociateToAccountHandler),
                createArgs(
                        b -> b.tokenDissociate(TokenDissociateTransactionBody.DEFAULT),
                        TransactionHandlers::tokenDissociateFromAccountHandler),
                createArgs(
                        b -> b.tokenFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody.DEFAULT),
                        TransactionHandlers::tokenFeeScheduleUpdateHandler),
                createArgs(
                        b -> b.tokenPause(TokenPauseTransactionBody.DEFAULT), TransactionHandlers::tokenPauseHandler),
                createArgs(
                        b -> b.tokenUnpause(TokenUnpauseTransactionBody.DEFAULT),
                        TransactionHandlers::tokenUnpauseHandler),

                // util
                createArgs(b -> b.utilPrng(UtilPrngTransactionBody.DEFAULT), TransactionHandlers::utilPrngHandler),

                // mixed
                createArgs(
                        b -> b.systemDelete(
                                SystemDeleteTransactionBody.newBuilder().contractID(ContractID.DEFAULT)),
                        TransactionHandlers::contractSystemDeleteHandler),
                createArgs(
                        b -> b.systemDelete(
                                SystemDeleteTransactionBody.newBuilder().fileID(FileID.DEFAULT)),
                        TransactionHandlers::fileSystemDeleteHandler),
                createArgs(
                        b -> b.systemUndelete(
                                SystemUndeleteTransactionBody.newBuilder().contractID(ContractID.DEFAULT)),
                        TransactionHandlers::contractSystemUndeleteHandler),
                createArgs(
                        b -> b.systemUndelete(
                                SystemUndeleteTransactionBody.newBuilder().fileID(FileID.DEFAULT)),
                        TransactionHandlers::fileSystemUndeleteHandler));
    }

    private static Arguments createArgs(
            Function<TransactionBody.Builder, TransactionBody.Builder> txBodySetup,
            Function<TransactionHandlers, TransactionHandler> handlerExtractor) {
        final var builder = TransactionBody.newBuilder();
        final var txBody = txBodySetup.apply(builder).build();
        return Arguments.of(txBody, handlerExtractor);
    }
}
