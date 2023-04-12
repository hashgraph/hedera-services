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

package com.hedera.node.app.service.mono.utils.replay;

import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.pbj.runtime.io.buffer.Bytes;

public class PbjLeafConverters {
    private PbjLeafConverters() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static Topic leafFromMerkle(final MerkleTopic topic) {
        final var builder = Topic.newBuilder()
                .topicNumber(topic.getKey().longValue())
                .sequenceNumber(topic.getSequenceNumber())
                .expiry(topic.getExpirationTimestamp().getSeconds())
                .autoRenewPeriod(topic.getAutoRenewDurationSeconds())
                .deleted(topic.isDeleted())
                .runningHash(Bytes.wrap(topic.getRunningHash()));
        if (topic.hasMemo()) {
            builder.memo(topic.getMemo());
        }
        if (topic.hasAdminKey()) {
            builder.adminKey(PbjConverter.toPbj(topic.getAdminKey()));
        }
        if (topic.hasSubmitKey()) {
            builder.submitKey(PbjConverter.toPbj(topic.getSubmitKey()));
        }
        return builder.build();
    }
}
