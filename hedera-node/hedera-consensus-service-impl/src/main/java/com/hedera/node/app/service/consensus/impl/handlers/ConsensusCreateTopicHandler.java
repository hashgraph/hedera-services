/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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
import static com.hedera.hapi.node.base.ResponseCodeEnum.BAD_ENCODING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FEE_EXEMPT_KEY_LIST_CONTAINS_DUPLICATED_KEYS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_KEY_IN_FEE_EXEMPT_KEY_LIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTRIES_FOR_FEE_EXEMPT_KEY_LIST_EXCEEDED;
import static com.hedera.node.app.hapi.utils.fee.ConsensusServiceFeeBuilder.getConsensusCreateTopicFee;
import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.RUNNING_HASH_BYTE_ARRAY_SIZE;
import static com.hedera.node.app.spi.validation.AttributeValidator.isImmutableKey;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.records.ConsensusCreateTopicStreamBuilder;
import com.hedera.node.app.service.consensus.impl.validators.ConsensusCustomFeesValidator;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.PureChecksContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TopicsConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.FeeData;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONSENSUS_CREATE_TOPIC}.
 */
@Singleton
public class ConsensusCreateTopicHandler implements TransactionHandler {
    private final ConsensusCustomFeesValidator customFeesValidator;

    /**
     * Default constructor for injection.
     * @param customFeesValidator custom fees validator
     */
    @Inject
    public ConsensusCreateTopicHandler(@NonNull final ConsensusCustomFeesValidator customFeesValidator) {
        requireNonNull(customFeesValidator);
        this.customFeesValidator = customFeesValidator;
    }

    @Override
    public void pureChecks(@NonNull final PureChecksContext context) throws PreCheckException {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.consensusCreateTopicOrThrow();
        final var uniqueKeysCount = op.feeExemptKeyList().stream().distinct().count();
        validateTruePreCheck(
                uniqueKeysCount == op.feeExemptKeyList().size(), FEE_EXEMPT_KEY_LIST_CONTAINS_DUPLICATED_KEYS);
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().consensusCreateTopicOrThrow();

        // The transaction cannot set the admin key unless the transaction was signed by that key
        if (op.hasAdminKey()) {
            context.requireKeyOrThrow(op.adminKey(), BAD_ENCODING);
            //  context.requireKeyOrThrow(op.adminKey(), INVALID_ADMIN_KEY); ref #7770
        }

        // If an account is to be used for auto-renewal, then the account must exist and the transaction
        // must be signed with that account's key.
        if (op.hasAutoRenewAccount()) {
            final var autoRenewAccountID = op.autoRenewAccount();
            context.requireKeyOrThrow(autoRenewAccountID, INVALID_AUTORENEW_ACCOUNT);
        }
    }

    /**
     * Given the appropriate context, creates a new topic.
     *
     * @param handleContext the {@link HandleContext} for the active transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Override
    public void handle(@NonNull final HandleContext handleContext) {
        requireNonNull(handleContext, "The argument 'context' must not be null");

        final var op = handleContext.body().consensusCreateTopicOrThrow();
        final var topicStore = handleContext.storeFactory().writableStore(WritableTopicStore.class);

        validateSemantics(op, handleContext);

        final var builder = new Topic.Builder();
        if (op.hasAdminKey() && !isImmutableKey(op.adminKey())) {
            builder.adminKey(op.adminKey());
        }
        if (op.hasSubmitKey()) {
            builder.submitKey(op.submitKey());
        }
        if (op.hasFeeScheduleKey()) {
            builder.feeScheduleKey(op.feeScheduleKey());
        }
        builder.feeExemptKeyList(op.feeExemptKeyList());
        builder.customFees(op.customFees());
        builder.memo(op.memo());

        final var impliedExpiry = handleContext.consensusNow().getEpochSecond()
                + op.autoRenewPeriodOrElse(Duration.DEFAULT).seconds();

        final var entityExpiryMeta = new ExpiryMeta(
                impliedExpiry, op.autoRenewPeriodOrElse(Duration.DEFAULT).seconds(), op.autoRenewAccount());

        try {
            final var effectiveExpiryMeta = handleContext
                    .expiryValidator()
                    .resolveCreationAttempt(false, entityExpiryMeta, HederaFunctionality.CONSENSUS_CREATE_TOPIC);

            // HapiTest, TopicCreateSuite.signingRequirementsEnforced() expects error code from resolveCreationAttempt()
            // before the following check
            if (op.hasAutoRenewAccount()) {
                validateTrue(op.hasAdminKey(), AUTORENEW_ACCOUNT_NOT_ALLOWED);
            }

            builder.autoRenewPeriod(effectiveExpiryMeta.autoRenewPeriod());
            builder.expirationSecond(effectiveExpiryMeta.expiry());
            builder.autoRenewAccountId(effectiveExpiryMeta.autoRenewAccountId());

            /* --- Add topic id to topic builder --- */
            builder.topicId(TopicID.newBuilder()
                    .topicNum(handleContext.entityNumGenerator().newEntityNum())
                    .build());

