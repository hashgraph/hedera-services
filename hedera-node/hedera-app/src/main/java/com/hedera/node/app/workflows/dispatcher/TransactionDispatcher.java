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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.CryptoSignatureWaivers;
import com.hedera.node.app.service.token.impl.CryptoSignatureWaiversImpl;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.StoreCache;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code TransactionDispatcher} provides functionality to forward pre-check, pre-handle, and
 * handle-transaction requests to the appropriate handler
 */
public class TransactionDispatcher {

    private final TransactionHandlers handlers;

    private final StoreCache storeCache;
    private final CryptoSignatureWaivers cryptoSignatureWaivers;

    /**
     * Constructor of {@code TransactionDispatcher}
     *
     * @param handlers a {@link TransactionHandlers} record with all available handlers
     * @param storeCache a {@link StoreCache} that maintains stores for all active {@link
     *     HederaState}s
     * @throws NullPointerException if one of the parameters is {@code null}
     */
    public TransactionDispatcher(
            @NonNull final TransactionHandlers handlers,
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

        return switch (transactionBody.data().kind()) {
            case CONSENSUS_CREATE_TOPIC -> handlers.consensusCreateTopicHandler()
                    .preHandle(transactionBody, payer, storeCache.getAccountStore(state));
            case CONSENSUS_UPDATE_TOPIC -> handlers.consensusUpdateTopicHandler()
                    .preHandle(transactionBody, payer);
            case CONSENSUS_DELETE_TOPIC -> handlers.consensusDeleteTopicHandler()
                    .preHandle(transactionBody, payer);
            case CONSENSUS_SUBMIT_MESSAGE -> handlers.consensusSubmitMessageHandler()
                    .preHandle(transactionBody, payer);

            case CONTRACT_CREATE_INSTANCE -> handlers.contractCreateHandler()
                    .preHandle(transactionBody, payer, storeCache.getAccountStore(state));
            case CONTRACT_UPDATE_INSTANCE -> handlers.contractUpdateHandler()
                    .preHandle(transactionBody, payer, storeCache.getAccountStore(state));
            case CONTRACT_CALL -> handlers.contractCallHandler()
                    .preHandle(transactionBody, payer, storeCache.getAccountStore(state));
            case CONTRACT_DELETE_INSTANCE -> handlers.contractDeleteHandler()
                    .preHandle(transactionBody, payer, storeCache.getAccountStore(state));
            case ETHEREUM_TRANSACTION -> handlers.etherumTransactionHandler()
                    .preHandle(transactionBody, payer);

            case CRYPTO_CREATE_ACCOUNT -> handlers.cryptoCreateHandler()
                    .preHandle(transactionBody, payer, storeCache.getAccountStore(state));
            case CRYPTO_UPDATE_ACCOUNT -> handlers.cryptoUpdateHandler()
                    .preHandle(
                            transactionBody,
                            payer,
                            storeCache.getAccountStore(state),
                            cryptoSignatureWaivers);
            case CRYPTO_TRANSFER -> handlers.cryptoTransferHandler()
                    .preHandle(
                            transactionBody,
                            payer,
                            storeCache.getAccountStore(state),
                            storeCache.getTokenStore(state));
            case CRYPTO_DELETE -> handlers.cryptoDeleteHandler()
                    .preHandle(transactionBody, payer, storeCache.getAccountStore(state));
            case CRYPTO_APPROVE_ALLOWANCE -> handlers.cryptoApproveAllowanceHandler()
                    .preHandle(transactionBody, payer, storeCache.getAccountStore(state));
            case CRYPTO_DELETE_ALLOWANCE -> handlers.cryptoDeleteAllowanceHandler()
                    .preHandle(transactionBody, payer, storeCache.getAccountStore(state));
            case CRYPTO_ADD_LIVE_HASH -> handlers.cryptoAddLiveHashHandler()
                    .preHandle(transactionBody, payer);
            case CRYPTO_DELETE_LIVE_HASH -> handlers.cryptoDeleteLiveHashHandler()
                    .preHandle(transactionBody, payer);

            case FILE_CREATE -> handlers.fileCreateHandler().preHandle(transactionBody, payer);
            case FILE_UPDATE -> handlers.fileUpdateHandler().preHandle(transactionBody, payer);
            case FILE_DELETE -> handlers.fileDeleteHandler().preHandle(transactionBody, payer);
            case FILE_APPEND -> handlers.fileAppendHandler().preHandle(transactionBody, payer);

            case FREEZE -> handlers.freezeHandler().preHandle(transactionBody, payer);

            case UNCHECKED_SUBMIT -> handlers.networkUncheckedSubmitHandler()
                    .preHandle(transactionBody, payer);

            case SCHEDULE_CREATE -> handlers.scheduleCreateHandler()
                    .preHandle(
                            transactionBody,
                            payer,
                            storeCache.getAccountStore(state),
                            (innerTxn, innerPayer) ->
                                    dispatchPreHandle(state, innerTxn, innerPayer));
            case SCHEDULE_SIGN -> handlers.scheduleSignHandler()
                    .preHandle(
                            transactionBody,
                            payer,
                            storeCache.getAccountStore(state),
                            storeCache.getScheduleStore(state),
                            (innerTxn, innerPayer) ->
                                    dispatchPreHandle(state, innerTxn, innerPayer));
            case SCHEDULE_DELETE -> handlers.scheduleDeleteHandler()
                    .preHandle(
                            transactionBody,
                            payer,
                            storeCache.getAccountStore(state),
                            storeCache.getScheduleStore(state));
            case TOKEN_CREATION -> handlers.tokenCreateHandler()
                    .preHandle(transactionBody, payer, storeCache.getAccountStore(state));
            case TOKEN_UPDATE -> handlers.tokenUpdateHandler().preHandle(transactionBody, payer);
            case TOKEN_MINT -> handlers.tokenMintHandler()
                    .preHandle(
                            transactionBody,
                            payer,
                            storeCache.getAccountStore(state),
                            storeCache.getTokenStore(state));
            case TOKEN_BURN -> handlers.tokenBurnHandler().preHandle(transactionBody, payer);
            case TOKEN_DELETION -> handlers.tokenDeleteHandler().preHandle(transactionBody, payer);
            case TOKEN_WIPE -> handlers.tokenAccountWipeHandler().preHandle(transactionBody, payer);
            case TOKEN_FREEZE -> handlers.tokenFreezeAccountHandler()
                    .preHandle(transactionBody, payer);
            case TOKEN_UNFREEZE -> handlers.tokenUnfreezeAccountHandler()
                    .preHandle(
                            transactionBody,
                            payer,
                            storeCache.getTokenStore(state),
                            storeCache.getAccountStore(state));
            case TOKEN_GRANT_KYC -> handlers.tokenGrantKycToAccountHandler()
                    .preHandle(transactionBody, payer);
            case TOKEN_REVOKE_KYC -> handlers.tokenRevokeKycFromAccountHandler()
                    .preHandle(
                            transactionBody,
                            payer,
                            storeCache.getTokenStore(state),
                            storeCache.getAccountStore(state));
            case TOKEN_ASSOCIATE -> handlers.tokenAssociateToAccountHandler()
                    .preHandle(transactionBody, payer);
            case TOKEN_DISSOCIATE -> handlers.tokenDissociateFromAccountHandler()
                    .preHandle(transactionBody, payer);
            case TOKEN_FEE_SCHEDULE_UPDATE -> handlers.tokenFeeScheduleUpdateHandler()
                    .preHandle(
                            transactionBody,
                            payer,
                            storeCache.getAccountStore(state),
                            storeCache.getTokenStore(state));
            case TOKEN_PAUSE -> handlers.tokenPauseHandler().preHandle(transactionBody, payer);
            case TOKEN_UNPAUSE -> handlers.tokenUnpauseHandler().preHandle(transactionBody, payer);

            case UTIL_PRNG -> handlers.utilPrngHandler().preHandle(transactionBody, payer);

            case SYSTEM_DELETE -> switch (transactionBody.systemDelete().get().id().kind()) {
                case CONTRACT_ID -> handlers.contractSystemDeleteHandler()
                        .preHandle(transactionBody, payer);
                case FILE_ID -> handlers.fileSystemDeleteHandler().preHandle(transactionBody, payer);
                case UNSET -> throw new IllegalArgumentException(
                        "SystemDelete without IdCase");
            };
            case SYSTEM_UNDELETE -> switch (transactionBody.systemUndelete().get().id().kind()) {
                case CONTRACT_ID -> handlers.contractSystemUndeleteHandler()
                        .preHandle(transactionBody, payer);
                case FILE_ID -> handlers.fileSystemUndeleteHandler()
                        .preHandle(transactionBody, payer);
                case UNSET -> throw new IllegalArgumentException(
                        "SystemUndelete without IdCase");
            };

            case NODE_STAKE_UPDATE, UNSET -> throw new UnsupportedOperationException(
                    "Not implemented");
        };
    }
}
