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

import com.hedera.node.app.service.admin.impl.components.AdminComponent;
import com.hedera.node.app.service.consensus.impl.components.ConsensusComponent;
import com.hedera.node.app.service.contract.impl.components.ContractComponent;
import com.hedera.node.app.service.file.impl.components.FileComponent;
import com.hedera.node.app.service.network.impl.components.NetworkComponent;
import com.hedera.node.app.service.schedule.impl.components.ScheduleComponent;
import com.hedera.node.app.service.token.impl.components.TokenComponent;
import com.hedera.node.app.service.util.impl.components.UtilComponent;
import com.hedera.node.app.workflows.dispatcher.TransactionHandlers;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Singleton;

@Module
public interface HandlersModule {
    @Provides
    @Singleton
    static TransactionHandlers provideTransactionHandlers(
            @NonNull AdminComponent adminComponent,
            @NonNull ConsensusComponent consensusComponent,
            @NonNull FileComponent fileComponent,
            @NonNull NetworkComponent networkComponent,
            @NonNull ContractComponent contractComponent,
            @NonNull ScheduleComponent scheduleComponent,
            @NonNull TokenComponent tokenComponent,
            @NonNull UtilComponent utilComponent) {
        return new TransactionHandlers(
                consensusComponent.consensusCreateTopicHandler(),
                consensusComponent.consensusUpdateTopicHandler(),
                consensusComponent.consensusDeleteTopicHandler(),
                consensusComponent.consensusSubmitMessageHandler(),
                contractComponent.contractCreateHandler(),
                contractComponent.contractUpdateHandler(),
                contractComponent.contractCallHandler(),
                contractComponent.contractDeleteHandler(),
                contractComponent.contractSystemDeleteHandler(),
                contractComponent.contractSystemUndeleteHandler(),
                contractComponent.etherumTransactionHandler(),
                tokenComponent.cryptoCreateHandler(),
                tokenComponent.cryptoUpdateHandler(),
                tokenComponent.cryptoTransferHandler(),
                tokenComponent.cryptoDeleteHandler(),
                tokenComponent.cryptoApproveAllowanceHandler(),
                tokenComponent.cryptoDeleteAllowanceHandler(),
                tokenComponent.cryptoAddLiveHashHandler(),
                tokenComponent.cryptoDeleteLiveHashHandler(),
                fileComponent.fileCreateHandler(),
                fileComponent.fileUpdateHandler(),
                fileComponent.fileDeleteHandler(),
                fileComponent.fileAppendHandler(),
                fileComponent.fileSystemDeleteHandler(),
                fileComponent.fileSystemUndeleteHandler(),
                adminComponent.freezeHandler(),
                networkComponent.networkUncheckedSubmitHandler(),
                scheduleComponent.scheduleCreateHandler(),
                scheduleComponent.scheduleSignHandler(),
                scheduleComponent.scheduleDeleteHandler(),
                tokenComponent.tokenCreateHandler(),
                tokenComponent.tokenUpdateHandler(),
                tokenComponent.tokenMintHandler(),
                tokenComponent.tokenBurnHandler(),
                tokenComponent.tokenDeleteHandler(),
                tokenComponent.tokenAccountWipeHandler(),
                tokenComponent.tokenFreezeAccountHandler(),
                tokenComponent.tokenUnfreezeAccountHandler(),
                tokenComponent.tokenGrantKycToAccountHandler(),
                tokenComponent.tokenRevokeKycFromAccountHandler(),
                tokenComponent.tokenAssociateToAccountHandler(),
                tokenComponent.tokenDissociateFromAccountHandler(),
                tokenComponent.tokenFeeScheduleUpdateHandler(),
                tokenComponent.tokenPauseHandler(),
                tokenComponent.tokenUnpauseHandler(),
                utilComponent.prngHandler());
    }
}
