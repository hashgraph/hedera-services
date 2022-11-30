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
package com.hedera.node.app.service.mono.sigs.metadata;

import com.google.common.base.MoreObjects;
import com.hedera.node.app.service.mono.sigs.order.KeyOrderingFailure;
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
        KNOWN_FAILURES.put(
                KeyOrderingFailure.MISSING_FILE,
                new SafeLookupResult<>(KeyOrderingFailure.MISSING_FILE));
        KNOWN_FAILURES.put(
                KeyOrderingFailure.MISSING_TOKEN,
                new SafeLookupResult<>(KeyOrderingFailure.MISSING_TOKEN));
        KNOWN_FAILURES.put(
                KeyOrderingFailure.MISSING_ACCOUNT,
                new SafeLookupResult<>(KeyOrderingFailure.MISSING_ACCOUNT));
        KNOWN_FAILURES.put(
                KeyOrderingFailure.INVALID_CONTRACT,
                new SafeLookupResult<>(KeyOrderingFailure.INVALID_CONTRACT));
        KNOWN_FAILURES.put(
                KeyOrderingFailure.IMMUTABLE_CONTRACT,
                new SafeLookupResult<>(KeyOrderingFailure.IMMUTABLE_CONTRACT));
        KNOWN_FAILURES.put(
                KeyOrderingFailure.INVALID_TOPIC,
                new SafeLookupResult<>(KeyOrderingFailure.INVALID_TOPIC));
        KNOWN_FAILURES.put(
                KeyOrderingFailure.INVALID_AUTORENEW_ACCOUNT,
                new SafeLookupResult<>(KeyOrderingFailure.INVALID_AUTORENEW_ACCOUNT));
        KNOWN_FAILURES.put(
                KeyOrderingFailure.MISSING_SCHEDULE,
                new SafeLookupResult<>(KeyOrderingFailure.MISSING_SCHEDULE));
        KNOWN_FAILURES.put(
                KeyOrderingFailure.IMMUTABLE_ACCOUNT,
                new SafeLookupResult<>(KeyOrderingFailure.IMMUTABLE_ACCOUNT));
    }

    private SafeLookupResult(KeyOrderingFailure failure) {
        this.failure = failure;
        metadata = Optional.empty();
    }

    public SafeLookupResult(T metadata) {
        this.failure = KeyOrderingFailure.NONE;
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
