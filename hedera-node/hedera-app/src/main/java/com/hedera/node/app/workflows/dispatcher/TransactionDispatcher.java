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

import com.hedera.node.app.service.consensus.impl.config.ConsensusServiceConfig;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.token.CryptoSignatureWaivers;
import com.hedera.node.app.service.token.impl.CryptoSignatureWaiversImpl;
import com.hedera.node.app.spi.PreHandleDispatcher;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.meta.PreHandleContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.workflows.handle.records.CreateTopicRecordBuilder;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A {@code TransactionDispatcher} provides functionality to forward pre-check, pre-handle, and
 * handle-transaction requests to the appropriate handler
 */
@Singleton
public class TransactionDispatcher {

    public static final String TYPE_NOT_SUPPORTED = "This transaction type is not supported";

    private final HandleContext handleContext;
    private final TransactionContext txnCtx;
    private final TransactionHandlers handlers;
    private final CryptoSignatureWaivers cryptoSignatureWaivers;
    private final GlobalDynamicProperties dynamicProperties;

    /**
     * Creates a {@code TransactionDispatcher} able to support the limited form of the
     * Consensus Service handlers described in
     * https://github.com/hashgraph/hedera-services/issues/4945, while still trying
     * to make a bit of progress toward a more general solution.
     *
     * @param handleContext     the context of the handle workflow
     * @param txnCtx            the mono context of the transaction
     * @param handlers          the handlers for all transaction types
     * @param accountNumbers    the account numbers of the system
     * @param dynamicProperties the dynamic properties of the system
     */
    @Inject
    public TransactionDispatcher(
            @NonNull final HandleContext handleContext,
            @NonNull final TransactionContext txnCtx,
            @NonNull final TransactionHandlers handlers,
            @NonNull final HederaAccountNumbers accountNumbers,
            @NonNull final GlobalDynamicProperties dynamicProperties) {
        this.txnCtx = txnCtx;
        this.handlers = requireNonNull(handlers);
        this.handleContext = handleContext;
        this.dynamicProperties = dynamicProperties;
        this.cryptoSignatureWaivers = new CryptoSignatureWaiversImpl(requireNonNull(accountNumbers));
    }

