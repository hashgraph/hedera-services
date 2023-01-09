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

import com.hedera.node.app.service.token.CryptoSignatureWaivers;
import com.hedera.node.app.service.token.impl.CryptoSignatureWaiversImpl;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.StoreCache;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

import static java.util.Objects.requireNonNull;

/**
 * A {@code Dispatcher} provides functionality to forward pre-check, pre-handle, and handle-requests
 * to the appropriate handler
 */
public class Dispatcher {

    private final Handlers handlers;

    private final StoreCache storeCache;
    private final CryptoSignatureWaivers cryptoSignatureWaivers;

    /**
     * Constructor of {@code Dispatcher}
     *
     * @param handlers a {@link Handlers} record with all available handlers
     * @param storeCache a {@link StoreCache} that maintains stores for all active {@link
     *     HederaState}s
     * @throws NullPointerException if one of the parameters is {@code null}
     */
    public Dispatcher(
            @NonNull final Handlers handlers,
            @NonNull final StoreCache storeCache,
            @NonNull final PreHandleContext preHandleContext) {
        this.handlers = requireNonNull(handlers);
        this.storeCache = requireNonNull(storeCache);
        this.cryptoSignatureWaivers =
                new CryptoSignatureWaiversImpl(preHandleContext.accountNumbers());
    }

