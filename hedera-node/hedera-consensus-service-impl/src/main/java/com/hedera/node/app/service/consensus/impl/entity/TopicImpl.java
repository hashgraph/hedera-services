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

package com.hedera.node.app.service.consensus.impl.entity;

import com.hedera.node.app.service.consensus.entity.Topic;
import com.hedera.node.app.service.consensus.entity.TopicBuilder;
import com.hedera.node.app.spi.key.HederaKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Optional;

/** An implementation of {@link Topic}. FUTURE: Should be moved to token-service-impl module */
public record TopicImpl(
        long topicNumber,
        @Nullable HederaKey adminKey,
        @Nullable HederaKey submitKey,
        String memo,
        long autoRenewAccountNumber,
        long autoRenewSecs,
        long expiry,
        boolean deleted,
        long sequenceNumber)
        implements Topic {

    @Override
    public long shardNumber() {
        // FUTURE: Need to get this from config
        return 0;
    }

    @Override
    public long realmNumber() {
        // FUTURE: Need to get this from config
        return 0;
    }

    @Override
    public Optional<HederaKey> getAdminKey() {
        return Optional.ofNullable(adminKey);
    }

    @Override
    public Optional<HederaKey> getSubmitKey() {
        return Optional.ofNullable(submitKey);
    }

    @Override
    @NonNull
    public TopicBuilder copy() {
        return new TopicBuilderImpl(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TopicImpl topic = (TopicImpl) o;
        return autoRenewAccountNumber == topic.autoRenewAccountNumber
                && autoRenewSecs == topic.autoRenewSecs
                && expiry == topic.expiry
                && deleted == topic.deleted
                && sequenceNumber == topic.sequenceNumber
                && Objects.equals(adminKey, topic.adminKey)
                && Objects.equals(submitKey, topic.submitKey)
                && Objects.equals(memo, topic.memo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                adminKey, submitKey, memo, autoRenewAccountNumber, autoRenewSecs, expiry, deleted, sequenceNumber);
    }

    @Override
    public String toString() {
        return "TopicImpl{" + "adminKey="
                + adminKey + ", submitKey="
                + submitKey + ", memo='"
                + memo + '\'' + ", autoRenewAccountNumber="
                + autoRenewAccountNumber + ", autoRenewSecs="
                + autoRenewSecs + ", expiry="
                + expiry + ", deleted="
                + deleted + ", sequenceNumber="
                + sequenceNumber + '}';
    }
}
