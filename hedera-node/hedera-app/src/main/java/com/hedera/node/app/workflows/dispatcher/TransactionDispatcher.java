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
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusDeleteTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusUpdateTopicTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.config.ConsensusServiceConfig;
import com.hedera.node.app.service.consensus.impl.records.ConsensusCreateTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.ConsensusSubmitMessageRecordBuilder;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.service.util.impl.config.PrngConfig;
import com.hedera.node.app.service.util.records.PrngRecordBuilder;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A {@code TransactionDispatcher} provides functionality to forward pre-check, pre-handle, and handle-transaction
 * requests to the appropriate handler
 *
 * <p>For handle, mostly just supports the limited form of the Consensus Service handlers
 * described in https://github.com/hashgraph/hedera-services/issues/4945, while still trying to make a bit of progress
 * toward the general implementation.
 */
@Singleton
public class TransactionDispatcher {
    public static final String TYPE_NOT_SUPPORTED = "This transaction type is not supported";
    private final HandleContext handleContext;
    private final TransactionHandlers handlers;
    private final GlobalDynamicProperties dynamicProperties;

    /**
     * Creates a {@code TransactionDispatcher}.
     *
     * @param handleContext the context of the handle workflow
     * @param handlers the handlers for all transaction types
     * @param dynamicProperties the dynamic properties of the system
     */
    @Inject
    public TransactionDispatcher(
            @NonNull final HandleContext handleContext,
            @NonNull final TransactionHandlers handlers,
            @NonNull final GlobalDynamicProperties dynamicProperties) {
        this.handlers = requireNonNull(handlers);
        this.handleContext = requireNonNull(handleContext);
        this.dynamicProperties = requireNonNull(dynamicProperties);
    }

