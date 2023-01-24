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
import com.hedera.node.app.spi.meta.ScheduleSigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflowContext;
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

    private final Handlers handlers;

    private final CryptoSignatureWaivers cryptoSignatureWaivers;

    /**
     * Constructor of {@code Dispatcher}
     *
     * @param handlers a {@link Handlers} record with all available handlers
     * @throws NullPointerException if one of the parameters is {@code null}
     */
    public Dispatcher(
            @NonNull final Handlers handlers,
            @NonNull final PreHandleContext preHandleContext) {
        this.handlers = requireNonNull(handlers);
        this.cryptoSignatureWaivers =
                new CryptoSignatureWaiversImpl(preHandleContext.accountNumbers());
    }

    /**
     * Dispatch a pre-handle request. It is forwarded to the correct handler, which takes care of
     * the specific functionality
     *
     * @param context the context of the pre-handle workflow
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void dispatchPreHandle(@NonNull final PreHandleWorkflowContext context) {
        requireNonNull(context);

        switch (context.getTxBody().getDataCase()) {
            case CONSENSUSCREATETOPIC -> handlers.consensusCreateTopicHandler()
                    .preHandle(setupRegularBuilder(context));
            case CONSENSUSUPDATETOPIC -> handlers.consensusUpdateTopicHandler()
                    .preHandle(setupRegularBuilder(context));
            case CONSENSUSDELETETOPIC -> handlers.consensusDeleteTopicHandler()
                    .preHandle(setupRegularBuilder(context));
            case CONSENSUSSUBMITMESSAGE -> handlers.consensusSubmitMessageHandler()
                    .preHandle(setupRegularBuilder(context));

            case CONTRACTCREATEINSTANCE -> handlers.contractCreateHandler()
                    .preHandle(setupRegularBuilder(context));
            case CONTRACTUPDATEINSTANCE -> handlers.contractUpdateHandler()
                    .preHandle(setupRegularBuilder(context));
            case CONTRACTCALL -> handlers.contractCallHandler()
                    .preHandle(setupRegularBuilder(context));
            case CONTRACTDELETEINSTANCE -> handlers.contractDeleteHandler()
                    .preHandle(setupRegularBuilder(context));
            case ETHEREUMTRANSACTION -> handlers.etherumTransactionHandler()
                    .preHandle(setupRegularBuilder(context));

            case CRYPTOCREATEACCOUNT -> handlers.cryptoCreateHandler()
                    .preHandle(setupRegularBuilder(context));
            case CRYPTOUPDATEACCOUNT -> handlers.cryptoUpdateHandler()
                    .preHandle(
                            setupRegularBuilder(context),
                            cryptoSignatureWaivers);
            case CRYPTOTRANSFER -> handlers.cryptoTransferHandler()
                        .preHandle(
                                setupRegularBuilder(context),
                                setupAccountStore(context),
                                setupTokenStore(context));
            case CRYPTODELETE -> handlers.cryptoDeleteHandler()
                    .preHandle(setupRegularBuilder(context));
            case CRYPTOAPPROVEALLOWANCE -> handlers.cryptoApproveAllowanceHandler()
                    .preHandle(setupRegularBuilder(context));
            case CRYPTODELETEALLOWANCE -> handlers.cryptoDeleteAllowanceHandler()
                    .preHandle(setupRegularBuilder(context));
            case CRYPTOADDLIVEHASH -> handlers.cryptoAddLiveHashHandler()
                    .preHandle(setupRegularBuilder(context));
            case CRYPTODELETELIVEHASH -> handlers.cryptoDeleteLiveHashHandler()
                    .preHandle(setupRegularBuilder(context));

            case FILECREATE -> handlers.fileCreateHandler().preHandle(setupRegularBuilder(context));
            case FILEUPDATE -> handlers.fileUpdateHandler().preHandle(setupRegularBuilder(context));
            case FILEDELETE -> handlers.fileDeleteHandler().preHandle(setupRegularBuilder(context));
            case FILEAPPEND -> handlers.fileAppendHandler().preHandle(setupRegularBuilder(context));

            case FREEZE -> handlers.freezeHandler().preHandle(setupRegularBuilder(context));

            case UNCHECKEDSUBMIT -> handlers.uncheckedSubmitHandler()
                    .preHandle(setupRegularBuilder(context));

            case SCHEDULECREATE -> handlers.scheduleCreateHandler()
                    .preHandle(
                            setupScheduledBuilder(context),
                            setupPreHandleDispatcher(context));
            case SCHEDULESIGN -> handlers.scheduleSignHandler()
                    .preHandle(
                            setupScheduledBuilder(context),
                            setupScheduleStore(context),
                            setupPreHandleDispatcher(context));
            case SCHEDULEDELETE -> handlers.scheduleDeleteHandler()
                    .preHandle(setupScheduledBuilder(context));

            case TOKENCREATION -> handlers.tokenCreateHandler().preHandle(setupRegularBuilder(context));
            case TOKENUPDATE -> handlers.tokenUpdateHandler().preHandle(setupRegularBuilder(context));
            case TOKENMINT -> handlers.tokenMintHandler().preHandle(setupRegularBuilder(context));
            case TOKENBURN -> handlers.tokenBurnHandler().preHandle(setupRegularBuilder(context));
            case TOKENDELETION -> handlers.tokenDeleteHandler().preHandle(setupRegularBuilder(context));
            case TOKENWIPE -> handlers.tokenAccountWipeHandler().preHandle(setupRegularBuilder(context));
            case TOKENFREEZE -> handlers.tokenFreezeAccountHandler()
                    .preHandle(setupRegularBuilder(context));
            case TOKENUNFREEZE -> handlers.tokenUnfreezeAccountHandler()
                    .preHandle(setupRegularBuilder(context));
            case TOKENGRANTKYC -> handlers.tokenGrantKycToAccountHandler()
                    .preHandle(setupRegularBuilder(context));
            case TOKENREVOKEKYC -> handlers.tokenRevokeKycFromAccountHandler()
                    .preHandle(setupRegularBuilder(context));
            case TOKENASSOCIATE -> handlers.tokenAssociateToAccountHandler()
                    .preHandle(setupRegularBuilder(context));
            case TOKENDISSOCIATE -> handlers.tokenDissociateFromAccountHandler()
                    .preHandle(setupRegularBuilder(context));
            case TOKEN_FEE_SCHEDULE_UPDATE -> handlers.tokenFeeScheduleUpdateHandler()
                    .preHandle(setupRegularBuilder(context));
            case TOKEN_PAUSE -> handlers.tokenPauseHandler().preHandle(setupRegularBuilder(context));
            case TOKEN_UNPAUSE -> handlers.tokenUnpauseHandler().preHandle(setupRegularBuilder(context));

            case UTIL_PRNG -> handlers.utilPrngHandler().preHandle(setupRegularBuilder(context));

            case SYSTEMDELETE -> {
                switch (context.getTxBody().getSystemDelete().getIdCase()) {
                    case CONTRACTID -> handlers.contractSystemDeleteHandler()
                            .preHandle(setupRegularBuilder(context));
                    case FILEID -> handlers.fileSystemDeleteHandler().preHandle(setupRegularBuilder(context));
                    case ID_NOT_SET -> throw new IllegalArgumentException(
                            "SystemDelete without IdCase");
                }
            }
            case SYSTEMUNDELETE -> {
                switch (context.getTxBody().getSystemUndelete().getIdCase()) {
                    case CONTRACTID -> handlers.contractSystemUndeleteHandler()
                            .preHandle(setupRegularBuilder(context));
                    case FILEID -> handlers.fileSystemUndeleteHandler()
                            .preHandle(setupRegularBuilder(context));
                    case ID_NOT_SET -> throw new IllegalArgumentException(
                            "SystemUndelete without IdCase");
                }
            }

            case NODE_STAKE_UPDATE, DATA_NOT_SET -> throw new UnsupportedOperationException(
                    "Not implemented");
        }
    }

    private static SigTransactionMetadataBuilder setupRegularBuilder(@NonNull final PreHandleWorkflowContext context) {
        final var tokenReadableStates = context.getReadableStates(TOKEN_SERVICE_KEY);
        final var accountStore = new ReadableAccountStore(tokenReadableStates);
        final var result = new SigTransactionMetadataBuilder(accountStore)
                .txnBody(context.getTxBody())
                .payerKeyFor(context.getPayerID());
        context.setMetadataBuilder(result);
        return result;
    }

    private static ScheduleSigTransactionMetadataBuilder setupScheduledBuilder(@NonNull final PreHandleWorkflowContext context) {
        final var tokenReadableStates = context.getReadableStates(TOKEN_SERVICE_KEY);
        final var accountStore = new ReadableAccountStore(tokenReadableStates);
        final var result = new ScheduleSigTransactionMetadataBuilder(accountStore)
                .txnBody(context.getTxBody())
                .payerKeyFor(context.getPayerID());
        context.setMetadataBuilder(result);
        return result;
    }

    private static ReadableAccountStore setupAccountStore(@NonNull final PreHandleWorkflowContext context) {
        final var tokenStates = context.getReadableStates(TOKEN_SERVICE_KEY);
        return new ReadableAccountStore(tokenStates);
    }

    private static ReadableScheduleStore setupScheduleStore(@NonNull final PreHandleWorkflowContext context) {
        final var scheduleStates = context.getReadableStates(SCHEDULE_SERVICE_KEY);
        return new ReadableScheduleStore(scheduleStates);
    }

    private ReadableTokenStore setupTokenStore(@NonNull final PreHandleWorkflowContext context) {
        final var tokenStates = context.getReadableStates(TOKEN_SERVICE_KEY);
        return new ReadableTokenStore(tokenStates);
    }

    private PreHandleDispatcher setupPreHandleDispatcher(@NonNull final PreHandleWorkflowContext context) {
        return (TransactionBody innerTxn, AccountID innerPayer) -> {
            final var nestedContext = context.createNestedContext(innerTxn, innerPayer);
            dispatchPreHandle(nestedContext);
            return nestedContext.getMetadataBuilder().build();
        };
    }
}
