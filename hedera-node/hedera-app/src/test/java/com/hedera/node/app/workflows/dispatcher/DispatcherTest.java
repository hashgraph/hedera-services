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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.admin.impl.handlers.FreezeHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusDeleteTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusUpdateTopicHandler;
import com.hedera.node.app.service.contract.impl.handlers.*;
import com.hedera.node.app.service.file.impl.handlers.*;
import com.hedera.node.app.service.network.impl.handlers.UncheckedSubmitHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleCreateHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleSignHandler;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.*;
import com.hedera.node.app.service.util.impl.handlers.UtilPrngHandler;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.PrehandleHandlerContext;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.prehandle.ReadableStatesTracker;
import com.hederahashgraph.api.proto.java.*;
import java.util.function.BiConsumer;
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
class DispatcherTest {

    @Mock(strictness = LENIENT)
    private HederaState state;

    @Mock(strictness = LENIENT)
    private ReadableAccountStore accountStore;

    @Mock private ConsensusCreateTopicHandler consensusCreateTopicHandler;
    @Mock private ConsensusUpdateTopicHandler consensusUpdateTopicHandler;
    @Mock private ConsensusDeleteTopicHandler consensusDeleteTopicHandler;
    @Mock private ConsensusSubmitMessageHandler consensusSubmitMessageHandler;

    @Mock private ContractCreateHandler contractCreateHandler;
    @Mock private ContractUpdateHandler contractUpdateHandler;
    @Mock private ContractCallHandler contractCallHandler;
    @Mock private ContractDeleteHandler contractDeleteHandler;
    @Mock private ContractSystemDeleteHandler contractSystemDeleteHandler;
    @Mock private ContractSystemUndeleteHandler contractSystemUndeleteHandler;
    @Mock private EtherumTransactionHandler etherumTransactionHandler;

    @Mock private CryptoCreateHandler cryptoCreateHandler;
    @Mock private CryptoUpdateHandler cryptoUpdateHandler;
    @Mock private CryptoTransferHandler cryptoTransferHandler;
    @Mock private CryptoDeleteHandler cryptoDeleteHandler;
    @Mock private CryptoApproveAllowanceHandler cryptoApproveAllowanceHandler;
    @Mock private CryptoDeleteAllowanceHandler cryptoDeleteAllowanceHandler;
    @Mock private CryptoAddLiveHashHandler cryptoAddLiveHashHandler;
    @Mock private CryptoDeleteLiveHashHandler cryptoDeleteLiveHashHandler;

    @Mock private FileCreateHandler fileCreateHandler;
    @Mock private FileUpdateHandler fileUpdateHandler;
    @Mock private FileDeleteHandler fileDeleteHandler;
    @Mock private FileAppendHandler fileAppendHandler;
    @Mock private FileSystemDeleteHandler fileSystemDeleteHandler;
    @Mock private FileSystemUndeleteHandler fileSystemUndeleteHandler;

    @Mock private FreezeHandler freezeHandler;

    @Mock private UncheckedSubmitHandler uncheckedSubmitHandler;

    @Mock private ScheduleCreateHandler scheduleCreateHandler;
    @Mock private ScheduleSignHandler scheduleSignHandler;
    @Mock private ScheduleDeleteHandler scheduleDeleteHandler;

    @Mock private TokenCreateHandler tokenCreateHandler;
    @Mock private TokenUpdateHandler tokenUpdateHandler;
    @Mock private TokenMintHandler tokenMintHandler;
    @Mock private TokenBurnHandler tokenBurnHandler;
    @Mock private TokenDeleteHandler tokenDeleteHandler;
    @Mock private TokenAccountWipeHandler tokenAccountWipeHandler;
    @Mock private TokenFreezeAccountHandler tokenFreezeAccountHandler;
    @Mock private TokenUnfreezeAccountHandler tokenUnfreezeAccountHandler;
    @Mock private TokenGrantKycToAccountHandler tokenGrantKycToAccountHandler;
    @Mock private TokenRevokeKycFromAccountHandler tokenRevokeKycFromAccountHandler;
    @Mock private TokenAssociateToAccountHandler tokenAssociateToAccountHandler;
    @Mock private TokenDissociateFromAccountHandler tokenDissociateFromAccountHandler;
    @Mock private TokenFeeScheduleUpdateHandler tokenFeeScheduleUpdateHandler;
    @Mock private TokenPauseHandler tokenPauseHandler;
    @Mock private TokenUnpauseHandler tokenUnpauseHandler;

