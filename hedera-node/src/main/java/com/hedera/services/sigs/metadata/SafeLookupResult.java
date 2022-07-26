/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sigs.metadata;

import static com.hedera.services.sigs.order.KeyOrderingFailure.IMMUTABLE_ACCOUNT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.IMMUTABLE_CONTRACT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_CONTRACT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_TOPIC;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_ACCOUNT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_FILE;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_SCHEDULE;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_TOKEN;
import static com.hedera.services.sigs.order.KeyOrderingFailure.NONE;

import com.google.common.base.MoreObjects;
import com.hedera.services.sigs.order.KeyOrderingFailure;
import java.util.EnumMap;
import java.util.Optional;

/**
 * Defines a type able to look up metadata associated to the signing activities of any Hedera entity
 * (account, smart contract, or file).
 */
public class SafeLookupResult<T> {
    private final Optional<T> metadata;
    private final KeyOrderingFailure failure;

    private static final EnumMap<KeyOrderingFailure, SafeLookupResult<?>> KNOWN_FAILURES =
            new EnumMap<>(KeyOrderingFailure.class);

    static {
        KNOWN_FAILURES.put(MISSING_FILE, new SafeLookupResult<>(MISSING_FILE));
        KNOWN_FAILURES.put(MISSING_TOKEN, new SafeLookupResult<>(MISSING_TOKEN));
        KNOWN_FAILURES.put(MISSING_ACCOUNT, new SafeLookupResult<>(MISSING_ACCOUNT));
        KNOWN_FAILURES.put(INVALID_CONTRACT, new SafeLookupResult<>(INVALID_CONTRACT));
        KNOWN_FAILURES.put(IMMUTABLE_CONTRACT, new SafeLookupResult<>(IMMUTABLE_CONTRACT));
        KNOWN_FAILURES.put(INVALID_TOPIC, new SafeLookupResult<>(INVALID_TOPIC));
        KNOWN_FAILURES.put(
                INVALID_AUTORENEW_ACCOUNT, new SafeLookupResult<>(INVALID_AUTORENEW_ACCOUNT));
        KNOWN_FAILURES.put(MISSING_SCHEDULE, new SafeLookupResult<>(MISSING_SCHEDULE));
        KNOWN_FAILURES.put(IMMUTABLE_ACCOUNT, new SafeLookupResult<>(IMMUTABLE_ACCOUNT));
    }

    private SafeLookupResult(KeyOrderingFailure failure) {
        this.failure = failure;
        metadata = Optional.empty();
    }

    public SafeLookupResult(T metadata) {
        this.failure = NONE;
        this.metadata = Optional.of(metadata);
    }

    @SuppressWarnings("unchecked")
    public static <R> SafeLookupResult<R> failure(KeyOrderingFailure type) {
        return (SafeLookupResult<R>) KNOWN_FAILURES.get(type);
    }

    public boolean succeeded() {
        return metadata.isPresent();
    }

    public KeyOrderingFailure failureIfAny() {
        return failure;
    }

    public T metadata() {
        return metadata.get();
    }

    @Override
    public String toString() {
        final var helper =
                MoreObjects.toStringHelper(SafeLookupResult.class).add("failure", failure);
        metadata.ifPresent(meta -> helper.add("metadata", meta));
        return helper.toString();
    }
}