    /**
     * Dispatch a pre-handle request. It is forwarded to the correct handler, which takes care of
     * the specific functionality
     *
     * @param state the {@link HederaState} of this request
     * @param transactionBody the {@link TransactionBody} of the request
     * @param payer the {@link AccountID} of the payer
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @NonNull
    public TransactionMetadata dispatchPreHandle(
            @NonNull final HederaState state,
            @NonNull final TransactionBody transactionBody,
            @NonNull final AccountID payer) {
        requireNonNull(state);
        requireNonNull(transactionBody);
        requireNonNull(payer);

        return switch (transactionBody.getDataCase()) {
            case CONSENSUSCREATETOPIC -> handlers.consensusCreateTopicHandler()
                    .preHandle(transactionBody, payer);
            case CONSENSUSUPDATETOPIC -> handlers.consensusUpdateTopicHandler()
                    .preHandle(transactionBody, payer);
            case CONSENSUSDELETETOPIC -> handlers.consensusDeleteTopicHandler()
                    .preHandle(transactionBody, payer);
            case CONSENSUSSUBMITMESSAGE -> handlers.consensusSubmitMessageHandler()
                    .preHandle(transactionBody, payer);

            case CONTRACTCREATEINSTANCE -> handlers.contractCreateHandler()
                    .preHandle(transactionBody, payer, storeCache.getAccountStore(state));
            case CONTRACTUPDATEINSTANCE -> handlers.contractUpdateHandler()
                    .preHandle(transactionBody, payer);
            case CONTRACTCALL -> handlers.contractCallHandler()
                    .preHandle(transactionBody, payer, storeCache.getAccountStore(state));
            case CONTRACTDELETEINSTANCE -> handlers.contractDeleteHandler()
                    .preHandle(transactionBody, payer);
            case ETHEREUMTRANSACTION -> handlers.etherumTransactionHandler()
                    .preHandle(transactionBody, payer);

            case CRYPTOCREATEACCOUNT -> handlers.cryptoCreateHandler()
                    .preHandle(transactionBody, payer, storeCache.getAccountStore(state));
            case CRYPTOUPDATEACCOUNT -> handlers.cryptoUpdateHandler()
                    .preHandle(
                            transactionBody,
                            payer,
                            storeCache.getAccountStore(state),
                            cryptoSignatureWaivers);
            case CRYPTOTRANSFER -> handlers.cryptoTransferHandler()
                    .preHandle(
                            transactionBody,
                            payer,
                            storeCache.getAccountStore(state),
                            storeCache.getTokenStore(state));
            case CRYPTODELETE -> handlers.cryptoDeleteHandler()
                    .preHandle(transactionBody, payer, storeCache.getAccountStore(state));
            case CRYPTOAPPROVEALLOWANCE -> handlers.cryptoApproveAllowanceHandler()
                    .preHandle(transactionBody, payer, storeCache.getAccountStore(state));
            case CRYPTODELETEALLOWANCE -> handlers.cryptoDeleteAllowanceHandler()
                    .preHandle(transactionBody, payer, storeCache.getAccountStore(state));
            case CRYPTOADDLIVEHASH -> handlers.cryptoAddLiveHashHandler()
                    .preHandle(transactionBody, payer);
            case CRYPTODELETELIVEHASH -> handlers.cryptoDeleteLiveHashHandler()
                    .preHandle(transactionBody, payer);

            case FILECREATE -> handlers.fileCreateHandler().preHandle(transactionBody, payer);
            case FILEUPDATE -> handlers.fileUpdateHandler().preHandle(transactionBody, payer);
            case FILEDELETE -> handlers.fileDeleteHandler().preHandle(transactionBody, payer);
            case FILEAPPEND -> handlers.fileAppendHandler().preHandle(transactionBody, payer);

            case FREEZE -> handlers.freezeHandler().preHandle(transactionBody, payer);

            case UNCHECKEDSUBMIT -> handlers.uncheckedSubmitHandler()
                    .preHandle(transactionBody, payer);

            case SCHEDULECREATE -> handlers.scheduleCreateHandler()
                    .preHandle(
                            transactionBody,
                            payer,
                            storeCache.getAccountStore(state),
                            (innerTxn, innerPayer) ->
                                    dispatchPreHandle(state, innerTxn, innerPayer));
            case SCHEDULESIGN -> handlers.scheduleSignHandler()
                    .preHandle(
                            transactionBody,
                            payer,
                            storeCache.getAccountStore(state),
                            storeCache.getScheduleStore(state),
                            (innerTxn, innerPayer) ->
                                    dispatchPreHandle(state, innerTxn, innerPayer));
            case SCHEDULEDELETE -> handlers.scheduleDeleteHandler()
                    .preHandle(transactionBody, payer);

            case TOKENCREATION -> handlers.tokenCreateHandler().preHandle(transactionBody, payer);
            case TOKENUPDATE -> handlers.tokenUpdateHandler().preHandle(transactionBody, payer);
            case TOKENMINT -> handlers.tokenMintHandler().preHandle(transactionBody, payer);
            case TOKENBURN -> handlers.tokenBurnHandler().preHandle(transactionBody, payer);
            case TOKENDELETION -> handlers.tokenDeleteHandler().preHandle(transactionBody, payer);
            case TOKENWIPE -> handlers.tokenAccountWipeHandler().preHandle(transactionBody, payer);
            case TOKENFREEZE -> handlers.tokenFreezeAccountHandler()
                    .preHandle(transactionBody, payer);
            case TOKENUNFREEZE -> handlers.tokenUnfreezeAccountHandler()
                    .preHandle(transactionBody, payer);
            case TOKENGRANTKYC -> handlers.tokenGrantKycToAccountHandler()
                    .preHandle(transactionBody, payer);
            case TOKENREVOKEKYC -> handlers.tokenRevokeKycFromAccountHandler()
                    .preHandle(transactionBody, payer);
            case TOKENASSOCIATE -> handlers.tokenAssociateToAccountHandler()
                    .preHandle(transactionBody, payer);
            case TOKENDISSOCIATE -> handlers.tokenDissociateFromAccountHandler()
                    .preHandle(transactionBody, payer);
            case TOKEN_FEE_SCHEDULE_UPDATE -> handlers.tokenFeeScheduleUpdateHandler()
                    .preHandle(transactionBody, payer);
            case TOKEN_PAUSE -> handlers.tokenPauseHandler().preHandle(transactionBody, payer);
            case TOKEN_UNPAUSE -> handlers.tokenUnpauseHandler().preHandle(transactionBody, payer);

            case UTIL_PRNG -> handlers.utilPrngHandler().preHandle(transactionBody, payer);

            case SYSTEMDELETE -> switch (transactionBody.getSystemDelete().getIdCase()) {
                case CONTRACTID -> handlers.contractSystemDeleteHandler()
                        .preHandle(transactionBody, payer);
                case FILEID -> handlers.fileSystemDeleteHandler().preHandle(transactionBody, payer);
                case ID_NOT_SET -> throw new IllegalArgumentException(
                        "SystemDelete without IdCase");
            };
            case SYSTEMUNDELETE -> switch (transactionBody.getSystemUndelete().getIdCase()) {
                case CONTRACTID -> handlers.contractSystemUndeleteHandler()
                        .preHandle(transactionBody, payer);
                case FILEID -> handlers.fileSystemUndeleteHandler()
                        .preHandle(transactionBody, payer);
                case ID_NOT_SET -> throw new IllegalArgumentException(
                        "SystemUndelete without IdCase");
            };

            case NODE_STAKE_UPDATE, DATA_NOT_SET -> throw new UnsupportedOperationException(
                    "Not implemented");
        };
    }
}
