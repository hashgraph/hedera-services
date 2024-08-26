/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops.streams.assertions;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hedera.services.stream.proto.TransactionSidecarRecord;

/**
 * Implements an assertion about one or more {@link RecordStreamItem}'s that should appear in the
 * record stream during---or shortly after---execution of a {@link
 * HapiSpec}.
 *
 * <p>Typical implementations will be stateful, and will be constructed with their "parent" {@link HapiSpec}.
 */
public interface RecordStreamAssertion {
    /**
     * Returns true if this assertion is applicable to the given item. (There is no reason to call
     * {@link #test(RecordStreamItem)} if this method returns false.)
     *
     * @param item the item to test
     * @return true if this assertion is applicable to the given item
     */
    default boolean isApplicableTo(RecordStreamItem item) {
        return false;
    }

    /**
     * Updates the assertion's state based on a relevant {@link RecordStreamItem}, throwing an
     * {@link AssertionError} if a failure state is reached; or returning true if the assertion has
     * reached a success state.
     *
     * @param item the item to test
     * @throws AssertionError if the assertion has failed
     * @return true if the assertion has succeeded
     */
    default boolean test(RecordStreamItem item) throws AssertionError {
        return true;
    }

    /**
     * Returns true if this assertion is applicable to the given sidecar. (There is no reason to call
     * {@link #test(RecordStreamItem)} if this method returns false.)
     *
     * @param sidecar the item sidecar test
     * @return true if this assertion is applicable to the given item
     */
    default boolean isApplicableToSidecar(TransactionSidecarRecord sidecar) {
        return false;
    }

    /**
     * Updates the assertion's state based on a relevant {@link TransactionSidecarRecord}, throwing an
     * {@link AssertionError} if a failure state is reached; or returning true if the assertion has
     * reached a success state.
     *
     * @param sidecar the sidecar to test
     * @throws AssertionError if the assertion has failed
     * @return true if the assertion has succeeded
     */
    default boolean testSidecar(TransactionSidecarRecord sidecar) throws AssertionError {
        return true;
    }

    /**
     * Hint to implementers to return a string that describes the assertion.
     *
     * @return a string that describes the assertion
     */
    @Override
    String toString();
}
