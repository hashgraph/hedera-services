package com.hedera.services.context.domain.topic;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hederahashgraph.api.proto.java.TopicID;
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.core.jproto.JTimestamp;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import static com.hedera.services.context.domain.topic.TopicDeserializer.TOPIC_DESERIALIZER;
import static com.hedera.services.context.domain.topic.TopicSerializer.TOPIC_SERIALIZER;

/**
 * A consensus service topic's memo, adminKey, submitKey, autoRenew duration and account, sequenceNumber and runningHash
 * expiration and deleted status.
 *
 * Optimize for small memory and serialized footprint.
 *
 * FastCopyable implemented, but there is no internally managed copy-on-write functionality, which is impractical
 * given JKey/JAccountID/JTimestamp mutability.
 *
 * Accessor interfaces follow the protobuf-paradigm. hasXyz() and getXyz() returning the default (non-null) value
 * if !hasXyz().
 *
 * Caller is expected to:
 * <ul>
 *   <li>not modify Topic (or its mutable child objects) once it's been used in an FCMap</li>
 *   <li>to edit a Topic in an FCMap - use copy constructor, <b>replace</b> fields requiring modification and then
 *   replace the Topic in the map.</li>
 * </ul>
 */
public final class Topic implements FastCopyable {
    public static Logger log = LogManager.getLogger(Topic.class);

    private String memo; // Null if empty/unset.
    private JKey adminKey; // Null if empty/unset.
    private JKey submitKey; // Null if empty/unset.
    private long autoRenewDurationSeconds;
    private JAccountID autoRenewAccountId; // Null if empty/unset.
    private JTimestamp expirationTimestamp; // Null if empty/unset.
    private boolean deleted;
    private final int RUNNING_HASH_BYTE_ARRAY_SIZE = 48;

    public static long RUNNING_HASH_VERSION = 2L;

    // sequenceNumber = 0 and runningHash = 48 bytes of '\0' before the first successful submitMessage() */
    private long sequenceNumber;
    private byte[] runningHash;

    public Topic() {}

    /**
     * Create a new topic.
     * @param memo short non-unique memo
     * @param adminKey the key to perform updateTopic/deleteTopic functions
     * @param submitKey the key (if any) to be able to submitMessage
     * @param autoRenewDurationSeconds
     * @param autoRenewAccountId
     * @param expirationTimestamp when submitMessage will start failing
     */
    public Topic(@Nullable String memo, @Nullable JKey adminKey, @Nullable JKey submitKey,
                 long autoRenewDurationSeconds, @Nullable JAccountID autoRenewAccountId,
                 @Nullable JTimestamp expirationTimestamp) {
        setMemo(memo);
        setAdminKey(adminKey);
        setSubmitKey(submitKey);
        setAutoRenewDurationSeconds(autoRenewDurationSeconds);
        setAutoRenewAccountId(autoRenewAccountId);
        setExpirationTimestamp(expirationTimestamp);
    }

    /**
     * Fast copy constructor for timestamps and keys. Shallow copy for immutables.
     * @param other
     * @throws Exception
     */
    public Topic(final Topic other) {
        this.memo = other.memo;
        this.adminKey = other.hasAdminKey() ? other.getAdminKey().clone() : null;
        this.submitKey = other.hasSubmitKey() ? other.getSubmitKey().clone() : null;
        this.autoRenewDurationSeconds = other.autoRenewDurationSeconds;
        this.autoRenewAccountId = other.hasAutoRenewAccountId() ? new JAccountID(other.autoRenewAccountId) : null;
        this.expirationTimestamp = other.hasExpirationTimestamp() ? new JTimestamp(other.expirationTimestamp) : null;
        this.deleted = other.deleted;

        this.sequenceNumber = other.sequenceNumber;
        this.runningHash = (null != other.runningHash) ? Arrays.copyOf(other.runningHash, other.runningHash.length)
                : null;
    }

    public boolean hasMemo() { return memo != null; }

    public String getMemo() { return hasMemo() ? memo : ""; }

    public void setMemo(@Nullable String memo) { this.memo = ((null != memo) && !memo.isEmpty()) ? memo : null; }

    public boolean hasAdminKey() { return adminKey != null; }

    public JKey getAdminKey() { return hasAdminKey() ? adminKey : getDefaultJKey(); }

    public void setAdminKey(@Nullable JKey adminKey) {
        this.adminKey = ((null != adminKey) && !adminKey.isEmpty()) ? adminKey : null;
    }

    public boolean hasSubmitKey() { return submitKey != null; }

    public JKey getSubmitKey() { return hasSubmitKey() ? submitKey : getDefaultJKey(); }

    public void setSubmitKey(@Nullable JKey submitKey) {
        this.submitKey = ((null != submitKey) && !submitKey.isEmpty()) ? submitKey : null;
    }

    public long getAutoRenewDurationSeconds() { return autoRenewDurationSeconds; }

    public void setAutoRenewDurationSeconds(long autoRenewDurationSeconds) {
        this.autoRenewDurationSeconds = autoRenewDurationSeconds;
    }

    public boolean hasAutoRenewAccountId() { return autoRenewAccountId != null; }

    public JAccountID getAutoRenewAccountId() {
        return hasAutoRenewAccountId() ? autoRenewAccountId : new JAccountID();
    }

    public void setAutoRenewAccountId(@Nullable JAccountID autoRenewAccountId) {
        this.autoRenewAccountId = ((null != autoRenewAccountId) && (0 != autoRenewAccountId.getAccountNum())) ?
                autoRenewAccountId : null;
    }

