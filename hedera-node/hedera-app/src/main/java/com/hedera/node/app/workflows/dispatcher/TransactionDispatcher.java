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
import com.hedera.node.app.spi.exceptions.HandleStatusException;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.meta.PreHandleContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A {@code TransactionDispatcher} provides functionality to forward pre-check, pre-handle, and
 * handle-transaction requests to the appropriate handler
 *
 * <p>For handle, mostly just supports the limited form of the Consensus Service handlers
 * described in https://github.com/hashgraph/hedera-services/issues/4945, while still trying to
 * make a bit of progress toward the general implementation.
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
     * Creates a {@code TransactionDispatcher}.
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
     *
     * <p>This will not be final signature of the dispatch method, since as per
     * https://github.com/hashgraph/hedera-services/issues/4945, we are currently
     * just adapting the last step of mono-service "workflow"; and only for
     * Consensus Service transactions.
     *
     * @param function the type of the consensus service transaction
     * @param txn the consensus transaction to be handled
     * @throws HandleStatusException if the handler fails
     * @throws IllegalArgumentException if there is no handler for the given function type
     */
    public void dispatchHandle(
            @NonNull final HederaFunctionality function,
            @NonNull final TransactionBody txn,
            @NonNull final WritableStoreFactory storeFactory) {
        switch (function) {
            case ConsensusCreateTopic -> dispatchConsensusCreateTopic(txn.getConsensusCreateTopic(), storeFactory);
            case ConsensusUpdateTopic -> dispatchConsensusUpdateTopic(txn.getConsensusUpdateTopic());
            case ConsensusDeleteTopic -> dispatchConsensusDeleteTopic(txn.getConsensusDeleteTopic());
            case ConsensusSubmitMessage -> dispatchConsensusSubmitMessage(txn.getConsensusSubmitMessage());
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
            @NonNull final ReadableStoreFactory storeFactory, @NonNull final PreHandleContext handlerContext) {
        requireNonNull(storeFactory);
        requireNonNull(handlerContext);

        final var txBody = handlerContext.getTxn();
        switch (txBody.getDataCase()) {
            case CONSENSUSCREATETOPIC -> handlers.consensusCreateTopicHandler().preHandle(handlerContext);
            case CONSENSUSUPDATETOPIC -> handlers.consensusUpdateTopicHandler().preHandle(handlerContext);
            case CONSENSUSDELETETOPIC -> handlers.consensusDeleteTopicHandler()
                    .preHandle(handlerContext, storeFactory.getTopicStore());
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
                    .preHandle(handlerContext, storeFactory.getAccountStore(), storeFactory.getTokenStore());
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
                    .preHandle(handlerContext, storeFactory.getScheduleStore(), setupPreHandleDispatcher(storeFactory));
            case SCHEDULEDELETE -> handlers.scheduleDeleteHandler()
                    .preHandle(handlerContext, storeFactory.getScheduleStore());

            case TOKENCREATION -> handlers.tokenCreateHandler().preHandle(handlerContext);
            case TOKENUPDATE -> handlers.tokenUpdateHandler().preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKENMINT -> handlers.tokenMintHandler().preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKENBURN -> handlers.tokenBurnHandler().preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKENDELETION -> handlers.tokenDeleteHandler().preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKENWIPE -> handlers.tokenAccountWipeHandler()
                    .preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKENFREEZE -> handlers.tokenFreezeAccountHandler()
                    .preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKENUNFREEZE -> handlers.tokenUnfreezeAccountHandler()
                    .preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKENGRANTKYC -> handlers.tokenGrantKycToAccountHandler()
                    .preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKENREVOKEKYC -> handlers.tokenRevokeKycFromAccountHandler()
                    .preHandle(handlerContext, storeFactory.getTokenStore());
            case TOKENASSOCIATE -> handlers.tokenAssociateToAccountHandler().preHandle(handlerContext);
            case TOKENDISSOCIATE -> handlers.tokenDissociateFromAccountHandler().preHandle(handlerContext);
            case TOKEN_FEE_SCHEDULE_UPDATE -> handlers.tokenFeeScheduleUpdateHandler()
                    .preHandle(handlerContext, storeFactory.getTokenStore());
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

    private PreHandleDispatcher setupPreHandleDispatcher(@NonNull final ReadableStoreFactory storeFactory) {
        return (TransactionBody innerTxn, AccountID innerPayer) -> {
            final var accountStore = storeFactory.getAccountStore();
            final var handlerContext = new PreHandleContext(accountStore, innerTxn, innerPayer);
            dispatchPreHandle(storeFactory, handlerContext);
            return new TransactionMetadata(handlerContext, List.of());
        };
    }

    private void dispatchConsensusDeleteTopic(final ConsensusDeleteTopicTransactionBody topicDeletion) {
        final var handler = handlers.consensusDeleteTopicHandler();
        final var recordBuilder = handler.newRecordBuilder();
        handler.handle(
                handleContext,
                topicDeletion,
                new ConsensusServiceConfig(
                        dynamicProperties.maxNumTopics(), dynamicProperties.messageMaxBytesAllowed()),
                recordBuilder);
    }

    private void dispatchConsensusUpdateTopic(final ConsensusUpdateTopicTransactionBody topicUpdate) {
        final var handler = handlers.consensusUpdateTopicHandler();
        final var recordBuilder = handler.newRecordBuilder();
        handler.handle(
                handleContext,
                topicUpdate,
                new ConsensusServiceConfig(
                        dynamicProperties.maxNumTopics(), dynamicProperties.messageMaxBytesAllowed()),
                recordBuilder);
    }

    private void dispatchConsensusCreateTopic(
            @NonNull final ConsensusCreateTopicTransactionBody topicCreation,
            @NonNull final WritableStoreFactory storeFactory) {
        final var handler = handlers.consensusCreateTopicHandler();
        final var recordBuilder = handler.newRecordBuilder();
        handler.handle(
                handleContext,
                topicCreation,
                new ConsensusServiceConfig(
                        dynamicProperties.maxNumTopics(), dynamicProperties.messageMaxBytesAllowed()),
                recordBuilder,
                storeFactory.getTopicStore());
        txnCtx.setCreated(TopicID.newBuilder()
                .setTopicNum(recordBuilder.getCreatedTopic())
                .build());
    }

    private void dispatchConsensusSubmitMessage(final ConsensusSubmitMessageTransactionBody messageSubmission) {
        final var handler = handlers.consensusSubmitMessageHandler();
        final var recordBuilder = handler.newRecordBuilder();
        handler.handle(
                handleContext,
                messageSubmission,
                new ConsensusServiceConfig(
                        dynamicProperties.maxNumTopics(), dynamicProperties.messageMaxBytesAllowed()),
                recordBuilder);
        txnCtx.setTopicRunningHash(recordBuilder.getNewTopicRunningHash(), recordBuilder.getNewTopicSequenceNumber());
    }
}
