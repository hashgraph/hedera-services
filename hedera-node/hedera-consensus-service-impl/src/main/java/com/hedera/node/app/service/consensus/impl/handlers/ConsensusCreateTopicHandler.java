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
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.config.ConsensusServiceConfig;
import com.hedera.node.app.service.consensus.impl.entity.TopicBuilderImpl;
import com.hedera.node.app.service.consensus.impl.records.ConsensusCreateTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.CreateTopicRecordBuilder;
import com.hedera.node.app.spi.exceptions.HandleStatusException;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#ConsensusCreateTopic}.
 */
@Singleton
public class ConsensusCreateTopicHandler implements TransactionHandler {
    @Inject
    public ConsensusCreateTopicHandler() {}

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
     *     passed to the handle stage
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context) {
        requireNonNull(context);
        final var op = context.getTxn().getConsensusCreateTopic();
        final var adminKey = asHederaKey(op.getAdminKey());
        adminKey.ifPresent(context::addToReqNonPayerKeys);
        final var submitKey = asHederaKey(op.getSubmitKey());
        submitKey.ifPresent(context::addToReqNonPayerKeys);

        if (op.hasAutoRenewAccount()) {
            final var autoRenewAccount = op.getAutoRenewAccount();
            context.addNonPayerKey(autoRenewAccount, ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT);
        }
    }

    /**
     * Given the appropriate context, creates a new topic.
     *
     *
     * @param handleContext          the {@link HandleContext} for the active transaction
     * @param op                     the {@link ConsensusCreateTopicTransactionBody} of the active transaction
     * @param consensusServiceConfig the {@link ConsensusServiceConfig} for the active transaction
     * @param recordBuilder          the {@link ConsensusCreateTopicRecordBuilder} for the active transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(
            @NonNull final HandleContext handleContext,
            @NonNull final ConsensusCreateTopicTransactionBody op,
            @NonNull final ConsensusServiceConfig consensusServiceConfig,
            @NonNull final ConsensusCreateTopicRecordBuilder recordBuilder,
            @NonNull final WritableTopicStore topicStore) {
        final var builder = new TopicBuilderImpl();

        /* validate admin and submit keys and set them */
        if (op.hasAdminKey()) {
            handleContext.attributeValidator().validateKey(op.getAdminKey());
            builder.adminKey(asHederaKey(op.getAdminKey()).get());
        }
        if (op.hasSubmitKey()) {
            handleContext.attributeValidator().validateKey(op.getSubmitKey());
            builder.submitKey(asHederaKey(op.getSubmitKey()).get());
        }

        /* validate if the current topic can be created */
        if (topicStore.getTopicState().size() >= consensusServiceConfig.maxTopics()) {
            throw new HandleStatusException(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
        }

        /* validate the topic memo */
        handleContext.attributeValidator().validateMemo(op.getMemo());
        builder.memo(op.getMemo());

        /* validate the auto-renew account */
        final var autoRenewPeriod =
                op.hasAutoRenewPeriod() ? op.getAutoRenewPeriod().getSeconds() : NA;
        final var autoRenewAccountId =
                op.hasAutoRenewAccount() ? op.getAutoRenewAccount().getAccountNum() : NA;

        final var entityExpiryMeta = new ExpiryMeta(
                NA, // expiry not set explicitly
                autoRenewPeriod,
                autoRenewAccountId);

        final var effectiveExpiryMeta = handleContext.expiryValidator().validateCreationAttempt(true, entityExpiryMeta);
        builder.autoRenewSecs(effectiveExpiryMeta.autoRenewPeriod());
        builder.expiry(effectiveExpiryMeta.expiry());
        builder.autoRenewAccountNumber(effectiveExpiryMeta.autoRenewNum());

        /* --- Do business logic --- */
        builder.topicNumber(handleContext.newEntityNumSupplier().getAsLong());

        /* --- Persist the topic --- */
        final var topic = builder.build();
        topicStore.put(topic);

        /* --- Build the record --- */
        recordBuilder.setCreatedTopic(topic.topicNumber());
    }

    @Override
    public ConsensusCreateTopicRecordBuilder newRecordBuilder() {
        return new CreateTopicRecordBuilder();
    }
}