    public boolean hasExpirationTimestamp() { return expirationTimestamp != null; }

    public JTimestamp getExpirationTimestamp() {
        return hasExpirationTimestamp() ? expirationTimestamp : new JTimestamp();
    }

    public void setExpirationTimestamp(@Nullable JTimestamp expirationTimestamp) {
        if ((null != expirationTimestamp) && ((0 != expirationTimestamp.getSeconds()) ||
                (0 != expirationTimestamp.getNano()))) {
            this.expirationTimestamp = expirationTimestamp;
        } else {
            this.expirationTimestamp = null;
        }
    }

    public boolean isDeleted() { return deleted; }

    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public long getSequenceNumber() { return sequenceNumber; }

    public void setSequenceNumber(long sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public boolean hasRunningHash() { return runningHash != null; }

    public byte[] getRunningHash() { return (runningHash != null) ?
            runningHash : new byte[RUNNING_HASH_BYTE_ARRAY_SIZE]; }

    public void setRunningHash(@Nullable byte[] runningHash) {
        this.runningHash = ((null != runningHash) && (0 != runningHash.length)) ? runningHash : null;
    }

    /**
     * adminKeys and submitKeys will be considered unequal in cases where the objects structures are unequal.
     * So an adminKey with an empty Threshold key will NOT be considered equal to an adminKey with an empty KeyList,
     * even though they effectively cause the same functionality.
     * @param other
     * @return
     */
    @Override
    public boolean equals(@Nullable final Object other) {
        if (this == other) {
            return true;
        }
        if ((null == other) || (getClass() != other.getClass())) {
            return false;
        }
        Topic oo = (Topic) other;
        try {
            return Objects.equals(this.memo, oo.memo)

                    // There is no decent equals() on JKey, but they should be deterministically serializable.
                    && Arrays.equals(getAdminKey().serialize(), oo.getAdminKey().serialize())
                    && Arrays.equals(getSubmitKey().serialize(), oo.getSubmitKey().serialize())

                    && Objects.equals(this.autoRenewDurationSeconds, oo.autoRenewDurationSeconds)
                    && Objects.equals(this.autoRenewAccountId, oo.autoRenewAccountId)
                    && Objects.equals(this.expirationTimestamp, oo.expirationTimestamp)
                    && (this.deleted == oo.deleted)
                    && (this.sequenceNumber == oo.sequenceNumber)
                    && Arrays.equals(this.runningHash, oo.runningHash);
        } catch (IOException ex) {
            // It should not be possible that serialize() fails on either key.
            throw new KeySerializationException(ex.getMessage());
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(memo, adminKey, submitKey, autoRenewDurationSeconds, autoRenewAccountId,
                expirationTimestamp, deleted, sequenceNumber, runningHash);
    }

    @SuppressWarnings("unchecked")
    public static <T extends FastCopyable> T deserialize(DataInputStream in) throws IOException {
        return (T)TOPIC_DESERIALIZER.deserialize(in);
    }

    @Override
    public FastCopyable copy() {
        return new Topic(this);
    }

    @Override
    public void copyTo(FCDataOutputStream out) throws IOException {
        TOPIC_SERIALIZER.serialize(this, out);
    }

    /**
     * This has to be a NoOp method.
     * @param out
     * @throws IOException
     */
    @Override
    public void copyToExtra(FCDataOutputStream out) throws IOException {}

    /**
     * This has to be a NoOp method.
     */
    @Override
    public void delete() {}

    @Override
    public void copyFrom(FCDataInputStream in) { throw new UnsupportedOperationException(); }

    @Override
    public void copyFromExtra(FCDataInputStream in) { throw new UnsupportedOperationException(); }

    @Override
    public void diffCopyTo(FCDataOutputStream out, FCDataInputStream in) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void diffCopyFrom(final FCDataOutputStream out, final FCDataInputStream in) {
        throw new UnsupportedOperationException();
    }

    private JKey getDefaultJKey() {
        return new JKeyList(new ArrayList<JKey>());
    }

    /**
     * Increment the sequence number if this is not the initial transaction on the topic (the create), and update the
     * running hash of the Transactions on this topic (submitted messages and modifications of the topic).
     *
     * @param message
     * @param topicId
     * @param consensusTimestamp
     * @throws NoSuchAlgorithmException If the crypto library on this system doesn't support the SHA384 algorithm
     */
    public void updateRunningHashAndSequenceNumber(
            @Nullable byte[] message,
            @Nullable TopicID topicId,
            @Nullable Instant consensusTimestamp
    ) throws NoSuchAlgorithmException, IOException {
        if (null == message) {
            message = new byte[0];
        }
        if (null == topicId) {
            topicId = TopicID.newBuilder().build();
        }
        if (null == consensusTimestamp) {
            consensusTimestamp = Instant.ofEpochSecond(0);
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bos)) {
            out.writeObject(getRunningHash());
            out.writeLong(RUNNING_HASH_VERSION);
            out.writeLong(topicId.getShardNum());
            out.writeLong(topicId.getRealmNum());
            out.writeLong(topicId.getTopicNum());
            out.writeLong(consensusTimestamp.getEpochSecond());
            out.writeInt(consensusTimestamp.getNano());
            ++sequenceNumber;
            out.writeLong(sequenceNumber);
            out.writeObject(MessageDigest.getInstance("SHA-384").digest(message));
            out.flush();
            runningHash = MessageDigest.getInstance("SHA-384").digest(bos.toByteArray());
        }
    }

    public static class KeySerializationException extends RuntimeException {
        public KeySerializationException(String message){
            super(message);
        }
    }
}
