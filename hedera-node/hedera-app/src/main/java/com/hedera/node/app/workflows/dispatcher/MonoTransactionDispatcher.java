/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A {@link TransactionDispatcher} subclass that supports use of the {@code mono-service} workflow
 * in end-to-end tests (EETs) that run with {@code workflows.enabled}.
 *
 * <p>This mostly means a "premature" call to {@code topicStore.commit()}; but also requires
 * transferring information from the {@code recordBuilder} to the {@code txnCtx} for transactions
 * of type {@code topicCreate} and {@code submitMessage}.
 */
@Singleton
public class MonoTransactionDispatcher extends TransactionDispatcher {

    public static final String TYPE_NOT_SUPPORTED = "This transaction type is not supported";

    private final TransactionContext txnCtx;
    private final UsageLimits usageLimits;

    @Inject
    public MonoTransactionDispatcher(
            @NonNull TransactionContext txnCtx,
            @NonNull TransactionHandlers handlers,
            @NonNull UsageLimits usageLimits) {
        super(handlers);
        this.txnCtx = requireNonNull(txnCtx);
        this.usageLimits = requireNonNull(usageLimits);
    }

    /**
     * Dispatches a transaction of the given type to the appropriate handler.
     *
     * <p>This will not be final signature of the dispatch method, since as per
     * <a href="https://github.com/hashgraph/hedera-services/issues/4945">issue #4945</a>, we are currently
     * just adapting the last step of mono-service "workflow"; and only for Consensus Service transactions.
     *
     * @param context  the {@link HandleContext} for the transaction
     * @throws HandleException if the handler fails
     * @throws IllegalArgumentException if there is no handler for the given function type
     */
    @Override
    public void dispatchHandle(@NonNull final HandleContext context) {
        final var txBody = context.body();
        switch (txBody.data().kind()) {
            case CONSENSUS_CREATE_TOPIC -> dispatchConsensusCreateTopic(context);
            case CONSENSUS_UPDATE_TOPIC -> dispatchConsensusUpdateTopic(context);
            case CONSENSUS_DELETE_TOPIC -> dispatchConsensusDeleteTopic(context);
            case CONSENSUS_SUBMIT_MESSAGE -> dispatchConsensusSubmitMessage(context);
            case TOKEN_GRANT_KYC -> dispatchTokenGrantKycToAccount(context);
            case TOKEN_REVOKE_KYC -> dispatchTokenRevokeKycFromAccount(context);
            case TOKEN_PAUSE -> dispatchTokenPause(context);
            case TOKEN_UNPAUSE -> dispatchTokenUnpause(context);
            default -> throw new IllegalArgumentException(TYPE_NOT_SUPPORTED);
        }
    }

    // TODO: In all the below methods, commit will be called in workflow or some other place
    //  when handle workflow is implemented

    /**
     * A temporary hook to isolate logic that we expect to move to a workflow, but is currently needed when running with
     * facility implementations that are adapters for either {@code mono-service} logic or integration tests.
     *
     * @param handleContext the {@link HandleContext} for the transaction
     */
    private void finishConsensusCreateTopic(@NonNull final HandleContext handleContext) {
        // Adapt the record builder outcome for mono-service
        final var recordBuilder = handleContext.recordBuilder(SingleTransactionRecordBuilder.class);
        txnCtx.setCreated(PbjConverter.fromPbj(recordBuilder.topicID()));
        // Adapt the metric impact for mono-service
        usageLimits.refreshTopics();
        final var topicStore = handleContext.writableStore(WritableTopicStore.class);
        topicStore.commit();
    }

    /**
     * A temporary hook to isolate logic that we expect to move to a workflow, but is currently needed when running with
     * facility implementations that are adapters for either {@code mono-service} logic or integration tests.
     *
     * @param handleContext the {@link HandleContext} for the transaction
     */
    private void finishConsensusUpdateTopic(@NonNull final HandleContext handleContext) {
        final var topicStore = handleContext.writableStore(WritableTopicStore.class);
        topicStore.commit();
    }

    /**
     * A temporary hook to isolate logic that we expect to move to a workflow, but is currently needed when running with
     * facility implementations that are adapters for either {@code mono-service} logic or integration tests.
     *
     * @param handleContext the {@link HandleContext} for the transaction
     */
    private void finishConsensusDeleteTopic(@NonNull final HandleContext handleContext) {
        final var topicStore = handleContext.writableStore(WritableTopicStore.class);
        topicStore.commit();
    }

