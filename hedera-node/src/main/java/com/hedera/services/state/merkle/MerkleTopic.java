package com.hedera.services.state.merkle;

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

import com.google.common.base.MoreObjects;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.serdes.TopicSerde;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.FCMValue;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.io.SerializedObjectProvider;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleNode;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongycastle.util.encoders.Hex;

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

import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.asLiteralString;

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
public final class MerkleTopic extends AbstractMerkleNode implements FCMValue, MerkleLeaf {
    public static Logger log = LogManager.getLogger(MerkleTopic.class);

    public static final int MAX_MEMO_BYTES = 1_024;
    public static final int RUNNING_HASH_BYTE_ARRAY_SIZE = 48;
    public static final long RUNNING_HASH_VERSION = 3L;

    static final int MERKLE_VERSION = 1;
    static final long RUNTIME_CONSTRUCTABLE_ID = 0xcfc535576b57baf0L;

    static TopicSerde topicSerde = new TopicSerde();
    static DomainSerdes serdes = new DomainSerdes();
    static EntityId.Provider legacyIdProvider = EntityId.LEGACY_PROVIDER;

    public static final MerkleTopic.Provider LEGACY_PROVIDER = new Provider();

    private String memo;
    private JKey adminKey;
    private JKey submitKey;
    private long autoRenewDurationSeconds;
    private EntityId autoRenewAccountId;
    private RichInstant expirationTimestamp;
    private boolean deleted;

