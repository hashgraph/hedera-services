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
import com.hedera.node.app.spi.PreHandleDispatcher;
import com.hedera.node.app.spi.meta.PreHandleContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * A {@code TransactionDispatcher} provides functionality to forward pre-check, pre-handle, and
 * handle-transaction requests to the appropriate handler
 */
public class TransactionDispatcher {

    public static final String TYPE_NOT_SUPPORTED = "This transaction type is not supported";

    private final TransactionHandlers handlers;

    private final CryptoSignatureWaivers cryptoSignatureWaivers;

    /**
     * Constructor of {@code TransactionDispatcher}
     *
     * @param handlers a {@link TransactionHandlers} record with all available handlers
     * @throws NullPointerException if one of the parameters is {@code null}
     */
    public TransactionDispatcher(
            @NonNull final TransactionHandlers handlers, @NonNull final HederaAccountNumbers accountNumbers) {
        this.handlers = requireNonNull(handlers);
        this.cryptoSignatureWaivers = new CryptoSignatureWaiversImpl(requireNonNull(accountNumbers));
    }

    /**
     * Dispatch a pre-handle request. It is forwarded to the correct handler, which takes care of
     * the specific functionality
     *
     * @param handlerContext the context of the pre-handle workflow
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void dispatchPreHandle(
            @NonNull final StoreFactory storeFactory, @NonNull final PreHandleContext handlerContext) {
        requireNonNull(storeFactory);
        requireNonNull(handlerContext);

        final var txBody = handlerContext.getTxn();
        switch (txBody.data().kind()) {
            case CONSENSUS_CREATE_TOPIC -> handlers.consensusCreateTopicHandler()
                    .preHandle(handlerContext);
            case CONSENSUS_UPDATE_TOPIC -> handlers.consensusUpdateTopicHandler()
                    .preHandle(handlerContext);
            case CONSENSUS_DELETE_TOPIC -> handlers.consensusDeleteTopicHandler()
                    .preHandle(handlerContext);
            case CONSENSUS_SUBMIT_MESSAGE -> handlers.consensusSubmitMessageHandler()
                    .preHandle(handlerContext);

            case CONTRACT_CREATE_INSTANCE -> handlers.contractCreateHandler().preHandle(handlerContext);
            case CONTRACT_UPDATE_INSTANCE -> handlers.contractUpdateHandler().preHandle(handlerContext);
            case CONTRACT_CALL -> handlers.contractCallHandler().preHandle(handlerContext);
            case CONTRACT_DELETE_INSTANCE -> handlers.contractDeleteHandler().preHandle(handlerContext);
            case ETHEREUM_TRANSACTION -> handlers.etherumTransactionHandler().preHandle(handlerContext);

            case CRYPTO_CREATE_ACCOUNT -> handlers.cryptoCreateHandler().preHandle(handlerContext);
            case CRYPTO_UPDATE_ACCOUNT -> handlers.cryptoUpdateHandler()
                    .preHandle(handlerContext, cryptoSignatureWaivers);
            case CRYPTO_TRANSFER -> handlers.cryptoTransferHandler()
                    .preHandle(handlerContext, storeFactory.getAccountStore(), storeFactory.getTokenStore());
            case CRYPTO_DELETE -> handlers.cryptoDeleteHandler().preHandle(handlerContext);
            case CRYPTO_APPROVE_ALLOWANCE -> handlers.cryptoApproveAllowanceHandler()
                    .preHandle(handlerContext);
            case CRYPTO_DELETE_ALLOWANCE -> handlers.cryptoDeleteAllowanceHandler()
                    .preHandle(handlerContext);
            case CRYPTO_ADD_LIVE_HASH -> handlers.cryptoAddLiveHashHandler().preHandle(handlerContext);
            case CRYPTO_DELETE_LIVE_HASH -> handlers.cryptoDeleteLiveHashHandler()
                    .preHandle(handlerContext);

            case FILE_CREATE -> handlers.fileCreateHandler().preHandle(handlerContext);
            case FILE_UPDATE -> handlers.fileUpdateHandler().preHandle(handlerContext);
            case FILE_DELETE -> handlers.fileDeleteHandler().preHandle(handlerContext);
            case FILE_APPEND -> handlers.fileAppendHandler().preHandle(handlerContext);

            case FREEZE -> handlers.freezeHandler().preHandle(handlerContext);

            case UNCHECKED_SUBMIT -> handlers.networkUncheckedSubmitHandler().preHandle(handlerContext);

            case SCHEDULE_CREATE -> handlers.scheduleCreateHandler()
                    .preHandle(handlerContext, setupPreHandleDispatcher(storeFactory));
            case SCHEDULE_SIGN -> handlers.scheduleSignHandler()
                    .preHandle(handlerContext, storeFactory.getScheduleStore(), setupPreHandleDispatcher(storeFactory));
            case SCHEDULE_DELETE -> handlers.scheduleDeleteHandler()
                    .preHandle(handlerContext, storeFactory.getScheduleStore());
            case TOKEN_CREATION -> handlers.tokenCreateHandler().preHandle(handlerContext);
            case TOKEN_UPDATE -> handlers.tokenUpdateHandler().preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKEN_MINT -> handlers.tokenMintHandler().preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKEN_BURN -> handlers.tokenBurnHandler().preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKEN_DELETION -> handlers.tokenDeleteHandler()
                    .preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKEN_WIPE -> handlers.tokenAccountWipeHandler()
                    .preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKEN_FREEZE -> handlers.tokenFreezeAccountHandler()
                    .preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKEN_UNFREEZE -> handlers.tokenUnfreezeAccountHandler()
                    .preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKEN_GRANT_KYC -> handlers.tokenGrantKycToAccountHandler()
                    .preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKEN_REVOKE_KYC -> handlers.tokenRevokeKycFromAccountHandler()
                    .preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKEN_ASSOCIATE -> handlers.tokenAssociateToAccountHandler().preHandle(handlerContext);
            case TOKEN_DISSOCIATE -> handlers.tokenDissociateFromAccountHandler()
                    .preHandle(handlerContext);
            case TOKEN_FEE_SCHEDULE_UPDATE -> handlers.tokenFeeScheduleUpdateHandler()
                    .preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKEN_PAUSE -> handlers.tokenPauseHandler().preHandle(handlerContext);
            case TOKEN_UNPAUSE -> handlers.tokenUnpauseHandler().preHandle(handlerContext);

            case UTIL_PRNG -> handlers.utilPrngHandler().preHandle(handlerContext);

            case SYSTEM_DELETE -> {
                switch (txBody.systemDelete().get().id().kind()) {
                    case CONTRACT_ID -> handlers.contractSystemDeleteHandler().preHandle(handlerContext);
                    case FILE_ID -> handlers.fileSystemDeleteHandler().preHandle(handlerContext);
                    case UNSET -> throw new IllegalArgumentException("SystemDelete without IdCase");
                }
            }
            case SYSTEM_UNDELETE -> {
                switch (txBody.systemUndelete().get().id().kind()) {
                    case CONTRACT_ID -> handlers.contractSystemUndeleteHandler().preHandle(handlerContext);
                    case FILE_ID -> handlers.fileSystemUndeleteHandler().preHandle(handlerContext);
                    case UNSET -> throw new IllegalArgumentException("SystemUndelete without IdCase");
                }
            }

            default -> throw new UnsupportedOperationException(TYPE_NOT_SUPPORTED);
        }
    }

    private PreHandleDispatcher setupPreHandleDispatcher(@NonNull final StoreFactory storeFactory) {
        return (TransactionBody innerTxn, AccountID innerPayer) -> {
            final var accountStore = storeFactory.getAccountStore();
            final var handlerContext = new PreHandleContext(accountStore, innerTxn, innerPayer);
            dispatchPreHandle(storeFactory, handlerContext);
            return new TransactionMetadata(handlerContext, List.of());
        };
    }
}
