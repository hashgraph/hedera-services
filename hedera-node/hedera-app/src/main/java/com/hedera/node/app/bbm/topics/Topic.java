/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.bbm.topics;

import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

record Topic(
        int number,
        @NonNull String memo,
        @NonNull RichInstant expirationTimestamp,
        boolean deleted,
        @NonNull JKey adminKey,
        @NonNull JKey submitKey,
        @NonNull byte[] runningHash,
        long sequenceNumber,
        long autoRenewDurationSeconds,
        @Nullable EntityId autoRenewAccountId) {

    Topic(@NonNull final MerkleTopic topic) {
        this(
                topic.getKey().intValue(),
                topic.getMemo(),
                topic.getExpirationTimestamp(),
                topic.isDeleted(),
                topic.getAdminKey(),
                topic.getSubmitKey(),
                null != topic.getRunningHash() ? topic.getRunningHash() : EMPTY_BYTES,
                topic.getSequenceNumber(),
                topic.getAutoRenewDurationSeconds(),
                topic.getAutoRenewAccountId());
        Objects.requireNonNull(memo, "memo");
        Objects.requireNonNull(adminKey, "adminKey");
        Objects.requireNonNull(submitKey, "submitKey");
        Objects.requireNonNull(runningHash, "runningHash");
    }

    static final byte[] EMPTY_BYTES = new byte[0];
}
