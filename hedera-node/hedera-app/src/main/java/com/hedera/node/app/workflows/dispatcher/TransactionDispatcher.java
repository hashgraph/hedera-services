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
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusDeleteTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusUpdateTopicTransactionBody;
import com.hedera.hapi.node.freeze.FreezeTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.admin.impl.ReadableSpecialFileStore;
import com.hedera.node.app.service.admin.impl.WritableSpecialFileStore;
import com.hedera.node.app.service.admin.impl.config.AdminServiceConfig;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.config.ConsensusServiceConfig;
import com.hedera.node.app.service.consensus.impl.records.ConsensusCreateTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.ConsensusSubmitMessageRecordBuilder;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.service.token.CryptoSignatureWaivers;
import com.hedera.node.app.service.token.impl.CryptoSignatureWaiversImpl;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.swirlds.common.system.SwirldDualState;
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
    private final TransactionHandlers handlers;
    private final CryptoSignatureWaivers cryptoSignatureWaivers;
    private final GlobalDynamicProperties dynamicProperties;
    private final SwirldDualState dualState;

    /**
     * Creates a {@code TransactionDispatcher}.
     *
     * @param handleContext     the context of the handle workflow
     * @param handlers          the handlers for all transaction types
     * @param accountNumbers    the account numbers of the system
     * @param dynamicProperties the dynamic properties of the system
     */
    @Inject
    public TransactionDispatcher(
            @NonNull final HandleContext handleContext,
            @NonNull final TransactionHandlers handlers,
            @NonNull final HederaAccountNumbers accountNumbers,
            @NonNull final GlobalDynamicProperties dynamicProperties) {
        this.handleContext = requireNonNull(handleContext);
        this.handlers = requireNonNull(handlers);
        this.cryptoSignatureWaivers = new CryptoSignatureWaiversImpl(requireNonNull(accountNumbers));
        this.dynamicProperties = requireNonNull(dynamicProperties);
        this.dualState = null; // TODO: need to initialize this somehow, probably through Dagger injection
    }

    /**
     * Dispatches a transaction of the given type to the appropriate handler.
     *
     * <p>This will not be final signature of the dispatch method, since as per
     * <a href="https://github.com/hashgraph/hedera-services/issues/4945">issue #4945</a>, we are currently
     * just adapting the last step of mono-service "workflow"; and only for
     * Consensus Service transactions.
     *
     * @param function the type of the consensus service transaction
     * @param txn the consensus transaction to be handled
     * @throws HandleException if the handler fails
     * @throws IllegalArgumentException if there is no handler for the given function type
     */
    public void dispatchHandle(
            @NonNull final HederaFunctionality function,
            @NonNull final TransactionBody txn,
            @NonNull final WritableStoreFactory writableStoreFactory) {
        switch (function) {
            case CONSENSUS_CREATE_TOPIC -> dispatchConsensusCreateTopic(
                    txn.consensusCreateTopicOrThrow(), writableStoreFactory.createTopicStore());
            case CONSENSUS_UPDATE_TOPIC -> dispatchConsensusUpdateTopic(
                    txn.consensusUpdateTopicOrThrow(), writableStoreFactory.createTopicStore());
            case CONSENSUS_DELETE_TOPIC -> dispatchConsensusDeleteTopic(
                    txn.consensusDeleteTopicOrThrow(), writableStoreFactory.createTopicStore());
            case CONSENSUS_SUBMIT_MESSAGE -> dispatchConsensusSubmitMessage(
                    txn, writableStoreFactory.createTopicStore());
            case TOKEN_GRANT_KYC_TO_ACCOUNT -> dispatchTokenGrantKycToAccount(
                    txn, writableStoreFactory.createTokenRelStore());
            case TOKEN_PAUSE -> dispatchTokenPause(txn, writableStoreFactory.createTokenStore());
            case TOKEN_UNPAUSE -> dispatchTokenUnpause(txn, writableStoreFactory.createTokenStore());
            case FREEZE -> dispatchFreeze(
                    txn.freezeOrThrow(), writableStoreFactory.createSpecialFileStore(), dualState);
            default -> throw new IllegalArgumentException(TYPE_NOT_SUPPORTED);
        }
    }

    /**
     * Dispatch a pre-handle request. It is forwarded to the correct handler, which takes care of
     * the specific functionality
     *
     * @param context the context of the pre-handle workflow
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    //    @SuppressWarnings("java:S1479") // ignore too many branches warning
    public void dispatchPreHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context, "The supplied argument 'context' cannot be null!");

        final var txBody = context.body();
        switch (txBody.data().kind()) {
            case CONSENSUS_CREATE_TOPIC -> handlers.consensusCreateTopicHandler()
                    .preHandle(context);
            case CONSENSUS_UPDATE_TOPIC -> handlers.consensusUpdateTopicHandler()
                    .preHandle(context, context.createStore(ReadableTopicStore.class));
            case CONSENSUS_DELETE_TOPIC -> handlers.consensusDeleteTopicHandler()
                    .preHandle(context, context.createStore(ReadableTopicStore.class));
            case CONSENSUS_SUBMIT_MESSAGE -> handlers.consensusSubmitMessageHandler()
                    .preHandle(context, context.createStore(ReadableTopicStore.class));

            case CONTRACT_CREATE_INSTANCE -> handlers.contractCreateHandler().preHandle(context);
            case CONTRACT_UPDATE_INSTANCE -> handlers.contractUpdateHandler().preHandle(context);
            case CONTRACT_CALL -> handlers.contractCallHandler().preHandle(context);
            case CONTRACT_DELETE_INSTANCE -> handlers.contractDeleteHandler().preHandle(context);
            case ETHEREUM_TRANSACTION -> handlers.etherumTransactionHandler().preHandle(context);

            case CRYPTO_CREATE_ACCOUNT -> handlers.cryptoCreateHandler().preHandle(context);
            case CRYPTO_UPDATE_ACCOUNT -> handlers.cryptoUpdateHandler().preHandle(context, cryptoSignatureWaivers);
            case CRYPTO_TRANSFER -> handlers.cryptoTransferHandler()
                    .preHandle(
                            context,
                            context.createStore(ReadableAccountStore.class),
                            context.createStore(ReadableTokenStore.class));
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

            case FREEZE -> handlers.freezeHandler()
                    .preHandle(context, context.createStore(ReadableSpecialFileStore.class));

            case UNCHECKED_SUBMIT -> handlers.networkUncheckedSubmitHandler().preHandle(context);

            case SCHEDULE_CREATE -> handlers.scheduleCreateHandler().preHandle(context, this::dispatchPreHandle);
            case SCHEDULE_SIGN -> handlers.scheduleSignHandler()
                    .preHandle(context, context.createStore(ReadableScheduleStore.class), this::dispatchPreHandle);
            case SCHEDULE_DELETE -> handlers.scheduleDeleteHandler()
                    .preHandle(context, context.createStore(ReadableScheduleStore.class));
            case TOKEN_CREATION -> handlers.tokenCreateHandler().preHandle(context);
            case TOKEN_UPDATE -> handlers.tokenUpdateHandler()
                    .preHandle(context, context.createStore(ReadableTokenStore.class));
            case TOKEN_MINT -> handlers.tokenMintHandler()
                    .preHandle(context, context.createStore(ReadableTokenStore.class));
            case TOKEN_BURN -> handlers.tokenBurnHandler()
                    .preHandle(context, context.createStore(ReadableTokenStore.class));
            case TOKEN_DELETION -> handlers.tokenDeleteHandler()
                    .preHandle(context, context.createStore(ReadableTokenStore.class));
            case TOKEN_WIPE -> handlers.tokenAccountWipeHandler()
                    .preHandle(context, context.createStore(ReadableTokenStore.class));
            case TOKEN_FREEZE -> handlers.tokenFreezeAccountHandler()
                    .preHandle(context, context.createStore(ReadableTokenStore.class));
            case TOKEN_UNFREEZE -> handlers.tokenUnfreezeAccountHandler()
                    .preHandle(context, context.createStore(ReadableTokenStore.class));
            case TOKEN_GRANT_KYC -> handlers.tokenGrantKycToAccountHandler()
                    .preHandle(context, context.createStore(ReadableTokenStore.class));
            case TOKEN_REVOKE_KYC -> handlers.tokenRevokeKycFromAccountHandler()
                    .preHandle(context, context.createStore(ReadableTokenStore.class));
            case TOKEN_ASSOCIATE -> handlers.tokenAssociateToAccountHandler().preHandle(context);
            case TOKEN_DISSOCIATE -> handlers.tokenDissociateFromAccountHandler()
                    .preHandle(context);
            case TOKEN_FEE_SCHEDULE_UPDATE -> handlers.tokenFeeScheduleUpdateHandler()
                    .preHandle(context, context.createStore(ReadableTokenStore.class));
            case TOKEN_PAUSE -> handlers.tokenPauseHandler()
                    .preHandle(context, context.createStore(ReadableTokenStore.class));
            case TOKEN_UNPAUSE -> handlers.tokenUnpauseHandler()
                    .preHandle(context, context.createStore(ReadableTokenStore.class));

            case UTIL_PRNG -> handlers.utilPrngHandler().preHandle(context);

            case SYSTEM_DELETE -> {
                switch (txBody.systemDeleteOrThrow().id().kind()) {
                    case CONTRACT_ID -> handlers.contractSystemDeleteHandler().preHandle(context);
                    case FILE_ID -> handlers.fileSystemDeleteHandler().preHandle(context);
                    case UNSET -> throw new IllegalArgumentException("SystemDelete without IdCase");
                }
            }
            case SYSTEM_UNDELETE -> {
                switch (txBody.systemUndeleteOrThrow().id().kind()) {
                    case CONTRACT_ID -> handlers.contractSystemUndeleteHandler().preHandle(context);
                    case FILE_ID -> handlers.fileSystemUndeleteHandler().preHandle(context);
                    case UNSET -> throw new IllegalArgumentException("SystemUndelete without IdCase");
                }
            }

            default -> throw new UnsupportedOperationException(TYPE_NOT_SUPPORTED);
        }
    }

    // TODO: In all the below methods, commit will be called in workflow or some other place
    //  when handle workflow is implemented
    private void dispatchConsensusDeleteTopic(
            @NonNull final ConsensusDeleteTopicTransactionBody topicDeletion,
            @NonNull final WritableTopicStore topicStore) {
        final var handler = handlers.consensusDeleteTopicHandler();
        handler.handle(topicDeletion, topicStore);
        finishConsensusDeleteTopic(topicStore);
    }

    /**
     * A temporary hook to isolate logic that we expect to move to a workflow, but
     * is currently needed when running with facility implementations that are adapters
     * for either {@code mono-service} logic or integration tests.
     *
     * @param topicStore the topic store used for the update
     */
    protected void finishConsensusDeleteTopic(@NonNull final WritableTopicStore topicStore) {
        // No-op by default
    }

    private void dispatchConsensusUpdateTopic(
            @NonNull final ConsensusUpdateTopicTransactionBody topicUpdate,
            @NonNull final WritableTopicStore topicStore) {
        final var handler = handlers.consensusUpdateTopicHandler();
        handler.handle(handleContext, topicUpdate, topicStore);
        finishConsensusUpdateTopic(topicStore);
    }

    /**
     * A temporary hook to isolate logic that we expect to move to a workflow, but
     * is currently needed when running with facility implementations that are adapters
     * for either {@code mono-service} logic or integration tests.
     *
     * @param topicStore the topic store used for the update
     */
    protected void finishConsensusUpdateTopic(@NonNull final WritableTopicStore topicStore) {
        // No-op by default
    }

    private void dispatchConsensusCreateTopic(
            @NonNull final ConsensusCreateTopicTransactionBody topicCreation,
            @NonNull final WritableTopicStore topicStore) {
        final var handler = handlers.consensusCreateTopicHandler();
        final var recordBuilder = handler.newRecordBuilder();
        handler.handle(
                handleContext,
                topicCreation,
                new ConsensusServiceConfig(
                        dynamicProperties.maxNumTopics(), dynamicProperties.messageMaxBytesAllowed()),
                recordBuilder,
                topicStore);
        finishConsensusCreateTopic(recordBuilder, topicStore);
    }

    /**
     * A temporary hook to isolate logic that we expect to move to a workflow, but
     * is currently needed when running with facility implementations that are adapters
     * for either {@code mono-service} logic or integration tests.
     *
     * @param recordBuilder the completed record builder for the creation
     * @param topicStore the topic store used for the creation
     */
    protected void finishConsensusCreateTopic(
            @NonNull final ConsensusCreateTopicRecordBuilder recordBuilder,
            @NonNull final WritableTopicStore topicStore) {
        // No-op by default
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
        finishConsensusSubmitMessage(recordBuilder, topicStore);
    }

    /**
     * A temporary hook to isolate logic that we expect to move to a workflow, but
     * is currently needed when running with facility implementations that are adapters
     * for either {@code mono-service} logic or integration tests.
     *
     * @param recordBuilder the completed record builder for the message submission
     * @param topicStore the topic store used for the message submission
     */
    protected void finishConsensusSubmitMessage(
            @NonNull final ConsensusSubmitMessageRecordBuilder recordBuilder,
            @NonNull final WritableTopicStore topicStore) {
        // No-op by default
    }

    /**
     * Dispatches the token grant KYC transaction to the appropriate handler.
     *
     * @param tokenGrantKyc the token grant KYC transaction
     * @param tokenRelStore the token relation store
     */
    private void dispatchTokenGrantKycToAccount(
            TransactionBody tokenGrantKyc, WritableTokenRelationStore tokenRelStore) {
        final var handler = handlers.tokenGrantKycToAccountHandler();
        handler.handle(tokenGrantKyc, tokenRelStore);
        tokenRelStore.commit();
    }

    /**
     * Dispatches the token unpause transaction to the appropriate handler.
     * @param tokenUnpause the token unpause transaction
     * @param tokenStore the token store
     */
    private void dispatchTokenUnpause(
            @NonNull final TransactionBody tokenUnpause, @NonNull final WritableTokenStore tokenStore) {
        final var handler = handlers.tokenUnpauseHandler();
        handler.handle(tokenUnpause, tokenStore);
        tokenStore.commit();
    }

    /**
     * Dispatches the token pause transaction to the appropriate handler.
     * @param tokenPause the token pause transaction
     * @param tokenStore the token store
     */
    private void dispatchTokenPause(
            @NonNull final TransactionBody tokenPause, @NonNull final WritableTokenStore tokenStore) {
        final var handler = handlers.tokenPauseHandler();
        handler.handle(tokenPause, tokenStore);
        tokenStore.commit();
    }

    private void dispatchFreeze(
            @NonNull final FreezeTransactionBody freezeTxn,
            @NonNull final WritableSpecialFileStore specialFileStore,
            @NonNull final SwirldDualState dualState) {
        final var handler = handlers.freezeHandler();
        handler.handle(
                freezeTxn,
                new AdminServiceConfig(dynamicProperties.upgradeArtifactsLoc()),
                specialFileStore,
                dualState);
    }
}
