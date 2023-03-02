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

package com.hedera.node.app.service.consensus.impl;

import com.hedera.node.app.service.consensus.entity.Topic;
import com.hedera.node.app.service.consensus.impl.entity.TopicImpl;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Base class for {@link ReadableTopicStore} and {@link WritableTopicStore}.
 */
public class TopicStore {
    protected TopicMetadata topicMetaFrom(final MerkleTopic topic) {
        return new TopicMetadata(
                Optional.ofNullable(topic.getMemo()),
                Optional.ofNullable(topic.getAdminKey()),
                Optional.ofNullable(topic.getSubmitKey()),
                topic.getAutoRenewDurationSeconds(),
                topic.getAutoRenewAccountId().num() == 0
                        ? Optional.empty()
                        : Optional.of(topic.getAutoRenewAccountId().num()),
                topic.getExpirationTimestamp().toGrpc(),
                topic.getSequenceNumber(),
                topic.getRunningHash(),
                topic.getKey().longValue(),
                topic.isDeleted());
    }

    /**
     * Create a {@link Topic} from a {@link MerkleTopic}
     * @param topic the {@link MerkleTopic}
     * @return the {@link Topic}
     */
    protected Topic topicFrom(final MerkleTopic topic) {
        return new TopicImpl(
                topic.getKey().longValue(),
                topic.getAdminKey(),
                topic.getSubmitKey(),
                topic.getMemo(),
                topic.getAutoRenewAccountId().num(),
                topic.getAutoRenewDurationSeconds(),
                topic.getExpirationTimestamp().getSeconds(),
                topic.isDeleted(),
                topic.getSequenceNumber(),
                topic.getRunningHash());
    }

    // TODO : Remove use of TopicMetadata and change to use Topic instead
    /**
     * Topic metadata
     * @param memo topic's memo
     * @param adminKey topic's admin key
     * @param submitKey topic's submit key
     * @param autoRenewDurationSeconds topic's auto-renew duration in seconds
     * @param autoRenewAccountId topic's auto-renew account id
     * @param expirationTimestamp topic's expiration timestamp
     * @param sequenceNumber topic's sequence number
     * @param runningHash topic's running hash
     * @param key topic's key
     * @param isDeleted topic's deleted flag
     */
    public record TopicMetadata(
            Optional<String> memo,
            Optional<HederaKey> adminKey,
            Optional<HederaKey> submitKey,
            long autoRenewDurationSeconds,
            Optional<Long> autoRenewAccountId,
            Timestamp expirationTimestamp,
            long sequenceNumber,
            byte[] runningHash,
            long key,
            boolean isDeleted) {
        // Overriding equals, hashCode and toString to consider array's content
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TopicMetadata that = (TopicMetadata) o;
            return autoRenewDurationSeconds == that.autoRenewDurationSeconds
                    && sequenceNumber == that.sequenceNumber
                    && key == that.key
                    && isDeleted == that.isDeleted
                    && Objects.equals(memo, that.memo)
                    && Objects.equals(adminKey, that.adminKey)
                    && Objects.equals(submitKey, that.submitKey)
                    && Objects.equals(autoRenewAccountId, that.autoRenewAccountId)
                    && Objects.equals(expirationTimestamp, that.expirationTimestamp)
                    && Arrays.equals(runningHash, that.runningHash);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(
                    memo,
                    adminKey,
                    submitKey,
                    autoRenewDurationSeconds,
                    autoRenewAccountId,
                    expirationTimestamp,
                    sequenceNumber,
                    key,
                    isDeleted);
            result = 31 * result + Arrays.hashCode(runningHash);
            return result;
        }

        @Override
        public String toString() {
            return "TopicMetadata{" + "memo="
                    + memo + ", adminKey="
                    + adminKey + ", submitKey="
                    + submitKey + ", autoRenewDurationSeconds="
                    + autoRenewDurationSeconds + ", autoRenewAccountId="
                    + autoRenewAccountId + ", expirationTimestamp="
                    + expirationTimestamp + ", sequenceNumber="
                    + sequenceNumber + ", runningHash="
                    + Arrays.toString(runningHash) + ", key="
                    + key + ", isDeleted="
                    + isDeleted + '}';
        }
    }
}
