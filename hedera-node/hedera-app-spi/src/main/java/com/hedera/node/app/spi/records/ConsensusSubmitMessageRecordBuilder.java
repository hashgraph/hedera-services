/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.records;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code RecordBuilder} specialization for tracking the side-effects of a
 * {@code ConsensusSubmitMessage} transaction.
 */
public interface ConsensusSubmitMessageRecordBuilder extends RecordBuilder<ConsensusSubmitMessageRecordBuilder> {
    /**
     * Tracks the new topic metadata for the topic receiving the submitted message
     * in the associated transaction.
     *
     * @param topicRunningHash the new running hash of the topic
     * @param sequenceNumber the new sequence number of the topic
     * @param runningHashVersion the running hash version used to compute the new running hash
     * @return this builder
     */
    @NonNull
    ConsensusSubmitMessageRecordBuilder setNewTopicMetadata(
            @NonNull byte[] topicRunningHash, long sequenceNumber, long runningHashVersion);
}
