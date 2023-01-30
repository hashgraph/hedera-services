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

import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.CryptoSignatureWaivers;
import com.hedera.node.app.service.token.impl.CryptoSignatureWaiversImpl;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.PreHandleDispatcher;
import com.hedera.node.app.spi.meta.PrehandleHandlerContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.workflows.prehandle.ReadableStatesTracker;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * A {@code Dispatcher} provides functionality to forward pre-check, pre-handle, and handle-requests
 * to the appropriate handler
 */
public class Dispatcher {

    // TODO: Intermediate solution until we find a better way to get the service-key
    private static final String TOKEN_SERVICE_KEY = new TokenServiceImpl().getServiceName();
    private static final String SCHEDULE_SERVICE_KEY = new ScheduleServiceImpl().getServiceName();
    public static final String TYPE_NOT_SUPPORTED = "This transaction type is not supported";

    private final Handlers handlers;

    private final CryptoSignatureWaivers cryptoSignatureWaivers;

    /**
     * Constructor of {@code Dispatcher}
     *
     * @param handlers a {@link Handlers} record with all available handlers
     * @throws NullPointerException if one of the parameters is {@code null}
     */
    public Dispatcher(
            @NonNull final Handlers handlers, @NonNull final PreHandleContext preHandleContext) {
        this.handlers = requireNonNull(handlers);
        this.cryptoSignatureWaivers =
                new CryptoSignatureWaiversImpl(preHandleContext.accountNumbers());
    }

