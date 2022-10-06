/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.models;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.RichInstant;
import java.time.Instant;
import javax.annotation.Nullable;

/**
 * Represents the model of a {@link com.hedera.services.state.merkle.MerkleTopic}.
 *
 * @author Yoan Sredkov
 */
public final class Topic {
    private final Id id;
    private String memo;
    @Nullable private JKey adminKey;
    @Nullable private JKey submitKey;
    @Nullable private Id autoRenewAccountId;
    private long autoRenewDurationSeconds;
    private boolean deleted;
    private boolean isNew;
    private RichInstant expirationTimestamp;

    private long sequenceNumber;

    public Topic(final Id id) {
        this.id = id;
    }

    /**
     * Creates a new {@link Topic} from the given body. Note: The created model is not added to
     * state, and must be explicitly persisted via {@link
     * com.hedera.services.store.TopicStore#persistNew(Topic)}
     *
     * @param id - the id generated in the transition logic
     * @param submitKey - the key which permits submitting messages
     * @param adminKey - the adminKey of the topic
     * @param autoRenewAccount - the account which pays for the automatic renewal of the topic
     * @param memo - memo of the topic
     * @param autoRenewPeriod - the period of automatic renewal
     * @param expirationTime - expiration time of the topic, or when {@link
     *     com.hedera.services.txns.consensus.SubmitMessageTransitionLogic} will start failing.
     * @return - the new topic
     */
    public static Topic fromGrpcTopicCreate(
            final Id id,
            @Nullable final JKey submitKey,
            @Nullable final JKey adminKey,
            @Nullable final Account autoRenewAccount,
            final String memo,
            final long autoRenewPeriod,
            final Instant expirationTime) {
        final var topic = new Topic(id);

        topic.setMemo(memo);
        topic.setDeleted(false);
        topic.setAutoRenewDurationSeconds(autoRenewPeriod);
        topic.setAutoRenewAccountId(autoRenewAccount != null ? autoRenewAccount.getId() : null);
        topic.setSubmitKey(submitKey);
        topic.setAdminKey(adminKey);
        topic.setExpirationTimestamp(RichInstant.fromJava(expirationTime));
        topic.setNew(true);
        return topic;
    }

    public Id getId() {
        return id;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(final String memo) {
        this.memo = memo;
    }

    @Nullable
    public JKey getAdminKey() {
        return adminKey;
    }

    public void setAdminKey(@Nullable final JKey adminKey) {
        this.adminKey = adminKey;
    }

    @Nullable
    public JKey getSubmitKey() {
        return submitKey;
    }

    public void setSubmitKey(@Nullable final JKey submitKey) {
        this.submitKey = submitKey;
    }

    @Nullable
    public Id getAutoRenewAccountId() {
        return autoRenewAccountId;
    }

    public void setAutoRenewAccountId(@Nullable final Id autoRenewAccountId) {
        this.autoRenewAccountId = autoRenewAccountId;
    }

    public long getAutoRenewDurationSeconds() {
        return autoRenewDurationSeconds;
    }

    public void setAutoRenewDurationSeconds(final long autoRenewDurationSeconds) {
        this.autoRenewDurationSeconds = autoRenewDurationSeconds;
    }

    public RichInstant getExpirationTimestamp() {
        return expirationTimestamp;
    }

    public void setExpirationTimestamp(final RichInstant expirationTimestamp) {
        this.expirationTimestamp = expirationTimestamp;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(final boolean deleted) {
        this.deleted = deleted;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(final long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(final boolean aNew) {
        isNew = aNew;
    }
}
