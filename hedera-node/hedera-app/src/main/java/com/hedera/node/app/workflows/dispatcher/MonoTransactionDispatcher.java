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

import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.TransactionRecord.EntropyOneOfType;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
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
    private final SideEffectsTracker sideEffectsTracker;

    @Inject
    public MonoTransactionDispatcher(
            @NonNull TransactionContext txnCtx,
            @NonNull TransactionHandlers handlers,
            @NonNull UsageLimits usageLimits,
            @NonNull SideEffectsTracker sideEffectsTracker) {
        super(handlers);
        this.txnCtx = requireNonNull(txnCtx);
        this.usageLimits = requireNonNull(usageLimits);
        this.sideEffectsTracker = requireNonNull(sideEffectsTracker);
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
            case CRYPTO_CREATE_ACCOUNT -> dispatchCryptoCreate(context);
            case CRYPTO_DELETE -> dispatchCryptoDelete(context);
            case CRYPTO_UPDATE_ACCOUNT -> dispatchCryptoUpdate(context);
            case FREEZE -> dispatchFreeze(context);
            case TOKEN_ASSOCIATE -> dispatchTokenAssociate(context);
            case TOKEN_DISSOCIATE -> dispatchTokenDissociate(context);
            case TOKEN_FREEZE -> dispatchTokenFreeze(context);
            case TOKEN_UNFREEZE -> dispatchTokenUnfreeze(context);
            case TOKEN_GRANT_KYC -> dispatchTokenGrantKycToAccount(context);
            case TOKEN_REVOKE_KYC -> dispatchTokenRevokeKycFromAccount(context);
            case TOKEN_PAUSE -> dispatchTokenPause(context);
            case TOKEN_UNPAUSE -> dispatchTokenUnpause(context);
            case TOKEN_FEE_SCHEDULE_UPDATE -> dispatchTokenFeeScheduleUpdate(context);
            case TOKEN_DELETION -> dispatchTokenDeletion(context);
            case UTIL_PRNG -> dispatchPrng(context);
            default -> throw new IllegalArgumentException(TYPE_NOT_SUPPORTED);
        }
    }
    // For all the below methods, commit is not called from stores, as it is responsibility of
    // handle workflow to call commit on WritableKVState.
    private void dispatchConsensusCreateTopic(@NonNull final HandleContext handleContext) {
        final var handler = handlers.consensusCreateTopicHandler();
        handler.handle(handleContext);
        finishConsensusCreateTopic(handleContext);
    }

    private void finishConsensusCreateTopic(@NonNull final HandleContext handleContext) {
        // Adapt the record builder outcome for mono-service
        final var recordBuilder = handleContext.recordBuilder(SingleTransactionRecordBuilder.class);
        txnCtx.setCreated(PbjConverter.fromPbj(recordBuilder.topicID()));
        // Adapt the metric impact for mono-service
        usageLimits.refreshTopics();
    }

    private void dispatchFreeze(@NonNull final HandleContext handleContext) {
        requireNonNull(handleContext);
        final var handler = handlers.freezeHandler();
        handler.handle(handleContext);
        finishFreeze(handleContext);
    }

    private void finishFreeze(@NonNull final HandleContext handleContext) {
        // Nothing to do
        // The only WritableStore that FreezeService uses is WritableFreezeStore
        // It is a thin wrapper around SwirldDualState instead of using WritableKVState like the other stores
        // SwirldDualState commits changes immediately instead of requiring a call to commit()
    }

    private void dispatchConsensusUpdateTopic(@NonNull final HandleContext handleContext) {
        final var handler = handlers.consensusUpdateTopicHandler();
        handler.handle(handleContext);
    }

    private void dispatchConsensusDeleteTopic(@NonNull final HandleContext handleContext) {
        final var handler = handlers.consensusDeleteTopicHandler();
        handler.handle(handleContext);
    }

    private void dispatchConsensusSubmitMessage(@NonNull final HandleContext handleContext) {
        final var handler = handlers.consensusSubmitMessageHandler();
        handler.handle(handleContext);
        finishConsensusSubmitMessage(handleContext);
    }

    private void finishConsensusSubmitMessage(@NonNull final HandleContext handleContext) {
        // Adapt the record builder outcome for mono-service
        final var recordBuilder = handleContext.recordBuilder(SingleTransactionRecordBuilder.class);
        txnCtx.setTopicRunningHash(
                PbjConverter.asBytes(recordBuilder.topicRunningHash()), recordBuilder.topicSequenceNumber());
    }

    private void dispatchCryptoCreate(@NonNull final HandleContext handleContext) {
        final var handler = handlers.cryptoCreateHandler();
        handler.handle(handleContext);
        finishCryptoCreate(handleContext);
    }

    private void finishCryptoCreate(@NonNull final HandleContext handleContext) {
        // If accounts can't be created, due to the usage of a price regime, throw an exception
        if (!usageLimits.areCreatableAccounts(1)) {
            throw new HandleException(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
        }
        // Adapt the record builder outcome for mono-service
        final var recordBuilder = handleContext.recordBuilder(SingleTransactionRecordBuilder.class);
        txnCtx.setCreated(PbjConverter.fromPbj(recordBuilder.accountID()));
    }

    private void dispatchTokenGrantKycToAccount(@NonNull final HandleContext handleContext) {
        final var handler = handlers.tokenGrantKycToAccountHandler();
        handler.handle(handleContext);
    }

    private void dispatchCryptoDelete(@NonNull final HandleContext handleContext) {
        final var handler = handlers.cryptoDeleteHandler();
        handler.handle(handleContext);
    }

    private void dispatchCryptoUpdate(@NonNull final HandleContext handleContext) {
        final var handler = handlers.cryptoUpdateHandler();
        handler.handle(handleContext);
    }

    private void dispatchTokenRevokeKycFromAccount(@NonNull final HandleContext handleContext) {
        final var handler = handlers.tokenRevokeKycFromAccountHandler();
        handler.handle(handleContext);
    }

    private void dispatchTokenAssociate(@NonNull final HandleContext handleContext) {
        final var handler = handlers.tokenAssociateToAccountHandler();
        handler.handle(handleContext);
    }

    private void dispatchTokenDissociate(@NonNull final HandleContext handleContext) {
        final var handler = handlers.tokenDissociateFromAccountHandler();
        handler.handle(handleContext);
    }

    private void dispatchTokenPause(@NonNull final HandleContext handleContext) {
        final var handler = handlers.tokenPauseHandler();
        handler.handle(handleContext);
    }

    private void dispatchTokenUnpause(@NonNull final HandleContext handleContext) {
        final var handler = handlers.tokenUnpauseHandler();
        handler.handle(handleContext);
    }

    private void dispatchTokenFreeze(@NonNull final HandleContext handleContext) {
        final var handler = handlers.tokenFreezeAccountHandler();
        handler.handle(handleContext);
    }

    private void dispatchTokenUnfreeze(@NonNull final HandleContext handleContext) {
        final var handler = handlers.tokenUnfreezeAccountHandler();
        handler.handle(handleContext);
    }

    private void dispatchPrng(@NonNull final HandleContext handleContext) {
        final var handler = handlers.utilPrngHandler();
        handler.handle(handleContext);
        finishUtilPrng(handleContext);
    }

    private void finishUtilPrng(@NonNull final HandleContext handleContext) {
        final var recordBuilder = handleContext.recordBuilder(SingleTransactionRecordBuilder.class);
        final var entropy = recordBuilder.entropy();
        if (entropy.kind() == EntropyOneOfType.PRNG_NUMBER) {
            sideEffectsTracker.trackRandomNumber((Integer) entropy.value());
        } else if (entropy.kind() == EntropyOneOfType.PRNG_BYTES) {
            sideEffectsTracker.trackRandomBytes(PbjConverter.asBytes((Bytes) entropy.value()));
        }
    }

    private void dispatchTokenFeeScheduleUpdate(@NonNull final HandleContext handleContext) {
        requireNonNull(handleContext);
        final var handler = handlers.tokenFeeScheduleUpdateHandler();
        handler.handle(handleContext);
    }

    private void dispatchTokenDeletion(@NonNull final HandleContext handleContext) {
        final var handler = handlers.tokenDeleteHandler();
        handler.handle(handleContext);
    }
}