    /**
     * Dispatches a transaction of the given type to the appropriate handler.
     *
     * <p>This will not be final signature of the dispatch method, since as per
     * <a href="https://github.com/hashgraph/hedera-services/issues/4945">issue #4945</a>, we are currently
     * just adapting the last step of mono-service "workflow"; and only for Consensus Service transactions.
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
            case TOKEN_REVOKE_KYC_FROM_ACCOUNT -> dispatchTokenRevokeKycFromAccount(
                    txn, writableStoreFactory.createTokenRelStore());
            case TOKEN_PAUSE -> dispatchTokenPause(txn, writableStoreFactory.createTokenStore());
            case TOKEN_UNPAUSE -> dispatchTokenUnpause(txn, writableStoreFactory.createTokenStore());
            case CRYPTO_CREATE -> dispatchCryptoCreate(txn, writableStoreFactory.createAccountStore());
            case UTIL_PRNG -> dispatchPrng(txn);
            default -> throw new IllegalArgumentException(TYPE_NOT_SUPPORTED);
        }
    }

    /**
     * Dispatch a pre-handle request. It is forwarded to the correct handler, which takes care of the specific
     * functionality
     *
     * @param context the context of the pre-handle workflow
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    //    @SuppressWarnings("java:S1479") // ignore too many branches warning
    public void dispatchPreHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context, "The supplied argument 'context' cannot be null!");

        try {
            final var handler = getHandler(context.body());
            handler.preHandle(context);
        } catch (UnsupportedOperationException ex) {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
        }
    }

    @NonNull
    private TransactionHandler getHandler(@NonNull final TransactionBody txBody) {
        return switch (txBody.data().kind()) {
            case CONSENSUS_CREATE_TOPIC -> handlers.consensusCreateTopicHandler();
            case CONSENSUS_UPDATE_TOPIC -> handlers.consensusUpdateTopicHandler();
            case CONSENSUS_DELETE_TOPIC -> handlers.consensusDeleteTopicHandler();
            case CONSENSUS_SUBMIT_MESSAGE -> handlers.consensusSubmitMessageHandler();

            case CONTRACT_CREATE_INSTANCE -> handlers.contractCreateHandler();
            case CONTRACT_UPDATE_INSTANCE -> handlers.contractUpdateHandler();
            case CONTRACT_CALL -> handlers.contractCallHandler();
            case CONTRACT_DELETE_INSTANCE -> handlers.contractDeleteHandler();
            case ETHEREUM_TRANSACTION -> handlers.etherumTransactionHandler();

            case CRYPTO_CREATE_ACCOUNT -> handlers.cryptoCreateHandler();
            case CRYPTO_UPDATE_ACCOUNT -> handlers.cryptoUpdateHandler();
            case CRYPTO_TRANSFER -> handlers.cryptoTransferHandler();
            case CRYPTO_DELETE -> handlers.cryptoDeleteHandler();
            case CRYPTO_APPROVE_ALLOWANCE -> handlers.cryptoApproveAllowanceHandler();
            case CRYPTO_DELETE_ALLOWANCE -> handlers.cryptoDeleteAllowanceHandler();
            case CRYPTO_ADD_LIVE_HASH -> handlers.cryptoAddLiveHashHandler();
            case CRYPTO_DELETE_LIVE_HASH -> handlers.cryptoDeleteLiveHashHandler();

            case FILE_CREATE -> handlers.fileCreateHandler();
            case FILE_UPDATE -> handlers.fileUpdateHandler();
            case FILE_DELETE -> handlers.fileDeleteHandler();
            case FILE_APPEND -> handlers.fileAppendHandler();

            case FREEZE -> handlers.freezeHandler();

            case UNCHECKED_SUBMIT -> handlers.networkUncheckedSubmitHandler();

            case SCHEDULE_CREATE -> handlers.scheduleCreateHandler();
            case SCHEDULE_SIGN -> handlers.scheduleSignHandler();
            case SCHEDULE_DELETE -> handlers.scheduleDeleteHandler();

            case TOKEN_CREATION -> handlers.tokenCreateHandler();
            case TOKEN_UPDATE -> handlers.tokenUpdateHandler();
            case TOKEN_MINT -> handlers.tokenMintHandler();
            case TOKEN_BURN -> handlers.tokenBurnHandler();
            case TOKEN_DELETION -> handlers.tokenDeleteHandler();
            case TOKEN_WIPE -> handlers.tokenAccountWipeHandler();
            case TOKEN_FREEZE -> handlers.tokenFreezeAccountHandler();
            case TOKEN_UNFREEZE -> handlers.tokenUnfreezeAccountHandler();
            case TOKEN_GRANT_KYC -> handlers.tokenGrantKycToAccountHandler();
            case TOKEN_REVOKE_KYC -> handlers.tokenRevokeKycFromAccountHandler();
            case TOKEN_ASSOCIATE -> handlers.tokenAssociateToAccountHandler();
            case TOKEN_DISSOCIATE -> handlers.tokenDissociateFromAccountHandler();
            case TOKEN_FEE_SCHEDULE_UPDATE -> handlers.tokenFeeScheduleUpdateHandler();
            case TOKEN_PAUSE -> handlers.tokenPauseHandler();
            case TOKEN_UNPAUSE -> handlers.tokenUnpauseHandler();

            case UTIL_PRNG -> handlers.utilPrngHandler();

            case SYSTEM_DELETE -> switch (txBody.systemDeleteOrThrow().id().kind()) {
                case CONTRACT_ID -> handlers.contractSystemDeleteHandler();
                case FILE_ID -> handlers.fileSystemDeleteHandler();
                default -> throw new UnsupportedOperationException("SystemDelete without IdCase");
            };
            case SYSTEM_UNDELETE -> switch (txBody.systemUndeleteOrThrow().id().kind()) {
                case CONTRACT_ID -> handlers.contractSystemUndeleteHandler();
                case FILE_ID -> handlers.fileSystemUndeleteHandler();
                default -> throw new UnsupportedOperationException("SystemUndelete without IdCase");
            };

            default -> throw new UnsupportedOperationException(TYPE_NOT_SUPPORTED);
        };
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
     * A temporary hook to isolate logic that we expect to move to a workflow, but is currently needed when running with
     * facility implementations that are adapters for either {@code mono-service} logic or integration tests.
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
     * A temporary hook to isolate logic that we expect to move to a workflow, but is currently needed when running with
     * facility implementations that are adapters for either {@code mono-service} logic or integration tests.
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
     * A temporary hook to isolate logic that we expect to move to a workflow, but is currently needed when running with
     * facility implementations that are adapters for either {@code mono-service} logic or integration tests.
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
     * A temporary hook to isolate logic that we expect to move to a workflow, but is currently needed when running with
     * facility implementations that are adapters for either {@code mono-service} logic or integration tests.
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
            @NonNull final TransactionBody tokenGrantKyc, @NonNull final WritableTokenRelationStore tokenRelStore) {
        final var handler = handlers.tokenGrantKycToAccountHandler();
        handler.handle(tokenGrantKyc, tokenRelStore);
        finishTokenGrantKycToAccount(tokenRelStore);
    }

    /**
     * A temporary hook to isolate logic that we expect to move to a workflow, but
     * is currently needed when running with facility implementations that are adapters
     * for either {@code mono-service} logic or integration tests.
     *
     * @param tokenRelStore the token rel store used for the message submission
     */
    protected void finishTokenGrantKycToAccount(@NonNull final WritableTokenRelationStore tokenRelStore) {
        // No-op by default
    }

