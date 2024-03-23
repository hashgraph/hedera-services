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

package com.hedera.node.app.statedumpers.topics;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbjKey;

import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

record BBMTopic(
        long number,
        @NonNull String memo,
        @NonNull RichInstant expirationTimestamp,
        boolean deleted,
        @NonNull JKey adminKey,
        @NonNull JKey submitKey,
        @NonNull byte[] runningHash,
        long sequenceNumber,
        long autoRenewDurationSeconds,
        @Nullable EntityId autoRenewAccountId) {

    static BBMTopic fromMod(@NonNull final com.hedera.hapi.node.state.consensus.Topic topic) {
        return new BBMTopic(
                topic.topicId().topicNum(),
                topic.memo(),
                RichInstant.fromJava(Instant.ofEpochSecond(topic.expirationSecond())),
                topic.deleted(),
                (JKey) fromPbjKey(topic.adminKey()).orElse(null),
                (JKey) fromPbjKey(topic.submitKey()).orElse(null),
                topic.runningHash().toByteArray(),
                topic.sequenceNumber(),
                topic.autoRenewPeriod(),
                EntityId.fromNum(topic.autoRenewAccountId().accountNum()));
    }
}
