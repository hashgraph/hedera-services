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

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalse;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.spi.validation.EntityExpiryMetadata.NA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_ACCOUNT_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.config.ConsensusServiceConfig;
import com.hedera.node.app.service.consensus.impl.entity.TopicBuilderImpl;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.meta.PreHandleContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.records.ConsensusCreateTopicRecordBuilder;
import com.hedera.node.app.spi.validation.EntityExpiryMetadata;
import com.hedera.node.app.spi.workflows.TransactionHandler;
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
     *     passed to {@link #handle(HandleContext, TransactionBody, ConsensusServiceConfig, ConsensusCreateTopicRecordBuilder, WritableTopicStore)}
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
     * Given the appropriate context needed to execute the logic to create a new topic.
     *
     * @param handleContext          the {@link HandleContext} for the active transaction
     * @param txnBody                the {@link TransactionBody} of the active transaction
     * @param consensusServiceConfig the {@link ConsensusServiceConfig} for the active transaction
     * @param recordBuilder          the {@link ConsensusCreateTopicRecordBuilder} for the active transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(
            @NonNull final HandleContext handleContext,
            @NonNull final TransactionBody txnBody,
            @NonNull final ConsensusServiceConfig consensusServiceConfig,
            @NonNull final ConsensusCreateTopicRecordBuilder recordBuilder,
            @NonNull final WritableTopicStore topicStore) {
        final var op = txnBody.getConsensusCreateTopic();
        final var builder = new TopicBuilderImpl();

        /* validate admin and submit keys and set them */
        if (op.hasAdminKey()) {
            final var validateAdminKey = handleContext.attributeValidator().validateKey(op.getAdminKey());
            validateTrue(validateAdminKey == OK, validateAdminKey);
            builder.adminKey(asHederaKey(op.getAdminKey()).get());
        }
        if (op.hasSubmitKey()) {
            if (handleContext.attributeValidator().validateKey(op.getSubmitKey()) == OK) {
                builder.adminKey(asHederaKey(op.getSubmitKey()).get());
            }
            final var validateSubmitKey = handleContext.attributeValidator().validateKey(op.getSubmitKey());
            validateTrue(validateSubmitKey == OK, validateSubmitKey);
            builder.adminKey(asHederaKey(op.getSubmitKey()).get());
        }

        /* validate if the current topic can be created */
        final var currentNumTopics = handleContext.entityCreationLimits().getNumTopics();
        validateTrue(
                currentNumTopics + 1L <= consensusServiceConfig.maxTopics(),
                MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);

        /* validate the topic memo */
        final var memoValidation =
                handleContext.attributeValidator().validateMemo(op.getMemo().getBytes());
        validateTrue(OK == memoValidation, memoValidation);
        builder.memo(op.getMemo());

        //        validateTrue(op.hasAutoRenewPeriod(), INVALID_RENEWAL_PERIOD); is this needed?
        final var autoRenewPeriod =
                op.hasAutoRenewPeriod() ? op.getAutoRenewPeriod().getSeconds() : NA;
        final var autoRenewAccountId = op.getAutoRenewAccount();

        final var entityExpiryMeta = new EntityExpiryMetadata(
                NA, // since we can estimate expiry , not sur if this is expected?
                op.hasAutoRenewPeriod() ? op.getAutoRenewPeriod().getSeconds() : NA,
                op.hasAutoRenewAccount() ? op.getAutoRenewAccount().getAccountNum() : NA);

        final var expiryMetadataValidation =
                handleContext.expiryValidator().validateCreationAttempt(true, entityExpiryMeta);
        validateTrue(expiryMetadataValidation == OK, expiryMetadataValidation);

        /* validate the auto-renew account */
        if (op.hasAutoRenewAccount()) {
            final var account = handleContext.accountLookup().getAccountById(autoRenewAccountId);
            validateTrue(account.isPresent(), INVALID_AUTORENEW_ACCOUNT);

            validateFalse(account.get().isSmartContract(), INVALID_AUTORENEW_ACCOUNT);
            validateTrue(op.hasAdminKey(), AUTORENEW_ACCOUNT_NOT_ALLOWED);
        }

        /* --- Do business logic --- */
        builder.topicNumber(handleContext.newEntityNumSupplier().getAsLong());

        /* --- Persist the topic --- */
        topicStore.put(builder.build());
        handleContext.entityCreationLimits().refreshTopics();
    }
}
