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

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusDeleteTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusUpdateTopicTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.config.ConsensusServiceConfig;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.token.CryptoSignatureWaivers;
import com.hedera.node.app.service.token.impl.CryptoSignatureWaiversImpl;
import com.hedera.node.app.spi.exceptions.HandleStatusException;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.PreHandleDispatcher;
import edu.umd.cs.findbugs.annotations.NonNull;
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
    private final UsageLimits usageLimits;

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
            @NonNull final GlobalDynamicProperties dynamicProperties,
            @NonNull final UsageLimits usageLimits) {
        this.txnCtx = requireNonNull(txnCtx);
        this.handlers = requireNonNull(handlers);
        this.handleContext = requireNonNull(handleContext);
        this.dynamicProperties = requireNonNull(dynamicProperties);
        this.cryptoSignatureWaivers = new CryptoSignatureWaiversImpl(requireNonNull(accountNumbers));
        this.usageLimits = requireNonNull(usageLimits);
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
            @NonNull final WritableStoreFactory writableStoreFactory) {
        final var topicStore = writableStoreFactory.createTopicStore();
        switch (function) {
            case CONSENSUS_CREATE_TOPIC -> dispatchConsensusCreateTopic(
                    txn.consensusCreateTopicOrThrow(), topicStore, usageLimits);
            case CONSENSUS_UPDATE_TOPIC -> dispatchConsensusUpdateTopic(txn.consensusUpdateTopicOrThrow(), topicStore);
            case CONSENSUS_DELETE_TOPIC -> dispatchConsensusDeleteTopic(txn.consensusDeleteTopicOrThrow(), topicStore);
            case CONSENSUS_SUBMIT_MESSAGE -> dispatchConsensusSubmitMessage(txn, topicStore);
            default -> throw new IllegalArgumentException(TYPE_NOT_SUPPORTED);
        }
    }

    /**
     * Dispatch a pre-handle request. It is forwarded to the correct handler, which takes care of
     * the specific functionality
     *
     * @param storeFactory the {@link ReadableStoreFactory} to get required stores
     * @param context the context of the pre-handle workflow
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    //    @SuppressWarnings("java:S1479") // ignore too many branches warning
    public void dispatchPreHandle(
            @NonNull final ReadableStoreFactory storeFactory, @NonNull final PreHandleContext context) {
        requireNonNull(storeFactory);
        requireNonNull(context);

        final var txBody = context.getTxn();
        switch (txBody.data().kind()) {
            case CONSENSUS_CREATE_TOPIC -> handlers.consensusCreateTopicHandler()
                    .preHandle(context);
            case CONSENSUS_UPDATE_TOPIC -> handlers.consensusUpdateTopicHandler()
                    .preHandle(context, storeFactory.createTopicStore());
            case CONSENSUS_DELETE_TOPIC -> handlers.consensusDeleteTopicHandler()
                    .preHandle(context, storeFactory.createTopicStore());
            case CONSENSUS_SUBMIT_MESSAGE -> handlers.consensusSubmitMessageHandler()
                    .preHandle(context, storeFactory.createTopicStore());

            case CONTRACT_CREATE_INSTANCE -> handlers.contractCreateHandler().preHandle(context);
            case CONTRACT_UPDATE_INSTANCE -> handlers.contractUpdateHandler().preHandle(context);
            case CONTRACT_CALL -> handlers.contractCallHandler().preHandle(context);
            case CONTRACT_DELETE_INSTANCE -> handlers.contractDeleteHandler().preHandle(context);
            case ETHEREUM_TRANSACTION -> handlers.etherumTransactionHandler().preHandle(context);

            case CRYPTO_CREATE_ACCOUNT -> handlers.cryptoCreateHandler().preHandle(context);
            case CRYPTO_UPDATE_ACCOUNT -> handlers.cryptoUpdateHandler().preHandle(context, cryptoSignatureWaivers);
            case CRYPTO_TRANSFER -> handlers.cryptoTransferHandler()
                    .preHandle(context, storeFactory.createAccountStore(), storeFactory.createTokenStore());
            case CRYPTO_DELETE -> handlers.cryptoDeleteHandler().preHandle(context);
            case CRYPTO_APPROVE_ALLOWANCE -> handlers.cryptoApproveAllowanceHandler()
                    .preHandle(context);
            case CRYPTO_DELETE_ALLOWANCE -> handlers.cryptoDeleteAllowanceHandler()
                    .preHandle(context);
            case CRYPTO_ADD_LIVE_HASH -> handlers.cryptoAddLiveHashHandler().preHandle(context);
            case CRYPTO_DELETE_LIVE_HASH -> handlers.cryptoDeleteLiveHashHandler()
                    .preHandle(context);

            case FILE_CREATE -> handlers.fileCreateHandler().preHandle(context);
            case FILE_UPDATE -> handlers.fileUpdateHandler().preHandle(context);
            case FILE_DELETE -> handlers.fileDeleteHandler().preHandle(context);
            case FILE_APPEND -> handlers.fileAppendHandler().preHandle(context);

            case FREEZE -> handlers.freezeHandler().preHandle(context);

            case UNCHECKED_SUBMIT -> handlers.networkUncheckedSubmitHandler().preHandle(context);

            case SCHEDULE_CREATE -> handlers.scheduleCreateHandler()
                    .preHandle(context, setupPreHandleDispatcher(storeFactory));
            case SCHEDULE_SIGN -> handlers.scheduleSignHandler()
                    .preHandle(context, storeFactory.createScheduleStore(), setupPreHandleDispatcher(storeFactory));
            case SCHEDULE_DELETE -> handlers.scheduleDeleteHandler()
                    .preHandle(context, storeFactory.createScheduleStore());
            case TOKEN_CREATION -> handlers.tokenCreateHandler().preHandle(context);
            case TOKEN_UPDATE -> handlers.tokenUpdateHandler().preHandle(context, storeFactory.createTokenStore());
            case TOKEN_MINT -> handlers.tokenMintHandler().preHandle(context, storeFactory.createTokenStore());
            case TOKEN_BURN -> handlers.tokenBurnHandler().preHandle(context, storeFactory.createTokenStore());
            case TOKEN_DELETION -> handlers.tokenDeleteHandler().preHandle(context, storeFactory.createTokenStore());
            case TOKEN_WIPE -> handlers.tokenAccountWipeHandler().preHandle(context, storeFactory.createTokenStore());
            case TOKEN_FREEZE -> handlers.tokenFreezeAccountHandler()
                    .preHandle(context, storeFactory.createTokenStore());
            case TOKEN_UNFREEZE -> handlers.tokenUnfreezeAccountHandler()
                    .preHandle(context, storeFactory.createTokenStore());
            case TOKEN_GRANT_KYC -> handlers.tokenGrantKycToAccountHandler()
                    .preHandle(context, storeFactory.createTokenStore());
            case TOKEN_REVOKE_KYC -> handlers.tokenRevokeKycFromAccountHandler()
                    .preHandle(context, storeFactory.createTokenStore());
            case TOKEN_ASSOCIATE -> handlers.tokenAssociateToAccountHandler().preHandle(context);
            case TOKEN_DISSOCIATE -> handlers.tokenDissociateFromAccountHandler()
                    .preHandle(context);
            case TOKEN_FEE_SCHEDULE_UPDATE -> handlers.tokenFeeScheduleUpdateHandler()
                    .preHandle(context, storeFactory.createTokenStore());
            case TOKEN_PAUSE -> handlers.tokenPauseHandler().preHandle(context);
            case TOKEN_UNPAUSE -> handlers.tokenUnpauseHandler().preHandle(context);

            case UTIL_PRNG -> handlers.utilPrngHandler().preHandle(context);

            case SYSTEM_DELETE -> {
                switch (txBody.systemDelete().get().id().kind()) {
                    case CONTRACT_ID -> handlers.contractSystemDeleteHandler().preHandle(context);
                    case FILE_ID -> handlers.fileSystemDeleteHandler().preHandle(context);
                    case UNSET -> throw new IllegalArgumentException("SystemDelete without IdCase");
                }
            }
            case SYSTEM_UNDELETE -> {
                switch (txBody.systemUndelete().get().id().kind()) {
                    case CONTRACT_ID -> handlers.contractSystemUndeleteHandler().preHandle(context);
                    case FILE_ID -> handlers.fileSystemUndeleteHandler().preHandle(context);
                    case UNSET -> throw new IllegalArgumentException("SystemUndelete without IdCase");
                }
            }

            default -> throw new UnsupportedOperationException(TYPE_NOT_SUPPORTED);
        }
    }

    private PreHandleDispatcher setupPreHandleDispatcher(@NonNull final ReadableStoreFactory storeFactory) {
        return context -> dispatchPreHandle(storeFactory, context);
    }

    private void dispatchConsensusDeleteTopic(
            @NonNull final ConsensusDeleteTopicTransactionBody topicDeletion,
            @NonNull final WritableTopicStore topicStore) {
        final var handler = handlers.consensusDeleteTopicHandler();
        handler.handle(topicDeletion, topicStore);
        // TODO: Commit will be called in workflow or some other place when handle workflow is implemented
        // This is temporary solution to make sure that topic is created
        topicStore.commit();
    }

    private void dispatchConsensusUpdateTopic(
            @NonNull final ConsensusUpdateTopicTransactionBody topicUpdate,
            @NonNull final WritableTopicStore topicStore) {
        final var handler = handlers.consensusUpdateTopicHandler();
        handler.handle(handleContext, topicUpdate, topicStore);
        // TODO: Commit will be called in workflow or some other place when handle workflow is implemented
        // This is temporary solution to make sure that topic is created
        topicStore.commit();
    }

    private void dispatchConsensusCreateTopic(
            @NonNull final ConsensusCreateTopicTransactionBody topicCreation,
            @NonNull final WritableTopicStore topicStore,
            @NonNull final UsageLimits usageLimits) {
        final var handler = handlers.consensusCreateTopicHandler();
        final var recordBuilder = handler.newRecordBuilder();
        handler.handle(
                handleContext,
                topicCreation,
                new ConsensusServiceConfig(
                        dynamicProperties.maxNumTopics(), dynamicProperties.messageMaxBytesAllowed()),
                recordBuilder,
                topicStore);
        txnCtx.setCreated(PbjConverter.fromPbj(
                TopicID.newBuilder().topicNum(recordBuilder.getCreatedTopic()).build()));
        usageLimits.refreshTopics();
        // TODO: Commit will be called in workflow or some other place when handle workflow is implemented
        // This is temporary solution to make sure that topic is created
        topicStore.commit();
    }

    private void dispatchConsensusSubmitMessage(
            @NonNull final TransactionBody messageSubmission, @NonNull final WritableTopicStore topicStore) {
        final var handler = handlers.consensusSubmitMessageHandler();
        final var recordBuilder = handler.newRecordBuilder();
        handler.handle(
                handleContext,
                messageSubmission,
                new ConsensusServiceConfig(
                        dynamicProperties.maxNumTopics(), dynamicProperties.messageMaxBytesAllowed()),
                recordBuilder,
                topicStore);
        txnCtx.setTopicRunningHash(recordBuilder.getNewTopicRunningHash(), recordBuilder.getNewTopicSequenceNumber());
        topicStore.commit();
    }
}