    @Mock private UtilPrngHandler utilPrngHandler;

    @Mock private HederaAccountNumbers numbers;
    @Mock private HederaFileNumbers fileNumbers;
    @Mock private AccountKeyLookup keyLookup;
    private PreHandleContext preHandleCtx;

    private Handlers handlers;
    private Dispatcher dispatcher;

    @BeforeEach
    void setup(@Mock final ReadableStates readableStates, @Mock HederaKey payerKey) {
        when(state.createReadableStates(any())).thenReturn(readableStates);
        when(accountStore.getKey(any(AccountID.class)))
                .thenReturn(KeyOrLookupFailureReason.withKey(payerKey));

        handlers =
                new Handlers(
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
                        uncheckedSubmitHandler,
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

        preHandleCtx = new PreHandleContext(numbers, fileNumbers, keyLookup);
        dispatcher = new Dispatcher(handlers, preHandleCtx);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new Dispatcher(null, preHandleCtx))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Dispatcher(handlers, null))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testDispatchWithIllegalParameters() {
        // given
        final var payer = AccountID.newBuilder().build();
        final var tracker = new ReadableStatesTracker(state);
        final var validContext =
                new PrehandleHandlerContext(
                        accountStore,
                        TransactionBody.newBuilder()
                                .setFileCreate(FileCreateTransactionBody.getDefaultInstance())
                                .build(),
                        payer);
        final var invalidSystemDelete =
                new PrehandleHandlerContext(
                        accountStore,
                        TransactionBody.newBuilder()
                                .setSystemDelete(SystemDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        payer);
        final var invalidSystemUndelete =
                new PrehandleHandlerContext(
                        accountStore,
                        TransactionBody.newBuilder()
                                .setSystemUndelete(
                                        SystemUndeleteTransactionBody.getDefaultInstance())
                                .build(),
                        payer);

        // then
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(null, validContext))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(tracker, null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(tracker, invalidSystemDelete))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(tracker, invalidSystemUndelete))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("getDispatchParameters")
    void testPreHandleWithPayer(
            final TransactionBody txBody,
            final BiConsumer<Handlers, PrehandleHandlerContext> verification) {
        // given
        final var payer = AccountID.newBuilder().build();
        final var tracker = new ReadableStatesTracker(state);
        final var context = new PrehandleHandlerContext(accountStore, txBody, payer);

        // when
        dispatcher.dispatchPreHandle(tracker, context);

        // then
        verification.accept(this.handlers, context);
    }

