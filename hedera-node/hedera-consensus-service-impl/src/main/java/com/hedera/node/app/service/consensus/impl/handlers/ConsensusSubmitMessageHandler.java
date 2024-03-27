/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CHUNK_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SUBMIT_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOPIC_MESSAGE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.LONG_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.RECEIPT_STORAGE_TIME_SEC;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.TX_HASH_SIZE;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.asBytes;
import static com.hedera.node.app.service.mono.state.merkle.MerkleTopic.RUNNING_HASH_VERSION;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.consensus.ConsensusSubmitMessageTransactionBody;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.records.ConsensusSubmitMessageRecordBuilder;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.ConsensusConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONSENSUS_SUBMIT_MESSAGE}.
 */
@Singleton
public class ConsensusSubmitMessageHandler implements TransactionHandler {
    @Inject
    public ConsensusSubmitMessageHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);

        final var op = context.body().consensusSubmitMessageOrThrow();
        final var topicStore = context.createStore(ReadableTopicStore.class);
        // The topic ID must be present on the transaction and the topic must exist.
        final var topic = topicStore.getTopic(op.topicID());
        mustExist(topic, INVALID_TOPIC_ID);
        validateFalsePreCheck(topic.deleted(), INVALID_TOPIC_ID);
        // If a submit key is specified on the topic, then only those transactions signed by that key can be
        // submitted to the topic. If there is no submit key, then it is not required on the transaction.
        if (topic.hasSubmitKey()) {
            context.requireKeyOrThrow(topic.submitKeyOrThrow(), INVALID_SUBMIT_KEY);
        }
    }

    /**
     * Given the appropriate context, submits a message to a topic.
     *
     * @param handleContext the {@link HandleContext} for the active transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Override
    public void handle(@NonNull final HandleContext handleContext) {
        requireNonNull(handleContext);

        final var txn = handleContext.body();
        final var op = txn.consensusSubmitMessageOrThrow();

        final var topicStore = handleContext.writableStore(WritableTopicStore.class);
        final var topic = topicStore.getForModify(op.topicIDOrElse(TopicID.DEFAULT));

        /* Validate all needed fields in the transaction */
        final var config = handleContext.configuration().getConfigData(ConsensusConfig.class);
        validateTransaction(txn, config, topic);

        /* since we have validated topic exists, topic.get() is safe to be called */
        try {
            final var updatedTopic = updateRunningHashAndSequenceNumber(txn, topic.get(), handleContext.consensusNow());

            /* --- Put the modified topic. It will be in underlying state's modifications map.
            It will not be committed to state until commit is called on the state.--- */
            topicStore.put(updatedTopic);

            final var recordBuilder = handleContext.recordBuilder(ConsensusSubmitMessageRecordBuilder.class);
            recordBuilder
                    .topicRunningHash(updatedTopic.runningHash())
                    .topicSequenceNumber(updatedTopic.sequenceNumber())
                    .topicRunningHashVersion(RUNNING_HASH_VERSION);
        } catch (IOException e) {
            throw new HandleException(INVALID_TRANSACTION);
        }
    }

    /**
     * Validates te transaction body. Throws {@link HandleException} if any of the validations fail.
     * @param txn the {@link TransactionBody} of the active transaction
     * @param config the {@link ConsensusConfig}
     * @param topic the topic to which the message is being submitted
     */
    private void validateTransaction(
            final TransactionBody txn, final ConsensusConfig config, final Optional<Topic> topic) {
        final var txnId = txn.transactionID();
        final var payer = txn.transactionIDOrElse(TransactionID.DEFAULT).accountIDOrElse(AccountID.DEFAULT);
        final var op = txn.consensusSubmitMessageOrThrow();

        /* Check if the message submitted is empty */
        // Question do we need this check ?
        final var msgLen = op.message().length();
        if (msgLen == 0) {
            throw new HandleException(INVALID_TOPIC_MESSAGE);
        }

        /* Check if the message submitted is greater than acceptable size */
        if (msgLen > config.messageMaxBytesAllowed()) {
            throw new HandleException(MESSAGE_SIZE_TOO_LARGE);
        }

        /* Check if the topic exists */
        if (topic.isEmpty()) {
            throw new HandleException(INVALID_TOPIC_ID);
        }
        /* If the message is too large, user will be able to submit the message fragments in chunks. Validate if chunk info is correct */
        validateChunkInfo(txnId, payer, op);
    }

    /**
     * If the message is too large, user will be able to submit the message fragments in chunks.
     * Validates the chunk info in the transaction body.
     * Throws {@link HandleException} if any of the validations fail.
     * @param txnId the {@link TransactionID} of the active transaction
     * @param payer the {@link AccountID} of the payer
     * @param op the {@link ConsensusSubmitMessageTransactionBody} of the active transaction
     */
    private void validateChunkInfo(
            final TransactionID txnId, final AccountID payer, final ConsensusSubmitMessageTransactionBody op) {
        if (op.hasChunkInfo()) {
            var chunkInfo = op.chunkInfoOrThrow();

            /* Validate chunk number */
            if (!(1 <= chunkInfo.number() && chunkInfo.number() <= chunkInfo.total())) {
                throw new HandleException(INVALID_CHUNK_NUMBER);
            }

            /* Validate the initial chunk transaction payer is the same payer for the current transaction*/
            if (!chunkInfo
                    .initialTransactionIDOrElse(TransactionID.DEFAULT)
                    .accountIDOrElse(AccountID.DEFAULT)
                    .equals(payer)) {
                throw new HandleException(INVALID_CHUNK_TRANSACTION_ID);
            }

            /* Validate if the transaction is submitting initial chunk,payer in initial transaction Id should be same as payer of the transaction */
            if (1 == chunkInfo.number()
                    && !chunkInfo
                            .initialTransactionIDOrElse(TransactionID.DEFAULT)
                            .equals(txnId)) {
                throw new HandleException(INVALID_CHUNK_TRANSACTION_ID);
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
    public Topic updateRunningHashAndSequenceNumber(
            @NonNull final TransactionBody txn, @NonNull final Topic topic, @Nullable Instant consensusNow)
            throws IOException {
        requireNonNull(txn);
        requireNonNull(topic);

        final var submitMessage = txn.consensusSubmitMessageOrThrow();
        final var payer = txn.transactionIDOrElse(TransactionID.DEFAULT).accountIDOrElse(AccountID.DEFAULT);
        final var topicId = submitMessage.topicIDOrElse(TopicID.DEFAULT);
        final var message = asBytes(submitMessage.message());

        // This line will be uncommented once there is PBJ fix to make copyBuilder() public
        final var topicBuilder = topic.copyBuilder();

        if (null == consensusNow) {
            consensusNow = Instant.ofEpochSecond(0);
        }

        var sequenceNumber = topic.sequenceNumber();
        var runningHash = topic.runningHash();

        final var boas = new ByteArrayOutputStream();
        try (final var out = new ObjectOutputStream(boas)) {
            out.writeObject(asBytes(runningHash));
            out.writeLong(RUNNING_HASH_VERSION);
            out.writeLong(payer.shardNum());
            out.writeLong(payer.realmNum());
            out.writeLong(payer.accountNumOrElse(0L));
            out.writeLong(topicId.shardNum());
            out.writeLong(topicId.realmNum());
            out.writeLong(topicId.topicNum());
            out.writeLong(consensusNow.getEpochSecond());
            out.writeInt(consensusNow.getNano());

            /* Update the sequence number */
            topicBuilder.sequenceNumber(++sequenceNumber);

            out.writeLong(sequenceNumber);
            out.writeObject(noThrowSha384HashOf(message));
            out.flush();
            runningHash = Bytes.wrap(noThrowSha384HashOf(boas.toByteArray()));

            /* Update the running hash */
            topicBuilder.runningHash(runningHash);
        }
        return topicBuilder.build();
    }

    public static byte[] noThrowSha384HashOf(final byte[] byteArray) {
        try {
            return MessageDigest.getInstance("SHA-384").digest(byteArray);
        } catch (final NoSuchAlgorithmException fatal) {
            throw new IllegalStateException(fatal);
        }
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);
        final var op = feeContext.body().consensusSubmitMessageOrThrow();

        return feeContext
                .feeCalculator(SubType.DEFAULT)
                .addBytesPerTransaction(BASIC_ENTITY_ID_SIZE + op.message().length())
                .addNetworkRamByteSeconds((LONG_SIZE + TX_HASH_SIZE) * RECEIPT_STORAGE_TIME_SEC)
                .calculate();
    }
}
