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

import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTORENEW_ACCOUNT_NOT_ALLOWED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.service.consensus.impl.codecs.ConsensusServiceStateTranslator.pbjToState;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.spi.key.KeyUtils.isEmpty;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.consensus.ConsensusUpdateTopicTransactionBody;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.mono.fees.calculation.consensus.txns.UpdateTopicResourceUsage;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
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

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().consensusUpdateTopicOrThrow();
        final var topicStore = context.createStore(ReadableTopicStore.class);

        // The topic ID must be present on the transaction and the topic must exist.
        final var topic = topicStore.getTopic(op.topicID());
        mustExist(topic, INVALID_TOPIC_ID);

        // Extending the expiry is the *only* update operation permitted without an admin key. So if that is the
        // only thing this transaction is doing, then we don't need to worry about checking any additional keys.
        if (onlyExtendsExpiry(op)) {
            return;
        }

        // Any other modifications on this topic require the admin key.
        context.requireKeyOrThrow(topic.adminKey(), UNAUTHORIZED);

        // If the transaction is setting a new admin key, then the transaction must also be signed by that new key
        if (op.hasAdminKey()) {
            context.requireKey(op.adminKeyOrThrow());
        }

        // If the transaction is setting a new account for auto-renewals, then that account must also
        // have signed the transaction
        if (op.hasAutoRenewAccount()) {
            final var autoRenewAccountID = op.autoRenewAccountOrThrow();
            if (!designatesAccountRemoval(autoRenewAccountID)) {
                context.requireKeyOrThrow(autoRenewAccountID, INVALID_AUTORENEW_ACCOUNT);
            }
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
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Override
    public void handle(@NonNull final HandleContext handleContext) {
        requireNonNull(handleContext);

        final var topicUpdate = handleContext.body().consensusUpdateTopic();
        final var topicStore = handleContext.writableStore(WritableTopicStore.class);
        final var maybeTopic = requireNonNull(topicStore).get(topicUpdate.topicIDOrElse(TopicID.DEFAULT));
        validateTrue(maybeTopic.isPresent(), INVALID_TOPIC_ID);
        final var topic = maybeTopic.get();
        validateFalse(topic.deleted(), INVALID_TOPIC_ID);

        // First validate this topic is mutable; and the pending mutations are allowed
        validateFalse(topic.adminKey() == null && wantsToMutateNonExpiryField(topicUpdate), UNAUTHORIZED);
        if (!(topicUpdate.hasAutoRenewAccount() && designatesAccountRemoval(topicUpdate.autoRenewAccount()))
                && topic.hasAutoRenewAccountId()) {
            validateFalse(
                    !topic.hasAdminKey() || (topicUpdate.hasAdminKey() && isEmpty(topicUpdate.adminKey())),
                    AUTORENEW_ACCOUNT_NOT_ALLOWED);
        }
        validateMaybeNewAttributes(handleContext, topicUpdate, topic);

        // Now we apply the mutations to a builder
        final var builder = new Topic.Builder();
        // But first copy over the immutable topic attributes to the builder
        builder.topicId(topic.topicId());
        builder.sequenceNumber(topic.sequenceNumber());
        builder.runningHash(topic.runningHash());
        builder.deleted(topic.deleted());
        // And then resolve mutable attributes, and put the new topic back
        resolveMutableBuilderAttributes(handleContext, topicUpdate, builder, topic);
        topicStore.put(builder.build());
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);
        final var op = feeContext.body();
        final var topicUpdate = op.consensusUpdateTopicOrThrow();
        final var topicId = topicUpdate.topicIDOrElse(TopicID.DEFAULT);
        final var topic = feeContext.readableStore(ReadableTopicStore.class).getTopic(topicId);

        return feeContext.feeCalculator(SubType.DEFAULT).legacyCalculate(sigValueObj -> new UpdateTopicResourceUsage()
                .usageGivenExplicit(fromPbj(op), sigValueObj, topic != null ? pbjToState(topic) : null));
    }

    private void resolveMutableBuilderAttributes(
            @NonNull final HandleContext handleContext,
            @NonNull final ConsensusUpdateTopicTransactionBody op,
            @NonNull final Topic.Builder builder,
            @NonNull final Topic topic) {
        if (op.hasAdminKey()) {
            var key = op.adminKey();
            // Empty key list is allowed and is used for immutable entities (e.g. system accounts)
            if (handleContext.attributeValidator().isImmutableKey(key)) {
                builder.adminKey((Key) null);
            } else {
                builder.adminKey(key);
            }
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
        final var resolvedExpiryMeta = resolvedUpdateMetaFrom(handleContext.expiryValidator(), op, topic);
        builder.expirationSecond(resolvedExpiryMeta.expiry());
        builder.autoRenewPeriod(resolvedExpiryMeta.autoRenewPeriod());
        if (op.hasAutoRenewAccount() && designatesAccountRemoval(op.autoRenewAccount())) {
            builder.autoRenewAccountId((AccountID) null);
        } else {
            builder.autoRenewAccountId(resolvedExpiryMeta.autoRenewAccountId());
        }
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
        final var currentMeta =
                new ExpiryMeta(topic.expirationSecond(), topic.autoRenewPeriod(), topic.autoRenewAccountId());
        if (updatesExpiryMeta(op)) {
            final var updateMeta = new ExpiryMeta(effExpiryOf(op), effAutoRenewPeriodOf(op), op.autoRenewAccount());
            try {
                return expiryValidator.resolveUpdateAttempt(currentMeta, updateMeta);
            } catch (final HandleException e) {
                if (e.getStatus() == INVALID_RENEWAL_PERIOD) {
                    // Tokens throw INVALID_EXPIRATION_TIME, but for topic it's expected currently to throw
                    // AUTORENEW_DURATION_NOT_IN_RANGE
                    // future('8906')
                    throw new HandleException(AUTORENEW_DURATION_NOT_IN_RANGE);
                }
                throw e;
            }
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
        // Empty key list is allowed and is used for immutable entities (e.g. system accounts)
        if (op.hasAdminKey() && !attributeValidator.isImmutableKey(op.adminKey())) {
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

    public static boolean wantsToMutateNonExpiryField(@NonNull final ConsensusUpdateTopicTransactionBody op) {
        return op.hasMemo()
                || op.hasAdminKey()
                || op.hasSubmitKey()
                || op.hasAutoRenewPeriod()
                || op.hasAutoRenewAccount();
    }

    private boolean designatesAccountRemoval(AccountID id) {
        return id.shardNum() == 0
                && id.realmNum() == 0
                && id.hasAccountNum()
                && id.accountNum() == 0
                && id.alias() == null;
    }
}
