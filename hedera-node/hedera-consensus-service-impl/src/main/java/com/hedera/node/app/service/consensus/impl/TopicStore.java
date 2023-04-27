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

import static com.hedera.node.app.service.mono.pbj.PbjConverter.asBytes;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.consensus.TopicMetadata;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Base class for {@link ReadableTopicStore} and {@link WritableTopicStore}.
 */
public class TopicStore {
    public static TopicMetadata topicMetaFrom(final Topic topic) {
        final var maybeAutoRenewNum = topic.autoRenewAccountNumber() == 0
                ? OptionalLong.empty()
                : OptionalLong.of(topic.autoRenewAccountNumber());
        return new TopicMetadata(
                Optional.of(topic.memo()),
                topic.adminKeyOrElse(Key.DEFAULT),
                topic.submitKeyOrElse(Key.DEFAULT),
                topic.autoRenewPeriod(),
                maybeAutoRenewNum,
                Timestamp.newBuilder().seconds(topic.expiry()).build(),
                topic.sequenceNumber(),
                asBytes(topic.runningHash()),
                topic.topicNumber(),
                topic.deleted());
    }
}
