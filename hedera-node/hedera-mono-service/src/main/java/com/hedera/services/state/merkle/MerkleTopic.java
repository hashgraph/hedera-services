/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.asIdLiteral;
import static com.hedera.services.utils.EntityIdUtils.asLiteralString;
import static com.swirlds.common.utility.CommonUtils.hex;

import com.google.common.base.MoreObjects;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.state.serdes.TopicSerde;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A consensus service topic's memo, adminKey, submitKey, autoRenew duration and account,
 * sequenceNumber and runningHash expiration and deleted status.
 *
 * <p>Optimize for small memory and serialized footprint.
 *
 * <p>FastCopyable implemented, but there is no internally managed copy-on-write functionality,
 * which is impractical given JKey/JAccountID/JTimestamp mutability.
 *
 * <p>Accessor interfaces follow the protobuf-paradigm. hasXyz() and getXyz() returning the default
 * (non-null) value if !hasXyz().
 *
 * <p>Caller is expected to:
 *
 * <ul>
 *   <li>not modify Topic (or its mutable child objects) once it's been used in an FCMap
 *   <li>to edit a Topic in an FCMap - use copy constructor, <b>replace</b> fields requiring
 *       modification and then replace the Topic in the map.
 * </ul>
 */
public final class MerkleTopic extends PartialMerkleLeaf implements Keyed<EntityNum>, MerkleLeaf {
    public static final int RUNNING_HASH_BYTE_ARRAY_SIZE = 48;
    public static final long RUNNING_HASH_VERSION = 3L;

    static final int RELEASE_0180_VERSION = 2;

    static final int CURRENT_VERSION = RELEASE_0180_VERSION;

    static final long RUNTIME_CONSTRUCTABLE_ID = 0xcfc535576b57baf0L;

    static TopicSerde topicSerde = new TopicSerde();

    private String memo;
    private JKey adminKey;
    private JKey submitKey;
    private long autoRenewDurationSeconds;
    private EntityId autoRenewAccountId;
    private RichInstant expirationTimestamp;
    private boolean deleted;
    private int number;

