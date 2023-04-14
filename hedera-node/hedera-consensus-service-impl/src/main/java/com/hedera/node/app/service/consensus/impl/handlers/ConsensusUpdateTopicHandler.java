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

package com.hedera.node.app.service.consensus.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.consensus.ConsensusUpdateTopicTransactionBody;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.records.ConsensusUpdateTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.UpdateTopicRecordBuilder;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONSENSUS_UPDATE_TOPIC}.
 */
@Singleton
public class ConsensusUpdateTopicHandler implements TransactionHandler {
    @Inject
    public ConsensusUpdateTopicHandler() {
        // Exists for injection
    }

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Typically, this method validates the {@link TransactionBody} semantically, gathers all
     * required keys, and warms the cache.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param context the {@link PreHandleContext} which collects all information that will be
     *     passed to {@code #handle()}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(
            @NonNull final PreHandleContext context, @NonNull ReadableTopicStore topicStore)
            throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().consensusUpdateTopicOrThrow();

        // The topic ID must be present on the transaction and the topic must exist.
        final var topic = topicStore.getTopicMetadata(op.topicID());
        mustExist(topic, INVALID_TOPIC_ID);

        // Extending the expiry is the *only* update operation permitted without an admin key. So if
        // that is the
        // only thing this transaction is doing, then we don't need to worry about checking any
        // additional keys.
        if (onlyExtendsExpiry(op)) {
            return;
        }

        // Any other modifications on this topic require the admin key.
        context.requireKeyOrThrow(topic.adminKey(), UNAUTHORIZED);

        // If the transaction is setting a new admin key, then the transaction must also be signed
        // by that new key
        if (op.hasAdminKey()) {
            context.requireKey(op.adminKeyOrThrow());
        }

