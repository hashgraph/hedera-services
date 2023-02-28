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
import edu.umd.cs.findbugs.annotations.Nullable;

/** Builds a topic using a builder pattern */
public interface TopicBuilder {
    /**
     * Override the adminKey specified on the topic
     *
     * @param key admin key
     * @return builder object
     */
    @NonNull
    TopicBuilder adminKey(@Nullable HederaKey key);

    /**
     * Override the submitKey specified on the topic
     *
     * @param key submit key
     * @return builder object
     */
    @NonNull
    TopicBuilder submitKey(@Nullable HederaKey key);

    /**
     * Override the memo specified on the topic
     *
     * @param memo topic memo
     * @return builder object
     */
    @NonNull
    TopicBuilder memo(@NonNull String memo);

    /**
     * Override the autoRenewAccountNumber specified on the topic
     *
     * @param autoRenewAccountNumber auto renew account number
     * @return builder object
     */
    @NonNull
    TopicBuilder autoRenewAccountNumber(@NonNull long autoRenewAccountNumber);

    /**
     * Override the auto-renewal seconds specified on the topic
     *
     * @param autoRenewSecs auto-renew seconds
     * @return builder object
     */
    @NonNull
    TopicBuilder autoRenewSecs(@NonNull long autoRenewSecs);

    @NonNull
    TopicBuilder topicNumber(@NonNull long value);

    /**
     * Override the expiration time specified on the topic
     *
     * @param expiry expiration time
     * @return builder object
     */
    @NonNull
    TopicBuilder expiry(@NonNull long expiry);

    /**
     * Override if the topic is deleted
     *
     * @param isDeleted if the topic is deleted
     * @return builder object
     */
    @NonNull
    TopicBuilder deleted(@NonNull boolean isDeleted);

    /**
     * Override the sequence number of the topic
     *
     * @param sequenceNumber sequence number
     * @return builder object
     */
    @NonNull
    TopicBuilder sequenceNumber(@NonNull long sequenceNumber);

    /**
     * Builds and returns an account with the state specified in the builder
     *
     * @return A non-null reference to a **new** topic. Two calls to this method return different
     *     instances.
     */
    @NonNull
    Topic build();
}