    // Before the first message is submitted to this topic, its sequenceNumber is 0 and runningHash
    // is 48 bytes of '\0'
    private long sequenceNumber;
    private byte[] runningHash;

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("number", number + " <-> " + asIdLiteral(number))
                .add("memo", memo)
                .add(
                        "expiry",
                        String.format(
                                "%d.%d",
                                expirationTimestamp.getSeconds(), expirationTimestamp.getNanos()))
                .add("deleted", deleted)
                .add("adminKey", MiscUtils.describe(adminKey))
                .add("submitKey", MiscUtils.describe(submitKey))
                .add("runningHash", (runningHash != null) ? hex(runningHash) : "<N/A>")
                .add("sequenceNumber", sequenceNumber)
                .add("autoRenewSecs", autoRenewDurationSeconds)
                .add("autoRenewAccount", asLiteralString(asAccount(autoRenewAccountId)))
                .toString();
    }

    public MerkleTopic() {
        /* RuntimeConstructable */
    }

    /**
     * Create a new topic.
     *
     * @param memo short non-unique memo
     * @param adminKey the key to perform updateTopic/deleteTopic functions
     * @param submitKey the key (if any) to be able to submitMessage
     * @param autoRenewDurationSeconds the auto-renew duration in seconds
     * @param autoRenewAccountId the account id that pays for auto-renew
     * @param expirationTimestamp when submitMessage will start failing
     */
    public MerkleTopic(
            final @Nullable String memo,
            final @Nullable JKey adminKey,
            final @Nullable JKey submitKey,
            final long autoRenewDurationSeconds,
            final @Nullable EntityId autoRenewAccountId,
            final @Nullable RichInstant expirationTimestamp) {
        setMemo(memo);
        setAdminKey(adminKey);
        setSubmitKey(submitKey);
        setAutoRenewDurationSeconds(autoRenewDurationSeconds);
        setAutoRenewAccountId(autoRenewAccountId);
        setExpirationTimestamp(expirationTimestamp);
    }

    public MerkleTopic(final MerkleTopic other) {
        this.memo = other.memo;
        this.adminKey = other.hasAdminKey() ? other.getAdminKey() : null;
        this.submitKey = other.hasSubmitKey() ? other.getSubmitKey() : null;
        this.autoRenewDurationSeconds = other.autoRenewDurationSeconds;
        this.autoRenewAccountId = other.hasAutoRenewAccountId() ? other.autoRenewAccountId : null;
        this.expirationTimestamp =
                other.hasExpirationTimestamp() ? other.expirationTimestamp : null;
        this.deleted = other.deleted;
        this.number = other.number;

        this.sequenceNumber = other.sequenceNumber;
        this.runningHash =
                (null != other.runningHash)
                        ? Arrays.copyOf(other.runningHash, other.runningHash.length)
                        : null;
    }

    /* --- MerkleLeaf --- */
    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        topicSerde.deserialize(in, this);
        // Added in 0.18
        number = in.readInt();
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        topicSerde.serialize(this, out);
        out.writeInt(number);
    }

    /* --- FastCopyable --- */
    @Override
    public MerkleTopic copy() {
        setImmutable(true);
        return new MerkleTopic(this);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if ((null == o) || !MerkleTopic.class.equals(o.getClass())) {
            return false;
        }
        final var that = (MerkleTopic) o;
        return Objects.equals(this.memo, that.memo)
                && this.number == that.number
                && equalUpToDecodability(this.getAdminKey(), that.getAdminKey())
                && equalUpToDecodability(this.getSubmitKey(), that.getSubmitKey())
                && Objects.equals(this.autoRenewDurationSeconds, that.autoRenewDurationSeconds)
                && Objects.equals(this.autoRenewAccountId, that.autoRenewAccountId)
                && Objects.equals(this.expirationTimestamp, that.expirationTimestamp)
                && (this.deleted == that.deleted)
                && (this.sequenceNumber == that.sequenceNumber)
                && Arrays.equals(this.runningHash, that.runningHash);
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
                runningHash,
                number);
    }

    /* --- Helpers --- */

    private JKey getDefaultJKey() {
        return new JKeyList(new ArrayList<>());
    }

    /**
     * Increment the sequence number if this is not the initial transaction on the topic (the
     * create), and update the running hash of the Transactions on this topic (submitted messages
     * and modifications of the topic).
     *
     * @param payer the account id to pay for the transaction
     * @param message the message submitted to the topic
     * @param topicId the topic id to receive the message
     * @param consensusTimestamp the consensus timestamp
     * @throws IOException when any component fails to write to a temporary stream for computing the
     *     running hash
     */
    public void updateRunningHashAndSequenceNumber(
            final AccountID payer,
            @Nullable byte[] message,
            @Nullable TopicID topicId,
            @Nullable Instant consensusTimestamp)
            throws IOException {
        throwIfImmutable(
                "Cannot change this topic's running hash or sequence number if it's immutable.");
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
            out.writeObject(CommonUtils.noThrowSha384HashOf(message));
            out.flush();
            runningHash = CommonUtils.noThrowSha384HashOf(boas.toByteArray());
        }
    }

    @Override
    public EntityNum getKey() {
        return new EntityNum(number);
    }

    @Override
    public void setKey(EntityNum phi) {
        number = phi.intValue();
    }

    /* --- Bean --- */
    public boolean hasMemo() {
        return memo != null;
    }

    public String getMemo() {
        return hasMemo() ? memo : "";
    }

    public void setMemo(final @Nullable String memo) {
        throwIfImmutable("Cannot change this topic's memo if it's immutable.");
        this.memo = ((null != memo) && !memo.isEmpty()) ? memo : null;
    }

    @Nullable
    public String getNullableMemo() {
        return memo;
    }

    public boolean hasAdminKey() {
        return adminKey != null;
    }

    public JKey getAdminKey() {
        return hasAdminKey() ? adminKey : getDefaultJKey();
    }

    @Nullable
    public JKey getNullableAdminKey() {
        return adminKey;
    }

    public void setAdminKey(final @Nullable JKey adminKey) {
        throwIfImmutable("Cannot change this topic's admin key if it's immutable.");
        this.adminKey = ((null != adminKey) && !adminKey.isEmpty()) ? adminKey : null;
    }

    public boolean hasSubmitKey() {
        return submitKey != null;
    }

    public JKey getSubmitKey() {
        return hasSubmitKey() ? submitKey : getDefaultJKey();
    }

    public void setSubmitKey(final @Nullable JKey submitKey) {
        throwIfImmutable("Cannot change this topic's memo if it's immutable.");
        this.submitKey = ((null != submitKey) && !submitKey.isEmpty()) ? submitKey : null;
    }

    @Nullable
    public JKey getNullableSubmitKey() {
        return submitKey;
    }

    public long getAutoRenewDurationSeconds() {
        return autoRenewDurationSeconds;
    }

    public void setAutoRenewDurationSeconds(final long autoRenewDurationSeconds) {
        throwIfImmutable(
                "Cannot change this topic's auto renewal duration seconds if it's immutable.");
        this.autoRenewDurationSeconds = autoRenewDurationSeconds;
    }

    public boolean hasAutoRenewAccountId() {
        return autoRenewAccountId != null;
    }

    public EntityId getAutoRenewAccountId() {
        return hasAutoRenewAccountId() ? autoRenewAccountId : new EntityId();
    }

    @Nullable
    public EntityId getNullableAutoRenewAccountId() {
        return autoRenewAccountId;
    }

    public void setAutoRenewAccountId(final @Nullable EntityId autoRenewAccountId) {
        throwIfImmutable("Cannot change this topic's auto renewal account if it's immutable.");
        this.autoRenewAccountId =
                ((null != autoRenewAccountId) && (0 != autoRenewAccountId.num()))
                        ? autoRenewAccountId
                        : null;
    }

    public boolean hasExpirationTimestamp() {
        return expirationTimestamp != null;
    }

    public RichInstant getExpirationTimestamp() {
        return hasExpirationTimestamp() ? expirationTimestamp : new RichInstant();
    }

    @Nullable
    public RichInstant getNullableExpirationTimestamp() {
        return expirationTimestamp;
    }

    public void setExpirationTimestamp(final @Nullable RichInstant expiry) {
        throwIfImmutable("Cannot change this topic's expiration timestamp if it's immutable.");
        if ((null != expiry) && ((0 != expiry.getSeconds()) || (0 != expiry.getNanos()))) {
            this.expirationTimestamp = expiry;
        } else {
            this.expirationTimestamp = null;
        }
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(final boolean deleted) {
        throwIfImmutable("Cannot change this topic's status to be deleted if it's immutable.");
        this.deleted = deleted;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(final long sequenceNumber) {
        throwIfImmutable("Cannot change this topic's sequence number if it's immutable.");
        this.sequenceNumber = sequenceNumber;
    }

    public boolean hasRunningHash() {
        return runningHash != null;
    }

    public byte[] getRunningHash() {
        return (runningHash != null) ? runningHash : new byte[RUNNING_HASH_BYTE_ARRAY_SIZE];
    }

    @Nullable
    public byte[] getNullableRunningHash() {
        return runningHash;
    }

    public void setRunningHash(final @Nullable byte[] runningHash) {
        throwIfImmutable("Cannot change this topic's running hash if it's immutable.");
        this.runningHash =
                ((null != runningHash) && (0 != runningHash.length)) ? runningHash : null;
    }
}