        // If the transaction is setting a new account for auto-renewals, then that account must
        // also
        // have signed the transaction
        if (op.hasAutoRenewAccount()) {
            final var autoRenewAccountID = op.autoRenewAccountOrThrow();
            context.requireKeyOrThrow(autoRenewAccountID, INVALID_AUTORENEW_ACCOUNT);
        }
    }

    private boolean onlyExtendsExpiry(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return op.hasExpirationTime()
                && !op.hasMemo()
                && !op.hasAdminKey()
                && !op.hasSubmitKey()
                && !op.hasAutoRenewPeriod()
                && !op.hasAutoRenewAccount();
    }

    /**
     * Given the appropriate context, updates a topic.
     *
     * @param handleContext the {@link HandleContext} for the active transaction
     * @param topicUpdate   the {@link ConsensusUpdateTopicTransactionBody} of the active transaction
     * @param topicStore    the {@link WritableTopicStore} for the active transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(
            @NonNull final HandleContext handleContext,
            @NonNull final ConsensusUpdateTopicTransactionBody topicUpdate,
            @NonNull final WritableTopicStore topicStore) {
        final var maybeTopic = requireNonNull(topicStore)
                .get(topicUpdate.topicIDOrElse(TopicID.DEFAULT).topicNum());
        validateTrue(maybeTopic.isPresent(), INVALID_TOPIC_ID);
        final var topic = maybeTopic.get();
        validateFalse(topic.deleted(), INVALID_TOPIC_ID);

        // First validate this topic is mutable; and the pending mutations are allowed
        validateFalse(topic.adminKey() == null && wantsToMutateNonExpiryField(topicUpdate), UNAUTHORIZED);
        validateMaybeNewAttributes(handleContext, topicUpdate, topic);

        // Now we apply the mutations to a builder
        final var builder = new Topic.Builder();
        // But first copy over the immutable topic attributes to the builder
        builder.topicNumber(topic.topicNumber());
        builder.sequenceNumber(topic.sequenceNumber());
        builder.runningHash(topic.runningHash());
        builder.deleted(topic.deleted());
        // And then resolve mutable attributes, and put the new topic back
        resolveMutableBuilderAttributes(handleContext.expiryValidator(), topicUpdate, builder, topic);
        topicStore.put(builder.build());
    }

    private void resolveMutableBuilderAttributes(
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final ConsensusUpdateTopicTransactionBody op,
            @NonNull final Topic.Builder builder,
            @NonNull final Topic topic) {
        if (op.hasAdminKey()) {
            builder.adminKey(op.adminKey());
        } else {
            builder.adminKey(topic.adminKey());
        }
        if (op.hasSubmitKey()) {
            builder.submitKey(op.submitKey());
        } else {
            builder.submitKey(topic.submitKey());
        }
        if (op.hasMemo()) {
            builder.memo(op.memo());
        } else {
            builder.memo(topic.memo());
        }
        final var resolvedExpiryMeta = resolvedUpdateMetaFrom(expiryValidator, op, topic);
        builder.expiry(resolvedExpiryMeta.expiry());
        builder.autoRenewPeriod(resolvedExpiryMeta.autoRenewPeriod());
        builder.autoRenewAccountNumber(resolvedExpiryMeta.autoRenewNum());
    }

    private void validateMaybeNewAttributes(
            @NonNull final HandleContext handleContext,
            @NonNull final ConsensusUpdateTopicTransactionBody op,
            @NonNull final Topic topic) {
        validateMaybeNewAdminKey(handleContext.attributeValidator(), op);
        validateMaybeNewSubmitKey(handleContext.attributeValidator(), op);
        validateMaybeNewMemo(handleContext.attributeValidator(), op);
        validateMaybeNewExpiry(handleContext.expiryValidator(), op, topic);
    }

    private void validateMaybeNewExpiry(
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final ConsensusUpdateTopicTransactionBody op,
            @NonNull final Topic topic) {
        resolvedUpdateMetaFrom(expiryValidator, op, topic);
    }

    private ExpiryMeta resolvedUpdateMetaFrom(
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final ConsensusUpdateTopicTransactionBody op,
            @NonNull final Topic topic) {
        final var currentMeta = new ExpiryMeta(topic.expiry(), topic.autoRenewPeriod(), topic.autoRenewAccountNumber());
        if (updatesExpiryMeta(op)) {
            final var updateMeta = new ExpiryMeta(
                    effExpiryOf(op),
                    effAutoRenewPeriodOf(op),
                    effAutoRenewShardOf(op),
                    effAutoRenewRealmOf(op),
                    effAutoRenewNumOf(op));
            return expiryValidator.resolveUpdateAttempt(currentMeta, updateMeta);
        } else {
            return currentMeta;
        }
    }

    private long effExpiryOf(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return op.hasExpirationTime() ? op.expirationTimeOrThrow().seconds() : NA;
    }

    private long effAutoRenewPeriodOf(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return op.hasAutoRenewPeriod() ? op.autoRenewPeriodOrThrow().seconds() : NA;
    }

    private long effAutoRenewShardOf(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return op.hasAutoRenewAccount() ? op.autoRenewAccountOrThrow().shardNum() : NA;
    }

    private long effAutoRenewRealmOf(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return op.hasAutoRenewAccount() ? op.autoRenewAccountOrThrow().realmNum() : NA;
    }

    private long effAutoRenewNumOf(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return op.hasAutoRenewAccount() ? op.autoRenewAccountOrThrow().accountNumOrElse(NA) : NA;
    }

    private boolean updatesExpiryMeta(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return op.expirationTime() != null || op.autoRenewPeriod() != null || op.autoRenewAccount() != null;
    }

    private void validateMaybeNewMemo(
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final ConsensusUpdateTopicTransactionBody op) {
        if (op.hasMemo()) {
            attributeValidator.validateMemo(op.memo());
        }
    }

    private void validateMaybeNewAdminKey(
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final ConsensusUpdateTopicTransactionBody op) {
        if (op.hasAdminKey()) {
            attributeValidator.validateKey(op.adminKey());
        }
    }

    private void validateMaybeNewSubmitKey(
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final ConsensusUpdateTopicTransactionBody op) {
        if (op.hasSubmitKey()) {
            attributeValidator.validateKey(op.submitKey());
        }
    }

    @Override
    public ConsensusUpdateTopicRecordBuilder newRecordBuilder() {
        return new UpdateTopicRecordBuilder();
    }

    public static boolean wantsToMutateNonExpiryField(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return op.hasMemo()
                || op.hasAdminKey()
                || op.hasSubmitKey()
                || op.hasAutoRenewPeriod()
                || op.hasAutoRenewAccount();
    }
}
