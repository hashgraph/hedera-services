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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.consensus.entity.Topic;
import com.hedera.node.app.service.consensus.entity.TopicBuilder;
import com.hedera.node.app.service.token.entity.Account;
import com.hedera.node.app.spi.key.HederaKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * An implementation of {@link TopicBuilder} for building Account instances. This class is
 * <strong>not</strong> exported from the module. FUTURE: Should be moved to token-service-impl
 * module
 */
public class TopicBuilderImpl implements TopicBuilder {
    private long topicNumber;
    private HederaKey adminKey;
    private HederaKey submitKey;
    private String memo;
    private long autoRenewAccountNumber;
    private long autoRenewSecs;
    private long expiry;
    private boolean deleted;
    private long sequenceNumber;
    /**
     * Create a builder for creating {@link Account}s, using the given copy as the basis for all
     * settings that are not overridden.
     *
     * @param copyOf The instance to copy
     */
    public TopicBuilderImpl(@NonNull Topic copyOf) {
        requireNonNull(copyOf);
        this.topicNumber = copyOf.topicNumber();
        this.adminKey = copyOf.getAdminKey().orElse(null);
        this.submitKey = copyOf.getSubmitKey().orElse(null);
        this.autoRenewAccountNumber = copyOf.autoRenewAccountNumber();
        this.memo = copyOf.memo();
        this.deleted = copyOf.deleted();
        this.autoRenewSecs = copyOf.autoRenewSecs();
        this.expiry = copyOf.expiry();
        this.sequenceNumber = copyOf.sequenceNumber();
    }

    public TopicBuilderImpl() {
        /* Default constructor for creating new topics */
    }

    @NonNull
    @Override
    public TopicBuilder topicNumber(@NonNull long value) {
        this.topicNumber = requireNonNull(value);
        return this;
    }

    @NonNull
    @Override
    public TopicBuilder expiry(@NonNull long value) {
        this.expiry = requireNonNull(value);
        return this;
    }

    @NonNull
    @Override
    public TopicBuilder adminKey(@Nullable HederaKey key) {
        this.adminKey = key;
        return this;
    }

    @NonNull
    @Override
    public TopicBuilder submitKey(@Nullable HederaKey key) {
        this.submitKey = key;
        return this;
    }

    @NonNull
    @Override
    public TopicBuilder memo(@NonNull String value) {
        this.memo = requireNonNull(value);
        return this;
    }

    @NonNull
    @Override
    public TopicBuilder autoRenewAccountNumber(@NonNull long autoRenewAccountNumber) {
        this.autoRenewAccountNumber = requireNonNull(autoRenewAccountNumber);
        return this;
    }

    @NonNull
    @Override
    public TopicBuilder deleted(@NonNull boolean value) {
        this.deleted = requireNonNull(value);
        return this;
    }

    @NonNull
    @Override
    public TopicBuilder sequenceNumber(@NonNull long sequenceNumber) {
        this.sequenceNumber = requireNonNull(sequenceNumber);
        return this;
    }

    @Override
    @NonNull
    public TopicBuilder autoRenewSecs(long value) {
        this.autoRenewSecs = requireNonNull(value);
        return this;
    }

    @Override
    @NonNull
    public Topic build() {
        return new TopicImpl(
                topicNumber,
                adminKey, // null if user did not set it
                submitKey, // null if user did not set it
                memo,
                autoRenewAccountNumber,
                autoRenewSecs,
                expiry,
                deleted,
                sequenceNumber);
    }
}