    /**
     * Dispatch a pre-handle request. It is forwarded to the correct handler, which takes care of
     * the specific functionality
     *
     * @param handlerContext the context of the pre-handle workflow
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void dispatchPreHandle(
            @NonNull final ReadableStatesTracker statesTracker,
            @NonNull final PrehandleHandlerContext handlerContext) {
        requireNonNull(statesTracker);
        requireNonNull(handlerContext);

        final var txBody = handlerContext.getTxn();
        switch (txBody.getDataCase()) {
            case CONSENSUSCREATETOPIC -> handlers.consensusCreateTopicHandler()
                    .preHandle(handlerContext);
            case CONSENSUSUPDATETOPIC -> handlers.consensusUpdateTopicHandler()
                    .preHandle(handlerContext);
            case CONSENSUSDELETETOPIC -> handlers.consensusDeleteTopicHandler()
                    .preHandle(handlerContext);
            case CONSENSUSSUBMITMESSAGE -> handlers.consensusSubmitMessageHandler()
                    .preHandle(handlerContext);

            case CONTRACTCREATEINSTANCE -> handlers.contractCreateHandler()
                    .preHandle(handlerContext);
            case CONTRACTUPDATEINSTANCE -> handlers.contractUpdateHandler()
                    .preHandle(handlerContext);
            case CONTRACTCALL -> handlers.contractCallHandler().preHandle(handlerContext);
            case CONTRACTDELETEINSTANCE -> handlers.contractDeleteHandler()
                    .preHandle(handlerContext);
            case ETHEREUMTRANSACTION -> handlers.etherumTransactionHandler()
                    .preHandle(handlerContext);

            case CRYPTOCREATEACCOUNT -> handlers.cryptoCreateHandler().preHandle(handlerContext);
            case CRYPTOUPDATEACCOUNT -> handlers.cryptoUpdateHandler()
                    .preHandle(handlerContext, cryptoSignatureWaivers);
            case CRYPTOTRANSFER -> handlers.cryptoTransferHandler()
                    .preHandle(
                            handlerContext,
                            setupAccountStore(statesTracker),
                            setupTokenStore(statesTracker));
            case CRYPTODELETE -> handlers.cryptoDeleteHandler().preHandle(handlerContext);
            case CRYPTOAPPROVEALLOWANCE -> handlers.cryptoApproveAllowanceHandler()
                    .preHandle(handlerContext);
            case CRYPTODELETEALLOWANCE -> handlers.cryptoDeleteAllowanceHandler()
                    .preHandle(handlerContext);
            case CRYPTOADDLIVEHASH -> handlers.cryptoAddLiveHashHandler().preHandle(handlerContext);
            case CRYPTODELETELIVEHASH -> handlers.cryptoDeleteLiveHashHandler()
                    .preHandle(handlerContext);

            case FILECREATE -> handlers.fileCreateHandler().preHandle(handlerContext);
            case FILEUPDATE -> handlers.fileUpdateHandler().preHandle(handlerContext);
            case FILEDELETE -> handlers.fileDeleteHandler().preHandle(handlerContext);
            case FILEAPPEND -> handlers.fileAppendHandler().preHandle(handlerContext);

            case FREEZE -> handlers.freezeHandler().preHandle(handlerContext);

            case UNCHECKEDSUBMIT -> handlers.uncheckedSubmitHandler().preHandle(handlerContext);

            case SCHEDULECREATE -> handlers.scheduleCreateHandler()
                    .preHandle(handlerContext, setupPreHandleDispatcher(statesTracker));
            case SCHEDULESIGN -> handlers.scheduleSignHandler()
                    .preHandle(
                            handlerContext,
                            setupScheduleStore(statesTracker),
                            setupPreHandleDispatcher(statesTracker));
            case SCHEDULEDELETE -> handlers.scheduleDeleteHandler().preHandle(handlerContext);

            case TOKENCREATION -> handlers.tokenCreateHandler().preHandle(handlerContext);
            case TOKENUPDATE -> handlers.tokenUpdateHandler().preHandle(handlerContext);
            case TOKENMINT -> handlers.tokenMintHandler().preHandle(handlerContext);
            case TOKENBURN -> handlers.tokenBurnHandler().preHandle(handlerContext);
            case TOKENDELETION -> handlers.tokenDeleteHandler().preHandle(handlerContext);
            case TOKENWIPE -> handlers.tokenAccountWipeHandler().preHandle(handlerContext);
            case TOKENFREEZE -> handlers.tokenFreezeAccountHandler().preHandle(handlerContext);
            case TOKENUNFREEZE -> handlers.tokenUnfreezeAccountHandler().preHandle(handlerContext);
            case TOKENGRANTKYC -> handlers.tokenGrantKycToAccountHandler()
                    .preHandle(handlerContext);
            case TOKENREVOKEKYC -> handlers.tokenRevokeKycFromAccountHandler()
                    .preHandle(handlerContext);
            case TOKENASSOCIATE -> handlers.tokenAssociateToAccountHandler()
                    .preHandle(handlerContext);
            case TOKENDISSOCIATE -> handlers.tokenDissociateFromAccountHandler()
                    .preHandle(handlerContext);
            case TOKEN_FEE_SCHEDULE_UPDATE -> handlers.tokenFeeScheduleUpdateHandler()
                    .preHandle(handlerContext);
            case TOKEN_PAUSE -> handlers.tokenPauseHandler().preHandle(handlerContext);
            case TOKEN_UNPAUSE -> handlers.tokenUnpauseHandler().preHandle(handlerContext);

            case UTIL_PRNG -> handlers.utilPrngHandler().preHandle(handlerContext);

            case SYSTEMDELETE -> {
                switch (txBody.getSystemDelete().getIdCase()) {
                    case CONTRACTID -> handlers.contractSystemDeleteHandler()
                            .preHandle(handlerContext);
                    case FILEID -> handlers.fileSystemDeleteHandler().preHandle(handlerContext);
                    case ID_NOT_SET -> throw new IllegalArgumentException(
                            "SystemDelete without IdCase");
                }
            }
            case SYSTEMUNDELETE -> {
                switch (txBody.getSystemUndelete().getIdCase()) {
                    case CONTRACTID -> handlers.contractSystemUndeleteHandler()
                            .preHandle(handlerContext);
                    case FILEID -> handlers.fileSystemUndeleteHandler().preHandle(handlerContext);
                    case ID_NOT_SET -> throw new IllegalArgumentException(
                            "SystemUndelete without IdCase");
                }
            }

            default -> throw new UnsupportedOperationException(TYPE_NOT_SUPPORTED);
        }
    }

    private static ReadableAccountStore setupAccountStore(
            @NonNull final ReadableStatesTracker statesTracker) {
        final var tokenStates = statesTracker.getReadableStates(TOKEN_SERVICE_KEY);
        return new ReadableAccountStore(tokenStates);
    }

    private static ReadableScheduleStore setupScheduleStore(
            @NonNull final ReadableStatesTracker statesTracker) {
        final var scheduleStates = statesTracker.getReadableStates(SCHEDULE_SERVICE_KEY);
        return new ReadableScheduleStore(scheduleStates);
    }

    private ReadableTokenStore setupTokenStore(@NonNull final ReadableStatesTracker statesTracker) {
        final var tokenStates = statesTracker.getReadableStates(TOKEN_SERVICE_KEY);
        return new ReadableTokenStore(tokenStates);
    }

    private PreHandleDispatcher setupPreHandleDispatcher(
            @NonNull final ReadableStatesTracker statesTracker) {
        return (TransactionBody innerTxn, AccountID innerPayer) -> {
            final var accountStore = setupAccountStore(statesTracker);
            final var handlerContext =
                    new PrehandleHandlerContext(accountStore, innerTxn, innerPayer);
            dispatchPreHandle(statesTracker, handlerContext);
            return new TransactionMetadata(handlerContext, List.of());
        };
    }
}