    /**
     * Dispatches a transaction of the given type to the appropriate handler.
     * Only Consensus Service transactions are supported.
     *
     * <p>This will not be final signature of the dispatch method, since as per
     * https://github.com/hashgraph/hedera-services/issues/4945, at this point we
     * are really dispatching within the mono-service "workflow".
     *
     * @param function the type of the consensus service transaction
     * @param txn the consensus transaction to be handled
     */
    public void dispatchHandle(
            @NonNull final HederaFunctionality function,
            @NonNull final TransactionBody txn,
            @NonNull final StoreFactory storeFactory) {
        switch (function) {
            case ConsensusCreateTopic -> {
                final var recordBuilder = new CreateTopicRecordBuilder();
                handlers.consensusCreateTopicHandler()
                        .handle(
                                handleContext,
                                txn,
                                new ConsensusServiceConfig(
                                        dynamicProperties.maxNumTopics(), dynamicProperties.messageMaxBytesAllowed()),
                                recordBuilder,
                                storeFactory.getWritableTopicStore());
                recordBuilder.exposeSideEffectsToMono(txnCtx);
            }
            default -> throw new IllegalArgumentException(TYPE_NOT_SUPPORTED);
        }
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
        switch (txBody.getDataCase()) {
            case CONSENSUSCREATETOPIC -> handlers.consensusCreateTopicHandler().preHandle(handlerContext);
            case CONSENSUSUPDATETOPIC -> handlers.consensusUpdateTopicHandler().preHandle(handlerContext);
            case CONSENSUSDELETETOPIC -> handlers.consensusDeleteTopicHandler()
                    .preHandle(handlerContext, storeFactory.getReadableTopicStore());
            case CONSENSUSSUBMITMESSAGE -> handlers.consensusSubmitMessageHandler()
                    .preHandle(handlerContext);

            case CONTRACTCREATEINSTANCE -> handlers.contractCreateHandler().preHandle(handlerContext);
            case CONTRACTUPDATEINSTANCE -> handlers.contractUpdateHandler().preHandle(handlerContext);
            case CONTRACTCALL -> handlers.contractCallHandler().preHandle(handlerContext);
            case CONTRACTDELETEINSTANCE -> handlers.contractDeleteHandler().preHandle(handlerContext);
            case ETHEREUMTRANSACTION -> handlers.etherumTransactionHandler().preHandle(handlerContext);

            case CRYPTOCREATEACCOUNT -> handlers.cryptoCreateHandler().preHandle(handlerContext);
            case CRYPTOUPDATEACCOUNT -> handlers.cryptoUpdateHandler()
                    .preHandle(handlerContext, cryptoSignatureWaivers);
            case CRYPTOTRANSFER -> handlers.cryptoTransferHandler()
                    .preHandle(
                            handlerContext,
                            storeFactory.getReadableAccountStore(),
                            storeFactory.getReadableTokenStore());
            case CRYPTODELETE -> handlers.cryptoDeleteHandler().preHandle(handlerContext);
            case CRYPTOAPPROVEALLOWANCE -> handlers.cryptoApproveAllowanceHandler()
                    .preHandle(handlerContext);
            case CRYPTODELETEALLOWANCE -> handlers.cryptoDeleteAllowanceHandler()
                    .preHandle(handlerContext);
            case CRYPTOADDLIVEHASH -> handlers.cryptoAddLiveHashHandler().preHandle(handlerContext);
            case CRYPTODELETELIVEHASH -> handlers.cryptoDeleteLiveHashHandler().preHandle(handlerContext);

            case FILECREATE -> handlers.fileCreateHandler().preHandle(handlerContext);
            case FILEUPDATE -> handlers.fileUpdateHandler().preHandle(handlerContext);
            case FILEDELETE -> handlers.fileDeleteHandler().preHandle(handlerContext);
            case FILEAPPEND -> handlers.fileAppendHandler().preHandle(handlerContext);

            case FREEZE -> handlers.freezeHandler().preHandle(handlerContext);

            case UNCHECKEDSUBMIT -> handlers.networkUncheckedSubmitHandler().preHandle(handlerContext);

            case SCHEDULECREATE -> handlers.scheduleCreateHandler()
                    .preHandle(handlerContext, setupPreHandleDispatcher(storeFactory));
            case SCHEDULESIGN -> handlers.scheduleSignHandler()
                    .preHandle(
                            handlerContext,
                            storeFactory.getReadableScheduleStore(),
                            setupPreHandleDispatcher(storeFactory));
            case SCHEDULEDELETE -> handlers.scheduleDeleteHandler()
                    .preHandle(handlerContext, storeFactory.getReadableScheduleStore());

            case TOKENCREATION -> handlers.tokenCreateHandler().preHandle(handlerContext);
            case TOKENUPDATE -> handlers.tokenUpdateHandler()
                    .preHandle(handlerContext, storeFactory.getReadableTokenStore());
            case TOKENMINT -> handlers.tokenMintHandler()
                    .preHandle(handlerContext, storeFactory.getReadableTokenStore());
            case TOKENBURN -> handlers.tokenBurnHandler()
                    .preHandle(handlerContext, storeFactory.getReadableTokenStore());
            case TOKENDELETION -> handlers.tokenDeleteHandler()
                    .preHandle(handlerContext, storeFactory.getReadableTokenStore());
            case TOKENWIPE -> handlers.tokenAccountWipeHandler()
                    .preHandle(handlerContext, storeFactory.getReadableTokenStore());
            case TOKENFREEZE -> handlers.tokenFreezeAccountHandler()
                    .preHandle(handlerContext, storeFactory.getReadableTokenStore());
            case TOKENUNFREEZE -> handlers.tokenUnfreezeAccountHandler()
                    .preHandle(handlerContext, storeFactory.getReadableTokenStore());
            case TOKENGRANTKYC -> handlers.tokenGrantKycToAccountHandler()
                    .preHandle(handlerContext, storeFactory.getReadableTokenStore());
            case TOKENREVOKEKYC -> handlers.tokenRevokeKycFromAccountHandler()
                    .preHandle(handlerContext, storeFactory.getReadableTokenStore());
            case TOKENASSOCIATE -> handlers.tokenAssociateToAccountHandler().preHandle(handlerContext);
            case TOKENDISSOCIATE -> handlers.tokenDissociateFromAccountHandler().preHandle(handlerContext);
            case TOKEN_FEE_SCHEDULE_UPDATE -> handlers.tokenFeeScheduleUpdateHandler()
                    .preHandle(handlerContext, storeFactory.getReadableTokenStore());
            case TOKEN_PAUSE -> handlers.tokenPauseHandler().preHandle(handlerContext);
            case TOKEN_UNPAUSE -> handlers.tokenUnpauseHandler().preHandle(handlerContext);

            case UTIL_PRNG -> handlers.utilPrngHandler().preHandle(handlerContext);

            case SYSTEMDELETE -> {
                switch (txBody.getSystemDelete().getIdCase()) {
                    case CONTRACTID -> handlers.contractSystemDeleteHandler().preHandle(handlerContext);
                    case FILEID -> handlers.fileSystemDeleteHandler().preHandle(handlerContext);
                    case ID_NOT_SET -> throw new IllegalArgumentException("SystemDelete without IdCase");
                }
            }
            case SYSTEMUNDELETE -> {
                switch (txBody.getSystemUndelete().getIdCase()) {
                    case CONTRACTID -> handlers.contractSystemUndeleteHandler().preHandle(handlerContext);
                    case FILEID -> handlers.fileSystemUndeleteHandler().preHandle(handlerContext);
                    case ID_NOT_SET -> throw new IllegalArgumentException("SystemUndelete without IdCase");
                }
            }

            default -> throw new UnsupportedOperationException(TYPE_NOT_SUPPORTED);
        }
    }

    private PreHandleDispatcher setupPreHandleDispatcher(@NonNull final StoreFactory storeFactory) {
        return (TransactionBody innerTxn, AccountID innerPayer) -> {
            final var accountStore = storeFactory.getReadableAccountStore();
            final var handlerContext = new PreHandleContext(accountStore, innerTxn, innerPayer);
            dispatchPreHandle(storeFactory, handlerContext);
            return new TransactionMetadata(handlerContext, List.of());
        };
    }
}