    /**
     * Dispatches the token revoke KYC transaction to the appropriate handler.
     *
     * @param tokenRevokeKyc the token revoke KYC transaction
     * @param tokenRelStore the token relation store
     */
    private void dispatchTokenRevokeKycFromAccount(
            @NonNull TransactionBody tokenRevokeKyc, @NonNull WritableTokenRelationStore tokenRelStore) {
        final var handler = handlers.tokenRevokeKycFromAccountHandler();
        handler.handle(tokenRevokeKyc, tokenRelStore);
        finishTokenRevokeKycFromAccount(tokenRelStore);
    }

    /**
     * A temporary hook to isolate logic that we expect to move to a workflow, but
     * is currently needed when running with facility implementations that are adapters
     * for either {@code mono-service} logic or integration tests.
     *
     * @param tokenRelStore the token rel store used for the message submission
     */
    protected void finishTokenRevokeKycFromAccount(@NonNull final WritableTokenRelationStore tokenRelStore) {
        // No-op by default
    }

    /**
     * Dispatches the token unpause transaction to the appropriate handler.
     *
     * @param tokenUnpause the token unpause transaction
     * @param tokenStore the token store
     */
    private void dispatchTokenUnpause(
            @NonNull final TransactionBody tokenUnpause, @NonNull final WritableTokenStore tokenStore) {
        final var handler = handlers.tokenUnpauseHandler();
        handler.handle(tokenUnpause, tokenStore);
        finishTokenUnPause(tokenStore);
    }

    /**
     * Dispatches the token pause transaction to the appropriate handler.
     *
     * @param tokenPause the token pause transaction
     * @param tokenStore the token store
     */
    private void dispatchTokenPause(
            @NonNull final TransactionBody tokenPause, @NonNull final WritableTokenStore tokenStore) {
        final var handler = handlers.tokenPauseHandler();
        handler.handle(tokenPause, tokenStore);
        finishTokenPause(tokenStore);
    }

    /**
     * A temporary hook to isolate logic that we expect to move to a workflow, but
     * is currently needed when running with facility implementations that are adapters
     * for either {@code mono-service} logic or integration tests.
     *
     * @param tokenStore the token store
     */
    protected void finishTokenPause(@NonNull final WritableTokenStore tokenStore) {
        // No-op by default
    }

    /**
     * A temporary hook to isolate logic that we expect to move to a workflow, but
     * is currently needed when running with facility implementations that are adapters
     * for either {@code mono-service} logic or integration tests.
     *
     * @param tokenStore the token store
     */
    protected void finishTokenUnPause(@NonNull final WritableTokenStore tokenStore) {
        // No-op by default
    }

    /**
     * Dispatches the util prng transaction to the appropriate handler.
     * @param utilPrng the util prng transaction body
     */
    private void dispatchPrng(@NonNull final TransactionBody utilPrng) {
        final var handler = handlers.utilPrngHandler();
        final var recordBuilder = handler.newRecordBuilder();
        handler.handle(
                handleContext,
                utilPrng.utilPrng(),
                new PrngConfig(dynamicProperties.isUtilPrngEnabled()),
                recordBuilder);
        finishUtilPrng(recordBuilder);
    }

    /**
     * A temporary hook to isolate logic that we expect to move to a workflow, but
     * is currently needed when running with facility implementations that are adapters
     * for either {@code mono-service} logic or integration tests.
     *
     * @param recordBuilder the record builder
     */
    protected void finishUtilPrng(@NonNull final PrngRecordBuilder recordBuilder) {
        // No-op by default
    }

    /**
     * Dispatches the crypto create transaction to the appropriate handler.
     * @param cryptoCreate the crypto create transaction body
     * @param accountStore the writable account store
     */
    private void dispatchCryptoCreate(
            @NonNull final TransactionBody cryptoCreate, @NonNull final WritableAccountStore accountStore) {
        final var handler = handlers.cryptoCreateHandler();
        final var recordBuilder = handler.newRecordBuilder();
        handler.handle(handleContext, cryptoCreate, accountStore, recordBuilder);
        finishCryptoCreate(recordBuilder, accountStore);
    }

    /**
     * A temporary hook to isolate logic that we expect to move to a workflow, but
     * is currently needed when running with facility implementations that are adapters
     * for either {@code mono-service} logic or integration tests.
     *
     * @param recordBuilder the completed record builder for the creation
     * @param accountStore the account store used for the creation
     */
    protected void finishCryptoCreate(
            @NonNull final CryptoCreateRecordBuilder recordBuilder, @NonNull final WritableAccountStore accountStore) {
        // No-op by default
    }
}