    /**
     * A temporary hook to isolate logic that we expect to move to a workflow, but is currently needed when running with
     * facility implementations that are adapters for either {@code mono-service} logic or integration tests.
     *
     * @param handleContext the {@link HandleContext} for the transaction
     */
    private void finishConsensusSubmitMessage(@NonNull final HandleContext handleContext) {
        // Adapt the record builder outcome for mono-service
        final var recordBuilder = handleContext.recordBuilder(SingleTransactionRecordBuilder.class);
        txnCtx.setTopicRunningHash(
                PbjConverter.asBytes(recordBuilder.topicRunningHash()), recordBuilder.topicSequenceNumber());
        final var topicStore = handleContext.writableStore(WritableTopicStore.class);
        topicStore.commit();
    }

    /**
     * A temporary hook to isolate logic that we expect to move to a workflow, but
     * is currently needed when running with facility implementations that are adapters
     * for either {@code mono-service} logic or integration tests.
     *
     * @param handleContext the {@link HandleContext} for the transaction
     */
    protected void finishTokenGrantKycToAccount(@NonNull final HandleContext handleContext) {
        final var tokenRelStore = handleContext.writableStore(WritableTokenRelationStore.class);
        tokenRelStore.commit();
    }

    /**
     * A temporary hook to isolate logic that we expect to move to a workflow, but
     * is currently needed when running with facility implementations that are adapters
     * for either {@code mono-service} logic or integration tests.
     *
     * @param handleContext the {@link HandleContext} for the transaction
     */
    protected void finishTokenRevokeKycFromAccount(@NonNull final HandleContext handleContext) {
        final var tokenRelStore = handleContext.writableStore(WritableTokenRelationStore.class);
        tokenRelStore.commit();
    }

    private void dispatchConsensusDeleteTopic(@NonNull final HandleContext handleContext) {
        final var handler = handlers.consensusDeleteTopicHandler();
        handler.handle(handleContext);
        finishConsensusDeleteTopic(handleContext);
    }

    private void dispatchConsensusUpdateTopic(@NonNull final HandleContext handleContext) {
        final var handler = handlers.consensusUpdateTopicHandler();
        handler.handle(handleContext);
        finishConsensusUpdateTopic(handleContext);
    }

    private void dispatchConsensusCreateTopic(@NonNull final HandleContext handleContext) {
        final var handler = handlers.consensusCreateTopicHandler();
        handler.handle(handleContext);
        finishConsensusCreateTopic(handleContext);
    }

    private void dispatchConsensusSubmitMessage(@NonNull final HandleContext handleContext) {
        final var handler = handlers.consensusSubmitMessageHandler();
        handler.handle(handleContext);
        finishConsensusSubmitMessage(handleContext);
    }

    /**
     * Dispatches the token grant KYC transaction to the appropriate handler.
     *
     * @param handleContext the {@link HandleContext} for the transaction
     */
    private void dispatchTokenGrantKycToAccount(@NonNull final HandleContext handleContext) {
        final var handler = handlers.tokenGrantKycToAccountHandler();
        handler.handle(handleContext);
        finishTokenGrantKycToAccount(handleContext);
    }

    /**
     * Dispatches the token revoke KYC transaction to the appropriate handler.
     *
     * @param handleContext the {@link HandleContext} for the transaction
     */
    private void dispatchTokenRevokeKycFromAccount(@NonNull final HandleContext handleContext) {
        final var handler = handlers.tokenRevokeKycFromAccountHandler();
        handler.handle(handleContext);
        finishTokenRevokeKycFromAccount(handleContext);
    }

    /**
     * Dispatches the token unpause transaction to the appropriate handler.
     *
     * @param handleContext the {@link HandleContext} for the transaction
     */
    private void dispatchTokenUnpause(@NonNull final HandleContext handleContext) {
        final var handler = handlers.tokenUnpauseHandler();
        handler.handle(handleContext);
        final var tokenStore = handleContext.writableStore(WritableTokenStore.class);
        tokenStore.commit();
    }

    /**
     * Dispatches the token pause transaction to the appropriate handler.
     *
     * @param handleContext the {@link HandleContext} for the transaction
     */
    private void dispatchTokenPause(@NonNull final HandleContext handleContext) {
        final var handler = handlers.tokenPauseHandler();
        handler.handle(handleContext);
        final var tokenStore = handleContext.writableStore(WritableTokenStore.class);
        tokenStore.commit();
    }

}