            builder.runningHash(Bytes.wrap(new byte[RUNNING_HASH_BYTE_ARRAY_SIZE]));

            /* --- Put the final topic. It will be in underlying state's modifications map.
            It will not be committed to state until commit is called on the state.--- */
            final var topic = builder.build();
            topicStore.putAndIncrementCount(topic);

            /* --- Build the record with newly created topic --- */
            final var recordBuilder =
                    handleContext.savepointStack().getBaseBuilder(ConsensusCreateTopicStreamBuilder.class);

            recordBuilder.topicID(topic.topicId());
        } catch (final HandleException e) {
            if (e.getStatus() == INVALID_EXPIRATION_TIME) {
                // Since for some reason TopicCreateTransactionBody does not have an expiration time,
                // it makes more sense to propagate AUTORENEW_DURATION_NOT_IN_RANGE
                throw new HandleException(AUTORENEW_DURATION_NOT_IN_RANGE);
            }
            throw e;
        }
    }

    private void validateSemantics(ConsensusCreateTopicTransactionBody op, HandleContext handleContext) {

        final var configuration = handleContext.configuration();
        final var topicConfig = configuration.getConfigData(TopicsConfig.class);
        final var topicStore = handleContext.storeFactory().readableStore(ReadableTopicStore.class);
        final var accountStore = handleContext.storeFactory().readableStore(ReadableAccountStore.class);
        final var tokenStore = handleContext.storeFactory().readableStore(ReadableTokenStore.class);
        final var tokenRelStore = handleContext.storeFactory().readableStore(ReadableTokenRelationStore.class);

        // Validate admin and submit keys and set them. Empty key list is allowed and is used for immutable entities
        if (op.hasAdminKey() && !isImmutableKey(op.adminKey())) {
            handleContext.attributeValidator().validateKey(op.adminKey());
        }

        // submitKey() is not checked in preCheck()
        if (op.hasSubmitKey()) {
            handleContext.attributeValidator().validateKey(op.submitKey());
        }

        // validate hasFeeScheduleKey()
        if (op.hasFeeScheduleKey()) {
            handleContext.attributeValidator().validateKey(op.feeScheduleKey(), INVALID_CUSTOM_FEE_SCHEDULE_KEY);
        }

        // validate fee exempt key list
        validateTrue(
                op.feeExemptKeyList().size() <= topicConfig.maxEntriesForFeeExemptKeyList(),
                MAX_ENTRIES_FOR_FEE_EXEMPT_KEY_LIST_EXCEEDED);
        op.feeExemptKeyList()
                .forEach(
                        key -> handleContext.attributeValidator().validateKey(key, INVALID_KEY_IN_FEE_EXEMPT_KEY_LIST));

        // validate custom fees
        validateTrue(op.customFees().size() <= topicConfig.maxCustomFeeEntriesForTopics(), CUSTOM_FEES_LIST_TOO_LONG);
        customFeesValidator.validate(
                accountStore, tokenRelStore, tokenStore, op.customFees(), handleContext.expiryValidator());

        /* Validate if the current topic can be created */
        validateTrue(
                topicStore.sizeOfState() < topicConfig.maxNumber(), MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);

        /* Validate the topic memo */
        handleContext.attributeValidator().validateMemo(op.memo());
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);
        final var body = feeContext.body();
        final var hasCustomFees =
                !body.consensusCreateTopicOrThrow().customFees().isEmpty();
        final var subType = hasCustomFees ? SubType.TOPIC_CREATE_WITH_CUSTOM_FEES : SubType.DEFAULT;

        return feeContext
                .feeCalculatorFactory()
                .feeCalculator(subType)
                .legacyCalculate(sigValueObj -> usageGiven(CommonPbjConverters.fromPbj(body), sigValueObj));
    }

    private FeeData usageGiven(
            final com.hederahashgraph.api.proto.java.TransactionBody txn, final SigValueObj sigUsage) {
        return getConsensusCreateTopicFee(txn, sigUsage);
    }
}
