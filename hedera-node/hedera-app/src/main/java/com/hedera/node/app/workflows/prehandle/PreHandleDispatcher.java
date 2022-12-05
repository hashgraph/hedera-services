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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.ServicesAccessor;
import com.hedera.node.app.service.admin.FreezePreTransactionHandler;
import com.hedera.node.app.service.consensus.ConsensusPreTransactionHandler;
import com.hedera.node.app.service.contract.ContractPreTransactionHandler;
import com.hedera.node.app.service.file.FilePreTransactionHandler;
import com.hedera.node.app.service.network.NetworkPreTransactionHandler;
import com.hedera.node.app.service.scheduled.SchedulePreTransactionHandler;
import com.hedera.node.app.service.token.CryptoPreTransactionHandler;
import com.hedera.node.app.service.token.TokenPreTransactionHandler;
import com.hedera.node.app.service.util.UtilPreTransactionHandler;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.state.HederaState;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code PreHandleDispatcher} takes a validated transaction and dispatches it to the correct
 * handler
 */
public final class PreHandleDispatcher {

    private final ConsensusPreTransactionHandler consensusHandler;
    private final ContractPreTransactionHandler contractHandler;
    private final CryptoPreTransactionHandler cryptoHandler;
    private final FilePreTransactionHandler fileHandler;
    private final FreezePreTransactionHandler freezeHandler;
    private final NetworkPreTransactionHandler networkHandler;
    private final SchedulePreTransactionHandler scheduleHandler;
    private final TokenPreTransactionHandler tokenHandler;
    private final UtilPreTransactionHandler utilHandler;

    /**
     * Constructor of {@code PreHandleDispatcherImpl}
     *
     * @param hederaState the {@link HederaState} this dispatcher is bound to
     * @param services the {@link ServicesAccessor} with all available services
     * @throws NullPointerException if one of the parameters is {@code null}
     */
    public PreHandleDispatcher(
            @NonNull final HederaState hederaState,
            @NonNull final ServicesAccessor services,
            @NonNull final PreHandleContext context) {
        requireNonNull(hederaState);
        requireNonNull(services);
        requireNonNull(context);
        final var consensusState = hederaState.createReadableStates(HederaState.CONSENSUS_SERVICE);
        consensusHandler =
                services.consensusService().createPreTransactionHandler(consensusState, context);

        final var contractState = hederaState.createReadableStates(HederaState.CONTRACT_SERVICE);
        contractHandler =
                services.contractService().createPreTransactionHandler(contractState, context);

        final var cryptoStates = hederaState.createReadableStates(HederaState.CRYPTO_SERVICE);
        cryptoHandler = services.cryptoService().createPreTransactionHandler(cryptoStates, context);

        final var fileStates = hederaState.createReadableStates(HederaState.FILE_SERVICE);
        fileHandler = services.fileService().createPreTransactionHandler(fileStates, context);

        final var freezeState = hederaState.createReadableStates(HederaState.FREEZE_SERVICE);
        freezeHandler = services.freezeService().createPreTransactionHandler(freezeState, context);

        final var networkState = hederaState.createReadableStates(HederaState.NETWORK_SERVICE);
        networkHandler =
                services.networkService().createPreTransactionHandler(networkState, context);

        final var scheduledState = hederaState.createReadableStates(HederaState.SCHEDULE_SERVICE);
        scheduleHandler =
                services.scheduleService().createPreTransactionHandler(scheduledState, context);

        final var tokenStates = hederaState.createReadableStates(HederaState.TOKEN_SERVICE);
        tokenHandler = services.tokenService().createPreTransactionHandler(tokenStates, context);

        final var utilStates = hederaState.createReadableStates(HederaState.UTIL_SERVICE);
        utilHandler = services.utilService().createPreTransactionHandler(utilStates, context);
    }

    /**
     * Dispatch a request. It is forwarded to the correct handler, which takes care of the specific
     * functionality
     *
     * @param transactionBody the {@link TransactionBody} of the request
     * @throws NullPointerException if {@code transactionBody} is {@code null}
     */
    @NonNull
    public TransactionMetadata dispatch(@NonNull final TransactionBody transactionBody) {
        requireNonNull(transactionBody);
        final var payer = transactionBody.getTransactionID().getAccountID();
        return switch (transactionBody.getDataCase()) {
            case CONSENSUSCREATETOPIC,
                    CONSENSUSUPDATETOPIC,
                    CONSENSUSDELETETOPIC,
                    CONSENSUSSUBMITMESSAGE -> consensusHandler.preHandle(transactionBody, payer);
            case CONTRACTCREATEINSTANCE,
                    CONTRACTUPDATEINSTANCE,
                    CONTRACTCALL,
                    CONTRACTDELETEINSTANCE,
                    ETHEREUMTRANSACTION -> contractHandler.preHandle(transactionBody, payer);
            case CRYPTOCREATEACCOUNT,
                    CRYPTOUPDATEACCOUNT,
                    CRYPTOTRANSFER,
                    CRYPTODELETE,
                    CRYPTOAPPROVEALLOWANCE,
                    CRYPTODELETEALLOWANCE,
                    CRYPTOADDLIVEHASH,
                    CRYPTODELETELIVEHASH -> cryptoHandler.preHandle(transactionBody, payer);
            case FILECREATE, FILEUPDATE, FILEDELETE, FILEAPPEND -> fileHandler.preHandle(
                    transactionBody, payer);
            case FREEZE -> freezeHandler.preHandleFreeze(transactionBody, payer);
            case UNCHECKEDSUBMIT -> networkHandler.preHandle(transactionBody, payer);
            case SCHEDULECREATE, SCHEDULESIGN, SCHEDULEDELETE -> scheduleHandler.preHandle(
                    transactionBody, payer);
            case TOKENCREATION,
                    TOKENUPDATE,
                    TOKENMINT,
                    TOKENBURN,
                    TOKENDELETION,
                    TOKENWIPE,
                    TOKENFREEZE,
                    TOKENUNFREEZE,
                    TOKENGRANTKYC,
                    TOKENREVOKEKYC,
                    TOKENASSOCIATE,
                    TOKENDISSOCIATE,
                    TOKEN_FEE_SCHEDULE_UPDATE,
                    TOKEN_PAUSE,
                    TOKEN_UNPAUSE -> tokenHandler.preHandle(transactionBody, payer);
            case UTIL_PRNG -> utilHandler.preHandlePrng(transactionBody, payer);
            case SYSTEMDELETE -> switch (transactionBody.getSystemDelete().getIdCase()) {
                case CONTRACTID -> contractHandler.preHandle(transactionBody, payer);
                case FILEID -> fileHandler.preHandle(transactionBody, payer);
                case ID_NOT_SET -> throw new IllegalArgumentException(
                        "SystemDelete without IdCase");
            };
            case SYSTEMUNDELETE -> switch (transactionBody.getSystemUndelete().getIdCase()) {
                case CONTRACTID -> contractHandler.preHandle(transactionBody, payer);
                case FILEID -> fileHandler.preHandle(transactionBody, payer);
                case ID_NOT_SET -> throw new IllegalArgumentException(
                        "SystemUndelete without IdCase");
            };

            case NODE_STAKE_UPDATE, DATA_NOT_SET -> throw new UnsupportedOperationException(
                    "Not implemented");
        };
    }
}
