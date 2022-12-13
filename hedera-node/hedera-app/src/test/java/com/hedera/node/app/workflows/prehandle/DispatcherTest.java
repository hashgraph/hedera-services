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
package com.hedera.node.app.workflows.prehandle;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.admin.FreezeService;
import com.hedera.node.app.service.admin.impl.handlers.FreezeHandler;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusDeleteTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusUpdateTopicHandler;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.handlers.ContractCallHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractCreateHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractDeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractSystemDeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractSystemUndeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractUpdateHandler;
import com.hedera.node.app.service.contract.impl.handlers.EtherumTransactionHandler;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.handlers.FileAppendHandler;
import com.hedera.node.app.service.file.impl.handlers.FileCreateHandler;
import com.hedera.node.app.service.file.impl.handlers.FileDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemUndeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileUpdateHandler;
import com.hedera.node.app.service.mono.config.AccountNumbers;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.service.network.impl.handlers.UncheckedSubmitHandler;
import com.hedera.node.app.service.scheduled.ScheduleService;
import com.hedera.node.app.service.scheduled.impl.handlers.ScheduleCreateHandler;
import com.hedera.node.app.service.scheduled.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.service.scheduled.impl.handlers.ScheduleSignHandler;
import com.hedera.node.app.service.token.CryptoPreTransactionHandler;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.TokenPreTransactionHandler;
import com.hedera.node.app.service.token.TokenService;
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
import com.hedera.node.app.service.util.UtilPreTransactionHandler;
import com.hedera.node.app.service.util.UtilService;
import com.hedera.node.app.service.util.impl.handlers.UtilPrngHandler;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import com.hedera.node.app.spi.state.States;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.Handlers;
import com.hederahashgraph.api.proto.java.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.assertj.core.api.Assertions;
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

    @Mock private HederaState hederaState;

    @Mock ConsensusCreateTopicHandler consensusCreateTopicHandler;
    @Mock ConsensusUpdateTopicHandler consensusUpdateTopicHandler;
    @Mock ConsensusDeleteTopicHandler consensusDeleteTopicHandler;
    @Mock ConsensusSubmitMessageHandler consensusSubmitMessageHandler;

    @Mock ContractCreateHandler contractCreateHandler;
    @Mock ContractUpdateHandler contractUpdateHandler;
    @Mock ContractCallHandler contractCallHandler;
    @Mock ContractDeleteHandler contractDeleteHandler;
    @Mock ContractSystemDeleteHandler contractSystemDeleteHandler;
    @Mock ContractSystemUndeleteHandler contractSystemUndeleteHandler;
    @Mock EtherumTransactionHandler etherumTransactionHandler;

    @Mock CryptoCreateHandler cryptoCreateHandler;
    @Mock CryptoUpdateHandler cryptoUpdateHandler;
    @Mock CryptoTransferHandler cryptoTransferHandler;
    @Mock CryptoDeleteHandler cryptoDeleteHandler;
    @Mock CryptoApproveAllowanceHandler cryptoApproveAllowanceHandler;
    @Mock CryptoDeleteAllowanceHandler cryptoDeleteAllowanceHandler;
    @Mock CryptoAddLiveHashHandler cryptoAddLiveHashHandler;
    @Mock CryptoDeleteLiveHashHandler cryptoDeleteLiveHashHandler;

    @Mock FileCreateHandler fileCreateHandler;
    @Mock FileUpdateHandler fileUpdateHandler;
    @Mock FileDeleteHandler fileDeleteHandler;
    @Mock FileAppendHandler fileAppendHandler;
    @Mock FileSystemDeleteHandler fileSystemDeleteHandler;
    @Mock FileSystemUndeleteHandler fileSystemUndeleteHandler;

    @Mock FreezeHandler freezeHandler;

    @Mock UncheckedSubmitHandler uncheckedSubmitHandler;

    @Mock ScheduleCreateHandler scheduleCreateHandler;
    @Mock ScheduleSignHandler scheduleSignHandler;
    @Mock ScheduleDeleteHandler scheduleDeleteHandler;

    @Mock TokenCreateHandler tokenCreateHandler;
    @Mock TokenUpdateHandler tokenUpdateHandler;
    @Mock TokenMintHandler tokenMintHandler;
    @Mock TokenBurnHandler tokenBurnHandler;
    @Mock TokenDeleteHandler tokenDeleteHandler;
    @Mock TokenAccountWipeHandler tokenAccountWipeHandler;
    @Mock TokenFreezeAccountHandler tokenFreezeAccountHandler;
    @Mock TokenUnfreezeAccountHandler tokenUnfreezeAccountHandler;
    @Mock TokenGrantKycToAccountHandler tokenGrantKycToAccountHandler;
    @Mock TokenRevokeKycFromAccountHandler tokenRevokeKycFromAccountHandler;
    @Mock TokenAssociateToAccountHandler tokenAssociateToAccountHandler;
    @Mock TokenDissociateFromAccountHandler tokenDissociateFromAccountHandler;
    @Mock TokenFeeScheduleUpdateHandler tokenFeeScheduleUpdateHandler;
    @Mock TokenPauseHandler tokenPauseHandler;
    @Mock TokenUnpauseHandler tokenUnpauseHandler;

    @Mock UtilPrngHandler utilPrngHandler;

    private Handlers handlers;
    private Dispatcher dispatcher;

    @SuppressWarnings("JUnitMalformedDeclaration")
    @BeforeEach
    void setup(@Mock States states) {
        when(hederaState.createReadableStates(any())).thenReturn(states);

        handlers = new Handlers(
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

                utilPrngHandler
        );

        dispatcher = new Dispatcher(handlers, hederaState);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new Dispatcher(null, hederaState))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Dispatcher(handlers, null))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testDispatchWithIllegalParameters() {
        // given
        final var invalidSystemDelete =
                TransactionBody.newBuilder()
                        .mergeSystemDelete(SystemDeleteTransactionBody.getDefaultInstance())
                        .build();
        final var invalidSystemUndelete =
                TransactionBody.newBuilder()
                        .mergeSystemUndelete(SystemUndeleteTransactionBody.getDefaultInstance())
                        .build();

        assertThatThrownBy(() -> dispatcher.preHandle(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> dispatcher.preHandle(invalidSystemDelete))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dispatcher.preHandle(invalidSystemUndelete))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("getDispatchParameters")
    void testDispatch(
            final TransactionBody txBody, final Consumer<Handlers> verification) {
        // when
        dispatcher.preHandle(txBody);

        // then
        verification.accept(this.handlers);
    }

    private static Stream<Arguments> getDispatchParameters() {
        return Stream.of(
                // consensus
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeConsensusCreateTopic(
                                        ConsensusCreateTopicTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.consensusCreateTopicHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeConsensusUpdateTopic(
                                        ConsensusUpdateTopicTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.consensusUpdateTopicHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeConsensusDeleteTopic(
                                        ConsensusDeleteTopicTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.consensusDeleteTopicHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeConsensusSubmitMessage(
                                        ConsensusSubmitMessageTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.consensusSubmitMessageHandler()).preHandle(any(), any())),

                // contract
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeContractCreateInstance(
                                        ContractCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.contractCreateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeContractUpdateInstance(
                                        ContractUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.contractUpdateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeContractCall(ContractCallTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.contractCallHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeContractDeleteInstance(
                                        ContractDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.contractDeleteHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeEthereumTransaction(
                                        EthereumTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.etherumTransactionHandler()).preHandle(any(), any())),

                // crypto
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoCreateAccount(
                                        CryptoCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.cryptoCreateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoUpdateAccount(
                                        CryptoUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.cryptoUpdateHandler()).preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoTransfer(
                                        CryptoTransferTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.cryptoTransferHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoDelete(CryptoDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.cryptoDeleteHandler()).preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoApproveAllowance(
                                        CryptoApproveAllowanceTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.cryptoApproveAllowanceHandler()).preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoDeleteAllowance(
                                        CryptoDeleteAllowanceTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.cryptoDeleteAllowanceHandler()).preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoAddLiveHash(
                                        CryptoAddLiveHashTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.cryptoAddLiveHashHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoDeleteLiveHash(
                                        CryptoDeleteLiveHashTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.cryptoDeleteLiveHashHandler()).preHandle(any(), any())),

                // file
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeFileCreate(FileCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.fileCreateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeFileUpdate(FileUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.fileUpdateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeFileDelete(FileDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.fileDeleteHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeFileAppend(FileAppendTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.fileAppendHandler()).preHandle(any(), any())),

                // freeze
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeFreeze(FreezeTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.freezeHandler()).preHandle(any(), any())),

                // network
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeUncheckedSubmit(UncheckedSubmitBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.uncheckedSubmitHandler()).preHandle(any(), any())),

                // schedule
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeScheduleCreate(
                                        ScheduleCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.scheduleCreateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeScheduleSign(ScheduleSignTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.scheduleSignHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeScheduleDelete(
                                        ScheduleDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.scheduleDeleteHandler()).preHandle(any(), any())),

                // token
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenCreation(TokenCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.tokenCreateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenUpdate(TokenUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.tokenUpdateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenMint(TokenMintTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.tokenMintHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenBurn(TokenBurnTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.tokenBurnHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenDeletion(TokenDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.tokenDeleteHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenWipe(
                                        TokenWipeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.tokenAccountWipeHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenFreeze(
                                        TokenFreezeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.tokenFreezeAccountHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenUnfreeze(
                                        TokenUnfreezeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.tokenUnfreezeAccountHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenGrantKyc(
                                        TokenGrantKycTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.tokenGrantKycToAccountHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenRevokeKyc(
                                        TokenRevokeKycTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.tokenRevokeKycFromAccountHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenAssociate(
                                        TokenAssociateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.tokenAssociateToAccountHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenDissociate(
                                        TokenDissociateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.tokenDissociateFromAccountHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenFeeScheduleUpdate(
                                        TokenFeeScheduleUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.tokenFeeScheduleUpdateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenPause(TokenPauseTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.tokenPauseHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenUnpause(TokenUnpauseTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.tokenUnpauseHandler()).preHandle(any(), any())),

                // util
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeUtilPrng(UtilPrngTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.utilPrngHandler()).preHandle(any(), any())),

                // mixed
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeSystemDelete(
                                        SystemDeleteTransactionBody.newBuilder()
                                                .setContractID(ContractID.getDefaultInstance())
                                                .build())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.contractSystemDeleteHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeSystemDelete(
                                        SystemDeleteTransactionBody.newBuilder()
                                                .setFileID(FileID.getDefaultInstance())
                                                .build())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.fileSystemDeleteHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeSystemUndelete(
                                        SystemUndeleteTransactionBody.newBuilder()
                                                .setContractID(ContractID.getDefaultInstance())
                                                .build())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.contractSystemUndeleteHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeSystemUndelete(
                                        SystemUndeleteTransactionBody.newBuilder()
                                                .setFileID(FileID.getDefaultInstance())
                                                .build())
                                .build(),
                        (Consumer<Handlers>)
                                h -> verify(h.fileSystemUndeleteHandler()).preHandle(any(), any()))
        );
    }
}
