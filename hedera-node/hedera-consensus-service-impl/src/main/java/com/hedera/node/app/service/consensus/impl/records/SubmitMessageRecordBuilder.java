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

package com.hedera.node.app.service.consensus.impl.records;

import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.RUNNING_HASH_VERSION;

import com.hedera.node.app.spi.records.UniversalRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

public class SubmitMessageRecordBuilder extends UniversalRecordBuilder<ConsensusSubmitMessageRecordBuilder>
        implements ConsensusSubmitMessageRecordBuilder {
    private long newSequenceNumber;

    @Nullable
    private byte[] newRunningHash = null;

    /**
     * {@inheritDoc}
     */
    @Override
    protected SubmitMessageRecordBuilder self() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ConsensusSubmitMessageRecordBuilder setNewTopicMetadata(
            final @NonNull byte[] topicRunningHash,
            final long sequenceNumber,
            // The mono context will (correctly) assume the latest running hash version,
            // but it probably makes more sense to provide it here in the future
            final long runningHashVersion) {
        this.newRunningHash = Objects.requireNonNull(topicRunningHash);
        this.newSequenceNumber = sequenceNumber;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public byte[] getNewTopicRunningHash() {
        throwIfMissingNewMetadata();
        return Objects.requireNonNull(newRunningHash);
    }

    @Override
    public long getNewTopicSequenceNumber() {
        throwIfMissingNewMetadata();
        return newSequenceNumber;
    }

    @Override
    public long getUsedRunningHashVersion() {
        throwIfMissingNewMetadata();
        return RUNNING_HASH_VERSION;
    }

    private void throwIfMissingNewMetadata() {
        if (newRunningHash == null) {
            throw new IllegalStateException("No new topic metadata was recorded");
        }
    }
}
