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

import com.hedera.node.app.ServicesAccessor;
import com.hedera.node.app.service.admin.FreezePreTransactionHandler;
import com.hedera.node.app.service.admin.FreezeService;
import com.hedera.node.app.service.consensus.ConsensusPreTransactionHandler;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.contract.ContractPreTransactionHandler;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FilePreTransactionHandler;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.mono.config.AccountNumbers;
import com.hedera.node.app.service.network.NetworkPreTransactionHandler;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.service.scheduled.SchedulePreTransactionHandler;
import com.hedera.node.app.service.scheduled.ScheduleService;
import com.hedera.node.app.service.token.CryptoPreTransactionHandler;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.TokenPreTransactionHandler;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.util.UtilPreTransactionHandler;
import com.hedera.node.app.service.util.UtilService;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import com.hedera.node.app.state.HederaState;
import com.hederahashgraph.api.proto.java.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreHandleDispatcherTest {

    @Mock private HederaState hederaState;

    @Mock private ConsensusPreTransactionHandler consensusHandler;
    @Mock private ContractPreTransactionHandler contractHandler;
    @Mock private CryptoPreTransactionHandler cryptoHandler;
    @Mock private FilePreTransactionHandler fileHandler;
    @Mock private FreezePreTransactionHandler freezeHandler;
    @Mock private NetworkPreTransactionHandler networkHandler;
    @Mock private SchedulePreTransactionHandler scheduleHandler;
    @Mock private TokenPreTransactionHandler tokenHandler;
    @Mock private UtilPreTransactionHandler utilHandler;

    private ServicesAccessor servicesAccessor;

    private PreHandleContext context;

    private PreHandleDispatcher dispatcher;

    @BeforeEach
    void setup(
            @Mock ConsensusService consensusService,
            @Mock ContractService contractService,
            @Mock CryptoService cryptoService,
            @Mock FileService fileService,
            @Mock FreezeService freezeService,
            @Mock NetworkService networkService,
            @Mock ScheduleService scheduleService,
            @Mock TokenService tokenService,
            @Mock UtilService utilService,
            @Mock AccountNumbers accountNumbers,
            @Mock HederaFileNumbers hederaFileNumbers) {
        servicesAccessor =
                new ServicesAccessor(
                        consensusService,
                        contractService,
                        cryptoService,
                        fileService,
                        freezeService,
                        networkService,
                        scheduleService,
                        tokenService,
                        utilService);

        context = new PreHandleContext(accountNumbers, hederaFileNumbers);

        when(consensusService.createPreTransactionHandler(any(), eq(context)))
                .thenReturn(consensusHandler);
        when(contractService.createPreTransactionHandler(any(), eq(context)))
                .thenReturn(contractHandler);
        when(cryptoService.createPreTransactionHandler(any(), eq(context)))
                .thenReturn(cryptoHandler);
        when(fileService.createPreTransactionHandler(any(), eq(context))).thenReturn(fileHandler);
        when(freezeService.createPreTransactionHandler(any(), eq(context)))
                .thenReturn(freezeHandler);
        when(networkService.createPreTransactionHandler(any(), eq(context)))
                .thenReturn(networkHandler);
        when(scheduleService.createPreTransactionHandler(any(), eq(context)))
                .thenReturn(scheduleHandler);
        when(tokenService.createPreTransactionHandler(any(), eq(context))).thenReturn(tokenHandler);
        when(utilService.createPreTransactionHandler(any(), eq(context))).thenReturn(utilHandler);

        dispatcher = new PreHandleDispatcher(hederaState, servicesAccessor, context);
    }

    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new PreHandleDispatcher(null, servicesAccessor, context))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreHandleDispatcher(hederaState, null, context))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreHandleDispatcher(hederaState, servicesAccessor, null))
                .isInstanceOf(NullPointerException.class);
    }

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

        assertThatThrownBy(() -> dispatcher.dispatch(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> dispatcher.dispatch(invalidSystemDelete))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dispatcher.dispatch(invalidSystemUndelete))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("getDispatchParameters")
    void testDispatch(
            final TransactionBody txBody, final Consumer<PreHandleDispatcherTest> verification) {
        // when
        dispatcher.dispatch(txBody);

        // then
        verification.accept(this);
    }

    private static Stream<Arguments> getDispatchParameters() {
        return Stream.of(
                // consensus
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeConsensusCreateTopic(
                                        ConsensusCreateTopicTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.consensusHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeConsensusUpdateTopic(
                                        ConsensusUpdateTopicTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.consensusHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeConsensusDeleteTopic(
                                        ConsensusDeleteTopicTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.consensusHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeConsensusSubmitMessage(
                                        ConsensusSubmitMessageTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.consensusHandler)
                                                .preHandle(any(), any())),

                // contract
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeContractCreateInstance(
                                        ContractCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.contractHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeContractUpdateInstance(
                                        ContractUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.contractHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeContractCall(ContractCallTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.contractHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeContractDeleteInstance(
                                        ContractDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.contractHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeEthereumTransaction(
                                        EthereumTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.contractHandler)
                                                .preHandle(any(), any())),

                // crypto
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoCreateAccount(
                                        CryptoCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.cryptoHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoUpdateAccount(
                                        CryptoUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.cryptoHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoTransfer(
                                        CryptoTransferTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.cryptoHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoDelete(CryptoDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.cryptoHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoApproveAllowance(
                                        CryptoApproveAllowanceTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.cryptoHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoDeleteAllowance(
                                        CryptoDeleteAllowanceTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.cryptoHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoAddLiveHash(
                                        CryptoAddLiveHashTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.cryptoHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoDeleteLiveHash(
                                        CryptoDeleteLiveHashTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.cryptoHandler)
                                                .preHandle(any(), any())),

                // file
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeFileCreate(FileCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test -> verify(test.fileHandler).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeFileUpdate(FileUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test -> verify(test.fileHandler).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeFileDelete(FileDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test -> verify(test.fileHandler).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeFileAppend(FileAppendTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.fileHandler)
                                                .preHandle(any(), any())),

                // freeze
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeFreeze(FreezeTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test -> verify(test.freezeHandler).preHandleFreeze(any(), any())),

                // network
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeUncheckedSubmit(UncheckedSubmitBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.networkHandler)
                                                .preHandle(any(), any())),

                // schedule
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeScheduleCreate(
                                        ScheduleCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.scheduleHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeScheduleSign(ScheduleSignTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.scheduleHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeScheduleDelete(
                                        ScheduleDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.scheduleHandler)
                                                .preHandle(any(), any())),

                // token
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenCreation(TokenCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test -> verify(test.tokenHandler).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenUpdate(TokenUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test -> verify(test.tokenHandler).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenMint(TokenMintTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test -> verify(test.tokenHandler).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenBurn(TokenBurnTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test -> verify(test.tokenHandler).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenDeletion(TokenDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test -> verify(test.tokenHandler).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenWipe(
                                        TokenWipeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test -> verify(test.tokenHandler).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenFreeze(
                                        TokenFreezeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.tokenHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenUnfreeze(
                                        TokenUnfreezeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.tokenHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenGrantKyc(
                                        TokenGrantKycTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.tokenHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenRevokeKyc(
                                        TokenRevokeKycTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.tokenHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenAssociate(
                                        TokenAssociateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test -> verify(test.tokenHandler).preHandle(any(),any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenDissociate(
                                        TokenDissociateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test -> verify(test.tokenHandler).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenFeeScheduleUpdate(
                                        TokenFeeScheduleUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.tokenHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenPause(TokenPauseTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test -> verify(test.tokenHandler).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenUnpause(TokenUnpauseTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test -> verify(test.tokenHandler).preHandle(any(), any())),

                // util
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeUtilPrng(UtilPrngTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test -> verify(test.utilHandler).preHandlePrng(any(), any())),

                // mixed
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeSystemDelete(
                                        SystemDeleteTransactionBody.newBuilder()
                                                .setContractID(ContractID.getDefaultInstance())
                                                .build())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.contractHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeSystemDelete(
                                        SystemDeleteTransactionBody.newBuilder()
                                                .setFileID(FileID.getDefaultInstance())
                                                .build())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.fileHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeSystemUndelete(
                                        SystemUndeleteTransactionBody.newBuilder()
                                                .setContractID(ContractID.getDefaultInstance())
                                                .build())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.contractHandler)
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeSystemUndelete(
                                        SystemUndeleteTransactionBody.newBuilder()
                                                .setFileID(FileID.getDefaultInstance())
                                                .build())
                                .build(),
                        (Consumer<PreHandleDispatcherTest>)
                                test ->
                                        verify(test.fileHandler)
                                                .preHandle(any(), any())));
    }
}
