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

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.node.app.service.mono.state.merkle.MerkleTopic.RUNNING_HASH_VERSION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_MESSAGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hashgraph.pbj.runtime.io.Bytes;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.config.ConsensusServiceConfig;
import com.hedera.node.app.service.consensus.impl.records.ConsensusSubmitMessageRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.SubmitMessageRecordBuilder;
import com.hedera.node.app.spi.exceptions.HandleStatusException;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#ConsensusSubmitMessage}.
 */
@Singleton
public class ConsensusSubmitMessageHandler implements TransactionHandler {
    @Inject
    public ConsensusSubmitMessageHandler() {}

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Determines signatures needed for submitting a new message to a consensus topic
     *
     * @param context the {@link PreHandleContext} which collects all information that will be
     *     passed to {@code handle()}
     * @param topicStore the {@link ReadableTopicStore} to use to resolve topic metadata
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context, @NonNull ReadableTopicStore topicStore) {
        requireNonNull(context);
        requireNonNull(topicStore);

        final var op = context.getTxn().getConsensusSubmitMessage();
        final var topicMeta = topicStore.getTopicMetadata(op.getTopicID());
        if (topicMeta.failed()) {
            context.status(ResponseCodeEnum.INVALID_TOPIC_ID);
            return;
        }

        final var submitKey = topicMeta.metadata().submitKey();
        submitKey.ifPresent(context::addToReqNonPayerKeys);
    }

    /**
     * Given the appropriate context, submits a message to a topic.
     *
     * @param handleContext the {@link HandleContext} for the active transaction
     * @param txn the {@link TransactionBody} of the active transaction
     * @param config the {@link ConsensusServiceConfig} for the active transaction
     * @param recordBuilder the {@link ConsensusSubmitMessageRecordBuilder} for the active transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(
            @NonNull final HandleContext handleContext,
            @NonNull final TransactionBody txn,
            @NonNull final ConsensusServiceConfig config,
            @NonNull final ConsensusSubmitMessageRecordBuilder recordBuilder,
            @NonNull final WritableTopicStore topicStore) {
        requireNonNull(handleContext);
        requireNonNull(txn);
        requireNonNull(config);
        requireNonNull(recordBuilder);
        requireNonNull(topicStore);

        final var op = txn.getConsensusSubmitMessage();
        final var topic = topicStore.getForModify(op.getTopicID().getTopicNum());
        /* Validate all needed fields in the transaction */
        validateTransaction(txn, config, topic);

        /* since we have validated topic exists, topic.get() is safe to be called */
        try {
            final var updatedTopic = updateRunningHashAndSequenceNumber(txn, topic.get(), handleContext.consensusNow());
            /* persist the updated topic */
            topicStore.put(updatedTopic);
        } catch (IOException e) {
            throw new HandleStatusException(INVALID_TRANSACTION);
        }
    }

    /**
     * Validates te transaction body. Throws {@link HandleStatusException} if any of the validations fail.
     * @param txn the {@link TransactionBody} of the active transaction
     * @param config the {@link ConsensusServiceConfig}
     * @param topic the topic to which the message is being submitted
     */
    private void validateTransaction(
            final TransactionBody txn, final ConsensusServiceConfig config, final Optional<Topic> topic) {
        final var txnId = txn.getTransactionID();
        final var payer = txn.getTransactionID().getAccountID();
        final var op = txn.getConsensusSubmitMessage();

        if (op.getMessage().isEmpty()) {
            throw new HandleStatusException(INVALID_TOPIC_MESSAGE);
        }

        if (op.getMessage().size() > config.maxMessageSize()) {
            throw new HandleStatusException(MESSAGE_SIZE_TOO_LARGE);
        }

        if (topic == null) {
            throw new HandleStatusException(INVALID_TOPIC_ID);
        }
        validateChunkInfo(txnId, payer, op);
    }

    /**
     * Validates the chunk info in the transaction body.
     * Throws {@link HandleStatusException} if any of the validations fail.
     * @param txnId the {@link TransactionID} of the active transaction
     * @param payer the {@link AccountID} of the payer
     * @param op the {@link ConsensusSubmitMessageTransactionBody} of the active transaction
     */
    private void validateChunkInfo(
            final TransactionID txnId, final AccountID payer, final ConsensusSubmitMessageTransactionBody op) {
        if (op.hasChunkInfo()) {
            var chunkInfo = op.getChunkInfo();
            if (!(1 <= chunkInfo.getNumber() && chunkInfo.getNumber() <= chunkInfo.getTotal())) {
                throw new HandleStatusException(INVALID_CHUNK_NUMBER);
            }
            if (!chunkInfo.getInitialTransactionID().getAccountID().equals(payer)) {
                throw new HandleStatusException(INVALID_CHUNK_TRANSACTION_ID);
            }
            if (1 == chunkInfo.getNumber()
                    && !chunkInfo.getInitialTransactionID().equals(txnId)) {
                throw new HandleStatusException(INVALID_CHUNK_TRANSACTION_ID);
            }
        }
    }

    /**
     * Updates the running hash and sequence number of the topic.
     * @param txn the {@link TransactionBody} of the active transaction
     * @param topic the topic to which the message is being submitted
     * @param consensusNow the consensus time of the active transaction
     * @return the updated topic
     * @throws IOException if there is an error while updating the running hash
     */
    public Topic updateRunningHashAndSequenceNumber(final TransactionBody txn, final Topic topic, Instant consensusNow)
            throws IOException {
        final var payer = txn.getTransactionID().getAccountID();
        final var topicId = txn.getConsensusSubmitMessage().getTopicID();
        final var message = txn.getConsensusSubmitMessage().getMessage().toByteArray();

//        final var topicBuilder = topic.copyBuilder();
        final var topicBuilder = new Topic.Builder()
                .topicNumber(topic.topicNumber())
                .adminKey(topic.adminKey())
                .submitKey(topic.submitKey())
                .autoRenewAccountNumber(topic.autoRenewAccountNumber())
                .autoRenewPeriod(topic.autoRenewPeriod())
                .expiry(topic.expiry())
                .deleted(topic.deleted())
                .memo(topic.memo());

        if (null == consensusNow) {
            consensusNow = Instant.ofEpochSecond(0);
        }

        var sequenceNumber = topic.sequenceNumber();
        var runningHash = topic.runningHash();

        final var boas = new ByteArrayOutputStream();
        try (final var out = new ObjectOutputStream(boas)) {
            out.writeObject(runningHash);
            out.writeLong(RUNNING_HASH_VERSION);
            out.writeLong(payer.getShardNum());
            out.writeLong(payer.getRealmNum());
            out.writeLong(payer.getAccountNum());
            out.writeLong(topicId.getShardNum());
            out.writeLong(topicId.getRealmNum());
            out.writeLong(topicId.getTopicNum());
            out.writeLong(consensusNow.getEpochSecond());
            out.writeInt(consensusNow.getNano());

            topicBuilder.sequenceNumber(++sequenceNumber);

            out.writeLong(sequenceNumber);
            out.writeObject(noThrowSha384HashOf(message));
            out.flush();
            runningHash = Bytes.wrap(noThrowSha384HashOf(boas.toByteArray()));

            topicBuilder.runningHash(runningHash);
        }
        return topicBuilder.build();
    }

    /** {@inheritDoc} */
    @Override
    public ConsensusSubmitMessageRecordBuilder newRecordBuilder() {
        return new SubmitMessageRecordBuilder();
    }
}
