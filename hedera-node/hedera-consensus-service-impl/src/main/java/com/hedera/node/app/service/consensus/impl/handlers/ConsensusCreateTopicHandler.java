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

import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.RUNNING_HASH_BYTE_ARRAY_SIZE;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.config.ConsensusServiceConfig;
import com.hedera.node.app.service.consensus.impl.records.ConsensusCreateTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.CreateTopicRecordBuilder;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONSENSUS_CREATE_TOPIC}.
 */
@Singleton
public class ConsensusCreateTopicHandler implements TransactionHandler {
    @Inject
    public ConsensusCreateTopicHandler() {
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
     *     passed to the handle stage
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().consensusCreateTopicOrThrow();

        // The transaction cannot set the admin key unless the transaction was signed by that key
        if (op.hasAdminKey()) {
            context.requireKeyOrThrow(op.adminKeyOrThrow(), INVALID_ADMIN_KEY);
        }

        // If an account is to be used for auto-renewal, then the account must exist and the transaction
        // must be signed with that account's key.
        if (op.hasAutoRenewAccount()) {
            final var autoRenewAccountID = op.autoRenewAccountOrThrow();
            context.requireKeyOrThrow(autoRenewAccountID, INVALID_AUTORENEW_ACCOUNT);
        }
    }

    /**
     * Given the appropriate context, creates a new topic.
     *
     * @param handleContext the {@link HandleContext} for the active transaction
     * @param op the {@link ConsensusCreateTopicTransactionBody} of the active transaction
     * @param consensusServiceConfig the {@link ConsensusServiceConfig} for the active transaction
     * @param recordBuilder the {@link ConsensusCreateTopicRecordBuilder} for the active transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(
            @NonNull final HandleContext handleContext,
            @NonNull final ConsensusCreateTopicTransactionBody op,
            @NonNull final ConsensusServiceConfig consensusServiceConfig,
            @NonNull final ConsensusCreateTopicRecordBuilder recordBuilder,
            @NonNull final WritableTopicStore topicStore) {
        final var builder = new Topic.Builder();

        /* Validate admin and submit keys and set them */
        if (op.hasAdminKey()) {
            handleContext.attributeValidator().validateKey(op.adminKey());
            builder.adminKey(op.adminKey());
        }
        if (op.hasSubmitKey()) {
            handleContext.attributeValidator().validateKey(op.submitKey());
            builder.submitKey(op.submitKey());
        }

        /* Validate if the current topic can be created */
        if (topicStore.sizeOfState() >= consensusServiceConfig.maxTopics()) {
            throw new HandleException(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
        }

        /* Validate the topic memo */
        handleContext.attributeValidator().validateMemo(op.memo());
        builder.memo(op.memo());

        final var impliedExpiry = handleContext.consensusNow().getEpochSecond()
                + op.autoRenewPeriodOrElse(Duration.DEFAULT).seconds();
        final var entityExpiryMeta = new ExpiryMeta(
                impliedExpiry,
                op.autoRenewPeriodOrElse(Duration.DEFAULT).seconds(),
                // Shard and realm will be ignored if num is NA
                op.hasAutoRenewAccount() ? op.autoRenewAccount().shardNum() : NA,
                op.hasAutoRenewAccount() ? op.autoRenewAccount().realmNum() : NA,
                op.hasAutoRenewAccount() ? op.autoRenewAccount().accountNumOrElse(NA) : NA);

        try {
            final var effectiveExpiryMeta =
                    handleContext.expiryValidator().resolveCreationAttempt(false, entityExpiryMeta);
            builder.autoRenewPeriod(effectiveExpiryMeta.autoRenewPeriod());
            builder.expiry(effectiveExpiryMeta.expiry());
            builder.autoRenewAccountNumber(effectiveExpiryMeta.autoRenewNum());

            /* --- Add topic number to topic builder --- */
            builder.topicNumber(handleContext.newEntityNumSupplier().getAsLong());

            builder.runningHash(Bytes.wrap(new byte[RUNNING_HASH_BYTE_ARRAY_SIZE]));

            /* --- Put the final topic. It will be in underlying state's modifications map.
            It will not be committed to state until commit is called on the state.--- */
            final var topic = builder.build();
            topicStore.put(topic);

            /* --- Build the record with newly created topic --- */
            recordBuilder.setCreatedTopic(topic.topicNumber());
        } catch (final HandleException e) {
            if (e.getStatus() == INVALID_EXPIRATION_TIME) {
                // Since for some reason TopicCreateTransactionBody does not have an expiration time,
                // it makes more sense to propagate AUTORENEW_DURATION_NOT_IN_RANGE
                throw new HandleException(AUTORENEW_DURATION_NOT_IN_RANGE);
            }
            throw e;
        }
    }

    @Override
    public ConsensusCreateTopicRecordBuilder newRecordBuilder() {
        return new CreateTopicRecordBuilder();
    }
}
