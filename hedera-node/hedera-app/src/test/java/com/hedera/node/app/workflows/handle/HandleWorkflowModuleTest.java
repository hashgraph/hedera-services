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

package com.hedera.node.app.workflows.handle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.admin.impl.components.AdminComponent;
import com.hedera.node.app.service.admin.impl.handlers.FreezeHandler;
import com.hedera.node.app.service.consensus.impl.components.ConsensusComponent;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusDeleteTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusUpdateTopicHandler;
import com.hedera.node.app.service.contract.impl.components.ContractComponent;
import com.hedera.node.app.service.contract.impl.handlers.ContractCallHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractCreateHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractDeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractSystemDeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractSystemUndeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractUpdateHandler;
import com.hedera.node.app.service.contract.impl.handlers.EtherumTransactionHandler;
import com.hedera.node.app.service.file.impl.components.FileComponent;
import com.hedera.node.app.service.file.impl.handlers.FileAppendHandler;
import com.hedera.node.app.service.file.impl.handlers.FileCreateHandler;
import com.hedera.node.app.service.file.impl.handlers.FileDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemUndeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileUpdateHandler;
import com.hedera.node.app.service.network.impl.components.NetworkComponent;
import com.hedera.node.app.service.network.impl.handlers.NetworkUncheckedSubmitHandler;
import com.hedera.node.app.service.schedule.impl.components.ScheduleComponent;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleCreateHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleSignHandler;
import com.hedera.node.app.service.token.impl.components.TokenComponent;
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
import com.hedera.node.app.service.util.impl.components.UtilComponent;
import com.hedera.node.app.service.util.impl.handlers.UtilPrngHandler;
import com.hedera.node.app.workflows.dispatcher.TransactionHandlers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleWorkflowModuleTest {
    @Mock
    private AdminComponent adminComponent;

    @Mock
    private ConsensusComponent consensusComponent;

    @Mock
    private FileComponent fileComponent;

    @Mock
    private NetworkComponent networkComponent;

    @Mock
    private ContractComponent contractComponent;

    @Mock
    private ScheduleComponent scheduleComponent;

    @Mock
    private TokenComponent tokenComponent;

    @Mock
    private UtilComponent utilComponent;

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

    @Test
    void usesComponentsToGetHandlers() {
        given(consensusComponent.consensusCreateTopicHandler()).willReturn(consensusCreateTopicHandler);
        given(consensusComponent.consensusUpdateTopicHandler()).willReturn(consensusUpdateTopicHandler);
        given(consensusComponent.consensusDeleteTopicHandler()).willReturn(consensusDeleteTopicHandler);
        given(consensusComponent.consensusSubmitMessageHandler()).willReturn(consensusSubmitMessageHandler);
        given(contractComponent.contractCreateHandler()).willReturn(contractCreateHandler);
        given(contractComponent.contractUpdateHandler()).willReturn(contractUpdateHandler);
        given(contractComponent.contractCallHandler()).willReturn(contractCallHandler);
        given(contractComponent.contractDeleteHandler()).willReturn(contractDeleteHandler);
        given(contractComponent.contractSystemDeleteHandler()).willReturn(contractSystemDeleteHandler);
        given(contractComponent.contractSystemUndeleteHandler()).willReturn(contractSystemUndeleteHandler);
        given(contractComponent.etherumTransactionHandler()).willReturn(etherumTransactionHandler);
        given(tokenComponent.cryptoCreateHandler()).willReturn(cryptoCreateHandler);
        given(tokenComponent.cryptoUpdateHandler()).willReturn(cryptoUpdateHandler);
        given(tokenComponent.cryptoTransferHandler()).willReturn(cryptoTransferHandler);
        given(tokenComponent.cryptoDeleteHandler()).willReturn(cryptoDeleteHandler);
        given(tokenComponent.cryptoApproveAllowanceHandler()).willReturn(cryptoApproveAllowanceHandler);
        given(tokenComponent.cryptoDeleteAllowanceHandler()).willReturn(cryptoDeleteAllowanceHandler);
        given(tokenComponent.cryptoAddLiveHashHandler()).willReturn(cryptoAddLiveHashHandler);
        given(tokenComponent.cryptoDeleteLiveHashHandler()).willReturn(cryptoDeleteLiveHashHandler);
        given(fileComponent.fileCreateHandler()).willReturn(fileCreateHandler);
        given(fileComponent.fileUpdateHandler()).willReturn(fileUpdateHandler);
        given(fileComponent.fileDeleteHandler()).willReturn(fileDeleteHandler);
        given(fileComponent.fileAppendHandler()).willReturn(fileAppendHandler);
        given(fileComponent.fileSystemDeleteHandler()).willReturn(fileSystemDeleteHandler);
        given(fileComponent.fileSystemUndeleteHandler()).willReturn(fileSystemUndeleteHandler);
        given(adminComponent.freezeHandler()).willReturn(freezeHandler);
        given(networkComponent.networkUncheckedSubmitHandler()).willReturn(networkUncheckedSubmitHandler);
        given(scheduleComponent.scheduleCreateHandler()).willReturn(scheduleCreateHandler);
        given(scheduleComponent.scheduleSignHandler()).willReturn(scheduleSignHandler);
        given(scheduleComponent.scheduleDeleteHandler()).willReturn(scheduleDeleteHandler);
        given(tokenComponent.tokenCreateHandler()).willReturn(tokenCreateHandler);
        given(tokenComponent.tokenUpdateHandler()).willReturn(tokenUpdateHandler);
        given(tokenComponent.tokenMintHandler()).willReturn(tokenMintHandler);
        given(tokenComponent.tokenBurnHandler()).willReturn(tokenBurnHandler);
        given(tokenComponent.tokenDeleteHandler()).willReturn(tokenDeleteHandler);
        given(tokenComponent.tokenAccountWipeHandler()).willReturn(tokenAccountWipeHandler);
        given(tokenComponent.tokenFreezeAccountHandler()).willReturn(tokenFreezeAccountHandler);
        given(tokenComponent.tokenUnfreezeAccountHandler()).willReturn(tokenUnfreezeAccountHandler);
        given(tokenComponent.tokenGrantKycToAccountHandler()).willReturn(tokenGrantKycToAccountHandler);
        given(tokenComponent.tokenRevokeKycFromAccountHandler()).willReturn(tokenRevokeKycFromAccountHandler);
        given(tokenComponent.tokenAssociateToAccountHandler()).willReturn(tokenAssociateToAccountHandler);
        given(tokenComponent.tokenDissociateFromAccountHandler()).willReturn(tokenDissociateFromAccountHandler);
        given(tokenComponent.tokenFeeScheduleUpdateHandler()).willReturn(tokenFeeScheduleUpdateHandler);
        given(tokenComponent.tokenPauseHandler()).willReturn(tokenPauseHandler);
        given(tokenComponent.tokenUnpauseHandler()).willReturn(tokenUnpauseHandler);
        given(utilComponent.prngHandler()).willReturn(utilPrngHandler);

        final var handlers = HandleWorkflowModule.provideTransactionHandlers(
                adminComponent,
                consensusComponent,
                fileComponent,
                networkComponent,
                contractComponent,
                scheduleComponent,
                tokenComponent,
                utilComponent);
        assertInstanceOf(TransactionHandlers.class, handlers);
    }
}
