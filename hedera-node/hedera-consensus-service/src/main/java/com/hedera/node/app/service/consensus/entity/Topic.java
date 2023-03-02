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

package com.hedera.node.app.service.consensus.entity;

import com.hedera.node.app.spi.key.HederaKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;

/** An entity represents a Consensus Topic. */
public interface Topic {
    /**
     * Topic's number
     *
     * @return topic number
     */
    long topicNumber();

    /**
     * The admin key of the topic. Without this key, topics can't be updated or deleted.
     * @return admin key
     */
    Optional<HederaKey> getAdminKey();

    /**
     * The submit key of the topic. Without this key, messages can't be submitted to the topic.
     * @return submit key
     */
    Optional<HederaKey> getSubmitKey();

    /**
     * The memo of the topic
     * @return memo
     */
    String memo();

    /**
     * The auto-renew account number for the topic
     * @return auto renew account number
     */
    long autoRenewAccountNumber();

    /**
     * The auto-renew period of the topic in seconds
     * @return auto renew period
     */
    long autoRenewSecs();

    /**
     * The expiration time of the topic in seconds
     * @return expiration time
     */
    long expiry();

    /**
     * If the topic is deleted
     * @return true if the topic is deleted, false otherwise
     */
    boolean deleted();

    /**
     * The sequence number of the last message that was submitted to this topic.
     * Before the first message is submitted to this topic, its sequenceNumber is 0 and runningHash is 48 bytes of '\0'.
     * @return sequence number
     */
    long sequenceNumber();

    /**
     * The running hash of the last message that was submitted to this topic.
     * Before the first message is submitted to this topic, its sequenceNumber is 0 and runningHash is 48 bytes of '\0'.
     * @return running hash
     */
    byte[] runningHash();

    /**
     * Creates an AccountBuilder that clones all state in this instance, allowing the user to
     * override only the specific state that they choose to override.
     *
     * @return A non-null builder pre-initialized with all state in this instance.
     */
    @NonNull
    TopicBuilder copy();
}
