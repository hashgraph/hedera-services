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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.admin.impl.handlers.FreezeHandler;
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
import com.hedera.node.app.service.network.impl.handlers.NetworkUncheckedSubmitHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleCreateHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleSignHandler;
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
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import edu.umd.cs.findbugs.annotations.NonNull;

/** A factory that creates all handlers and bundles them in a {@link TransactionHandlers} */
public final class HandlersFactory {

    private HandlersFactory() {}

    /**
     * This method creates all handlers with the provided arguments and bundles them in a {@link
     * TransactionHandlers} record.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @return the created handlers
     */
    public static TransactionHandlers createHandlers(
            @NonNull final HederaAccountNumbers hederaAccountNumbers,
            @NonNull final HederaFileNumbers hederaFileNumbers) {
        requireNonNull(hederaAccountNumbers);
        requireNonNull(hederaFileNumbers);

        // TODO - provide DI, c.f. https://github.com/hashgraph/hedera-services/issues/4317
        return new TransactionHandlers(
                new ConsensusCreateTopicHandler(),
                new ConsensusUpdateTopicHandler(),
                new ConsensusDeleteTopicHandler(),
                new ConsensusSubmitMessageHandler(),
                new ContractCreateHandler(),
                new ContractUpdateHandler(),
                new ContractCallHandler(),
                new ContractDeleteHandler(),
                new ContractSystemDeleteHandler(),
                new ContractSystemUndeleteHandler(),
                new EtherumTransactionHandler(),
                new CryptoCreateHandler(),
                new CryptoUpdateHandler(),
                new CryptoTransferHandler(),
                new CryptoDeleteHandler(),
                new CryptoApproveAllowanceHandler(),
                new CryptoDeleteAllowanceHandler(),
                new CryptoAddLiveHashHandler(),
                new CryptoDeleteLiveHashHandler(),
                new FileCreateHandler(),
                new FileUpdateHandler(),
                new FileDeleteHandler(),
                new FileAppendHandler(),
                new FileSystemDeleteHandler(),
                new FileSystemUndeleteHandler(),
                new FreezeHandler(),
                new NetworkUncheckedSubmitHandler(),
                new ScheduleCreateHandler(),
                new ScheduleSignHandler(),
                new ScheduleDeleteHandler(),
                new TokenCreateHandler(),
                new TokenUpdateHandler(),
                new TokenMintHandler(),
                new TokenBurnHandler(),
                new TokenDeleteHandler(),
                new TokenAccountWipeHandler(),
                new TokenFreezeAccountHandler(),
                new TokenUnfreezeAccountHandler(),
                new TokenGrantKycToAccountHandler(),
                new TokenRevokeKycFromAccountHandler(),
                new TokenAssociateToAccountHandler(),
                new TokenDissociateFromAccountHandler(),
                new TokenFeeScheduleUpdateHandler(),
                new TokenPauseHandler(),
                new TokenUnpauseHandler(),
                new UtilPrngHandler());
    }
}