    private static Stream<Arguments> getDispatchParameters() {
        return Stream.of(
                // consensus
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setConsensusCreateTopic(
                                        ConsensusCreateTopicTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.consensusCreateTopicHandler())
                                                .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setConsensusUpdateTopic(
                                        ConsensusUpdateTopicTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.consensusUpdateTopicHandler())
                                                .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setConsensusDeleteTopic(
                                        ConsensusDeleteTopicTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.consensusDeleteTopicHandler())
                                                .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setConsensusSubmitMessage(
                                        ConsensusSubmitMessageTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.consensusSubmitMessageHandler())
                                                .preHandle(meta)),

                // contract
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setContractCreateInstance(
                                        ContractCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.contractCreateHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setContractUpdateInstance(
                                        ContractUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.contractUpdateHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setContractCall(ContractCallTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.contractCallHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setContractDeleteInstance(
                                        ContractDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.contractDeleteHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setEthereumTransaction(
                                        EthereumTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.etherumTransactionHandler())
                                                .preHandle(meta)),

                // crypto
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoCreateAccount(
                                        CryptoCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.cryptoCreateHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoUpdateAccount(
                                        CryptoUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.cryptoUpdateHandler())
                                                .preHandle(eq(meta), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(
                                        CryptoTransferTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.cryptoTransferHandler())
                                                .preHandle(eq(meta), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoDelete(CryptoDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.cryptoDeleteHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoApproveAllowance(
                                        CryptoApproveAllowanceTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.cryptoApproveAllowanceHandler())
                                                .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoDeleteAllowance(
                                        CryptoDeleteAllowanceTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.cryptoDeleteAllowanceHandler())
                                                .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoAddLiveHash(
                                        CryptoAddLiveHashTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.cryptoAddLiveHashHandler())
                                                .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoDeleteLiveHash(
                                        CryptoDeleteLiveHashTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.cryptoDeleteLiveHashHandler())
                                                .preHandle(meta)),

                // file
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setFileCreate(FileCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.fileCreateHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setFileUpdate(FileUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.fileUpdateHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setFileDelete(FileDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.fileDeleteHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setFileAppend(FileAppendTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.fileAppendHandler()).preHandle(meta)),

                // freeze
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setFreeze(FreezeTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.freezeHandler()).preHandle(meta)),

                // network
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setUncheckedSubmit(UncheckedSubmitBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.uncheckedSubmitHandler()).preHandle(meta)),

                // schedule
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setScheduleCreate(
                                        ScheduleCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.scheduleCreateHandler())
                                                .preHandle(eq(meta), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setScheduleSign(ScheduleSignTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.scheduleSignHandler())
                                                .preHandle(eq(meta), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setScheduleDelete(
                                        ScheduleDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.scheduleDeleteHandler()).preHandle(meta)),

                // token
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenCreation(TokenCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.tokenCreateHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenUpdate(TokenUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.tokenUpdateHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenMint(TokenMintTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.tokenMintHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenBurn(TokenBurnTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.tokenBurnHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenDeletion(TokenDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.tokenDeleteHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenWipe(TokenWipeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.tokenAccountWipeHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenFreeze(
                                        TokenFreezeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.tokenFreezeAccountHandler())
                                                .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenUnfreeze(
                                        TokenUnfreezeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.tokenUnfreezeAccountHandler())
                                                .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenGrantKyc(TokenGrantKycTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.tokenGrantKycToAccountHandler())
                                                .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenRevokeKyc(
                                        TokenRevokeKycTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.tokenRevokeKycFromAccountHandler())
                                                .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenAssociate(
                                        TokenAssociateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.tokenAssociateToAccountHandler())
                                                .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenDissociate(
                                        TokenDissociateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.tokenDissociateFromAccountHandler())
                                                .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenFeeScheduleUpdate(
                                        TokenFeeScheduleUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.tokenFeeScheduleUpdateHandler())
                                                .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenPause(TokenPauseTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.tokenPauseHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenUnpause(TokenUnpauseTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.tokenUnpauseHandler()).preHandle(meta)),

                // util
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setUtilPrng(UtilPrngTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.utilPrngHandler()).preHandle(meta)),

                // mixed
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setSystemDelete(
                                        SystemDeleteTransactionBody.newBuilder()
                                                .setContractID(ContractID.getDefaultInstance())
                                                .build())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.contractSystemDeleteHandler())
                                                .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setSystemDelete(
                                        SystemDeleteTransactionBody.newBuilder()
                                                .setFileID(FileID.getDefaultInstance())
                                                .build())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.fileSystemDeleteHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setSystemUndelete(
                                        SystemUndeleteTransactionBody.newBuilder()
                                                .setContractID(ContractID.getDefaultInstance())
                                                .build())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.contractSystemUndeleteHandler())
                                                .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setSystemUndelete(
                                        SystemUndeleteTransactionBody.newBuilder()
                                                .setFileID(FileID.getDefaultInstance())
                                                .build())
                                .build(),
                        (BiConsumer<Handlers, PrehandleHandlerContext>)
                                (handlers, meta) ->
                                        verify(handlers.fileSystemUndeleteHandler())
                                                .preHandle(meta)));
    }
}
