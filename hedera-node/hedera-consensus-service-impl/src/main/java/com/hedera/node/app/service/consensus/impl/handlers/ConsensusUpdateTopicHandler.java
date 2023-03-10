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

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.spi.exceptions.HandleStatusException.validateFalse;
import static com.hedera.node.app.spi.exceptions.HandleStatusException.validateTrue;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.consensus.ConsensusUpdateTopicTransactionBody;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.records.ConsensusUpdateTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.UpdateTopicRecordBuilder;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CONSENSUS_UPDATE_TOPIC}.
 */
@Singleton
public class ConsensusUpdateTopicHandler implements TransactionHandler {
    @Inject
    public ConsensusUpdateTopicHandler() {}

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Typically, this method validates the {@link TransactionBody} semantically, gathers all
     * required keys, warms the cache, and creates the {@link TransactionMetadata} that is used in
     * the handle stage.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param context the {@link PreHandleContext} which collects all information that will be
     *                passed to {@code #handle()}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context, @NonNull ReadableTopicStore topicStore) {
        requireNonNull(context);
        final var op = context.getTxn().consensusUpdateTopic().orElseThrow();

        if (onlyExtendsExpiry(op)) {
            return;
        }

        final var topicMeta = topicStore.getTopicMetadata(op.topicID());
        if (topicMeta.failed()) {
            context.status(ResponseCodeEnum.INVALID_TOPIC_ID);
            return;
        }

        final var adminKey = topicMeta.metadata().adminKey();
        if (adminKey.isPresent()) {
            context.addToReqNonPayerKeys(adminKey.get());
        }

        if (op.adminKey() != null) {
            asHederaKey(op.adminKey()).ifPresent(context::addToReqNonPayerKeys);
        }
        if (updatesToNonSentinelAutoRenewAccount(op)) {
            context.addNonPayerKey(
                    op.autoRenewAccount(),
                    ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT);
        }
    }

    private boolean updatesToNonSentinelAutoRenewAccount(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return op.autoRenewAccount() != null &&
                !AccountID.newBuilder().build().equals(op.autoRenewAccount());
    }

    private boolean onlyExtendsExpiry(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return op.expirationTime() == null
                && op.memo() == null
                && op.adminKey() == null
                && op.submitKey() == null
                && op.autoRenewPeriod() == null
                && op.autoRenewAccount() == null;
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
        final var maybeTopic =
                requireNonNull(topicStore).get(topicUpdate.topicID().topicNum());
        validateTrue(maybeTopic.isPresent(), ResponseCodeEnum.INVALID_TOPIC_ID);
        final var topic = maybeTopic.get();
        validateFalse(topic.deleted(), ResponseCodeEnum.INVALID_TOPIC_ID);

        // First validate this topic is mutable; and the pending mutations are allowed
        validateFalse(
                topic.adminKey() == null && wantsToMutateNonExpiryField(topicUpdate), ResponseCodeEnum.UNAUTHORIZED);
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
        if (op.adminKey() != null) {
            builder.adminKey(op.adminKey());
        } else {
            builder.adminKey(topic.adminKey());
        }
        if (op.submitKey() != null) {
            builder.submitKey(op.submitKey());
        } else {
            builder.submitKey(topic.submitKey());
        }
        if (op.memo().isPresent()) {
            builder.memo(op.memo().get());
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
        return Optional.ofNullable(op.expirationTime())
                .map(Timestamp::seconds)
                .orElse(NA);
    }

    private long effAutoRenewPeriodOf(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return Optional.ofNullable(op.autoRenewPeriod())
                .map(Duration::seconds)
                .orElse(NA);
    }

    private long effAutoRenewShardOf(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return Optional.ofNullable(op.autoRenewAccount())
                .map(AccountID::shardNum)
                .orElse(NA);
    }

    private long effAutoRenewNumOf(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return Optional.ofNullable(op.autoRenewAccount())
                .flatMap(AccountID::accountNum)
                .orElse(NA);
    }

    private long effAutoRenewRealmOf(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return Optional.ofNullable(op.autoRenewAccount())
                .map(AccountID::realmNum)
                .orElse(NA);
    }

    private boolean updatesExpiryMeta(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return op.expirationTime() != null
                || op.autoRenewPeriod() != null
                || op.autoRenewAccount() != null;
    }

    private void validateMaybeNewMemo(
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final ConsensusUpdateTopicTransactionBody op) {
        op.memo().ifPresent(attributeValidator::validateMemo);
    }

    private void validateMaybeNewAdminKey(
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final ConsensusUpdateTopicTransactionBody op) {
        if (op.adminKey() != null) {
            attributeValidator.validateKey(op.adminKey());
        }
    }

    private void validateMaybeNewSubmitKey(
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final ConsensusUpdateTopicTransactionBody op) {

        if (op.submitKey() != null) {
            attributeValidator.validateKey(op.submitKey());
        }
    }

    @Override
    public ConsensusUpdateTopicRecordBuilder newRecordBuilder() {
        return new UpdateTopicRecordBuilder();
    }

    public static boolean wantsToMutateNonExpiryField(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return op.memo() != null
                || op.adminKey() != null
                || op.submitKey() != null
                || op.autoRenewPeriod() != null
                || op.autoRenewAccount() != null;
    }
}