    // Before the first message is submitted to this topic, its sequenceNumber is 0 and runningHash is 48 bytes of '\0'
    private long sequenceNumber;
    private byte[] runningHash;

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("memo", memo)
                .add("expiry",
                        String.format("%d.%d", expirationTimestamp.getSeconds(), expirationTimestamp.getNanos()))
                .add("deleted", deleted)
                .add("adminKey", MiscUtils.describe(adminKey))
                .add("submitKey", MiscUtils.describe(submitKey))
                .add("runningHash", (runningHash != null) ? Hex.toHexString(runningHash) : "<N/A>")
                .add("sequenceNumber", sequenceNumber)
				.add("autoRenewSecs", autoRenewDurationSeconds)
                .add("autoRenewAccount", asLiteralString(asAccount(autoRenewAccountId)))
                .toString();
    }

    public MerkleTopic() {}

    /**
     * Create a new topic.
     * @param memo short non-unique memo
     * @param adminKey the key to perform updateTopic/deleteTopic functions
     * @param submitKey the key (if any) to be able to submitMessage
     * @param autoRenewDurationSeconds
     * @param autoRenewAccountId
     * @param expirationTimestamp when submitMessage will start failing
     */
    public MerkleTopic(
            @Nullable String memo,
            @Nullable JKey adminKey,
            @Nullable JKey submitKey,
            long autoRenewDurationSeconds,
            @Nullable EntityId autoRenewAccountId,
            @Nullable RichInstant expirationTimestamp
    ) {
        setMemo(memo);
        setAdminKey(adminKey);
        setSubmitKey(submitKey);
        setAutoRenewDurationSeconds(autoRenewDurationSeconds);
        setAutoRenewAccountId(autoRenewAccountId);
        setExpirationTimestamp(expirationTimestamp);
    }

    public MerkleTopic(final MerkleTopic other) {
        this.memo = other.memo;
        this.adminKey = other.hasAdminKey() ? other.getAdminKey().clone() : null;
        this.submitKey = other.hasSubmitKey() ? other.getSubmitKey().clone() : null;
        this.autoRenewDurationSeconds = other.autoRenewDurationSeconds;
        this.autoRenewAccountId = other.hasAutoRenewAccountId() ? other.autoRenewAccountId : null;
        this.expirationTimestamp = other.hasExpirationTimestamp() ? other.expirationTimestamp : null;
        this.deleted = other.deleted;

        this.sequenceNumber = other.sequenceNumber;
        this.runningHash = (null != other.runningHash)
                ? Arrays.copyOf(other.runningHash, other.runningHash.length)
                : null;
    }

    @Deprecated
    public static class Provider implements SerializedObjectProvider {
        @Override
        @SuppressWarnings("unchecked")
        public FastCopyable deserialize(DataInputStream din) throws IOException {
            var in = (SerializableDataInputStream)din;

            in.readShort();
            in.readShort();

        	var topic = new MerkleTopic();
            if (in.readBoolean()) {
                var bytes = in.readByteArray(MAX_MEMO_BYTES);
                if (null != bytes) {
                    topic.setMemo(StringUtils.newStringUtf8(bytes));
                }
            }

            if (in.readBoolean()) {
                topic.setAdminKey(serdes.deserializeKey(in));
            }
            if (in.readBoolean()) {
                topic.setSubmitKey(serdes.deserializeKey(in));
            }
            topic.setAutoRenewDurationSeconds(in.readLong());
            if (in.readBoolean()) {
                topic.setAutoRenewAccountId(legacyIdProvider.deserialize(in));
            }
            if (in.readBoolean()) {
                topic.setExpirationTimestamp(serdes.deserializeLegacyTimestamp(in));
            }
            topic.setDeleted(in.readBoolean());
            topic.setSequenceNumber(in.readLong());
            if (in.readBoolean()) {
                topic.setRunningHash(in.readByteArray(RUNNING_HASH_BYTE_ARRAY_SIZE));
            }
            return topic;
        }
    }

    /* --- MerkleLeaf --- */
    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return MERKLE_VERSION;
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
    	topicSerde.deserializeV1(in, this);
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
    	topicSerde.serialize(this, out);
    }

    /* --- FastCopyable --- */

    @Override
    public MerkleTopic copy() {
        return new MerkleTopic(this);
    }

    @Override
    public void delete() { }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if ((null == o) || !MerkleTopic.class.equals(o.getClass())) {
            return false;
        }
        MerkleTopic that = (MerkleTopic) o;
        try {
            return Objects.equals(this.memo, that.memo)
                    && Arrays.equals(getAdminKey().serialize(), that.getAdminKey().serialize())
                    && Arrays.equals(getSubmitKey().serialize(), that.getSubmitKey().serialize())
                    && Objects.equals(this.autoRenewDurationSeconds, that.autoRenewDurationSeconds)
                    && Objects.equals(this.autoRenewAccountId, that.autoRenewAccountId)
                    && Objects.equals(this.expirationTimestamp, that.expirationTimestamp)
                    && (this.deleted == that.deleted)
                    && (this.sequenceNumber == that.sequenceNumber)
                    && Arrays.equals(this.runningHash, that.runningHash);
        } catch (IOException ex) {
            throw new KeySerializationException(ex.getMessage());
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                memo,
                adminKey,
                submitKey,
                autoRenewDurationSeconds,
                autoRenewAccountId,
                expirationTimestamp,
                deleted,
                sequenceNumber,
                runningHash);
    }

    @Override
    @Deprecated
    public void copyFrom(SerializableDataInputStream in) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public void copyFromExtra(SerializableDataInputStream in) {
        throw new UnsupportedOperationException();
    }

    /* --- Helpers --- */

    private JKey getDefaultJKey() {
        return new JKeyList(new ArrayList<>());
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
            AccountID payer,
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

        var boas = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(boas)) {
            out.writeObject(getRunningHash());
            out.writeLong(RUNNING_HASH_VERSION);
            out.writeLong(payer.getShardNum());
            out.writeLong(payer.getRealmNum());
            out.writeLong(payer.getAccountNum());
            out.writeLong(topicId.getShardNum());
            out.writeLong(topicId.getRealmNum());
            out.writeLong(topicId.getTopicNum());
            out.writeLong(consensusTimestamp.getEpochSecond());
            out.writeInt(consensusTimestamp.getNano());
            ++sequenceNumber;
            out.writeLong(sequenceNumber);
            out.writeObject(MessageDigest.getInstance("SHA-384").digest(message));
            out.flush();
            runningHash = MessageDigest.getInstance("SHA-384").digest(boas.toByteArray());
        }
    }

    public static class KeySerializationException extends RuntimeException {
        public KeySerializationException(String message){
            super(message);
        }
    }

    /* --- Bean --- */
    public boolean hasMemo() {
        return memo != null;
    }

    public String getMemo() {
        return hasMemo() ? memo : "";
    }

    public void setMemo(@Nullable String memo) {
        this.memo = ((null != memo) && !memo.isEmpty()) ? memo : null;
    }

    public boolean hasAdminKey() {
        return adminKey != null;
    }

    public JKey getAdminKey() {
        return hasAdminKey() ? adminKey : getDefaultJKey();
    }

    public void setAdminKey(@Nullable JKey adminKey) {
        this.adminKey = ((null != adminKey) && !adminKey.isEmpty()) ? adminKey : null;
    }

    public boolean hasSubmitKey() {
        return submitKey != null;
    }

    public JKey getSubmitKey() {
        return hasSubmitKey() ? submitKey : getDefaultJKey();
    }

    public void setSubmitKey(@Nullable JKey submitKey) {
        this.submitKey = ((null != submitKey) && !submitKey.isEmpty()) ? submitKey : null;
    }

    public long getAutoRenewDurationSeconds() {
        return autoRenewDurationSeconds;
    }

    public void setAutoRenewDurationSeconds(long autoRenewDurationSeconds) {
        this.autoRenewDurationSeconds = autoRenewDurationSeconds;
    }

    public boolean hasAutoRenewAccountId() {
        return autoRenewAccountId != null;
    }

    public EntityId getAutoRenewAccountId() {
        return hasAutoRenewAccountId() ? autoRenewAccountId : new EntityId();
    }

    public void setAutoRenewAccountId(@Nullable EntityId autoRenewAccountId) {
        this.autoRenewAccountId = ((null != autoRenewAccountId) && (0 != autoRenewAccountId.num()))
                ? autoRenewAccountId
                : null;
    }

    public boolean hasExpirationTimestamp() {
        return expirationTimestamp != null;
    }

    public RichInstant getExpirationTimestamp() {
        return hasExpirationTimestamp() ? expirationTimestamp : new RichInstant();
    }

    public void setExpirationTimestamp(@Nullable RichInstant expiry) {
        if ((null != expiry) && ((0 != expiry.getSeconds()) || (0 != expiry.getNanos()))) {
            this.expirationTimestamp = expiry;
        } else {
            this.expirationTimestamp = null;
        }
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public boolean hasRunningHash() {
        return runningHash != null;
    }

    public byte[] getRunningHash() {
        return (runningHash != null) ? runningHash : new byte[RUNNING_HASH_BYTE_ARRAY_SIZE];
    }

    public void setRunningHash(@Nullable byte[] runningHash) {
        this.runningHash = ((null != runningHash) && (0 != runningHash.length)) ? runningHash : null;
    }
}
