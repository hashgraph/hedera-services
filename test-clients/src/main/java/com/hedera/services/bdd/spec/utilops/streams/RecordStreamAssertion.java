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
package com.hedera.services.bdd.spec.utilops.streams;

import com.hedera.services.bdd.spec.utilops.streams.assertions.CryptoCreateAssertion;
import com.hedera.services.stream.proto.RecordStreamItem;

/**
 * Implements an assertion about one or more {@link RecordStreamItem}'s that should appear in the
 * record stream during---or shortly after---execution of a {@link
 * com.hedera.services.bdd.spec.HapiSpec}.
 *
 * <p>Typical implementations will be stateful, and will be constructed with their "parent" {@link
 * com.hedera.services.bdd.spec.HapiSpec}. (See {@link CryptoCreateAssertion} for a minimal
 * assertion that the record stream includes a {@link RecordStreamItem} for a particular account's
 * creation.) A more complex assertion might validate that an account was not only created, but also
 * expired and was renewed with the correct new expiry.
 */
public interface RecordStreamAssertion {
    /**
     * Returns true if this assertion is applicable to the given item. (There is no reason to call
     * {@link #updateAndTest(RecordStreamItem)} if this method returns false.)
     *
     * @param item the item to test
     * @return true if this assertion is applicable to the given item
     */
    boolean isApplicableTo(RecordStreamItem item);

    /**
     * Updates the assertion's state based on a relevant {@link RecordStreamItem}, throwing an
     * {@link AssertionError} if a failure state is reached; or returning true if the assertion has
     * reached a success state.
     *
     * @param item the item to test
     * @throws AssertionError if the assertion has failed
     * @return true if the assertion has succeeded
     */
    boolean updateAndTest(RecordStreamItem item) throws AssertionError;

    /**
     * Hint to implementers to return a string that describes the assertion.
     *
     * @return a string that describes the assertion
     */
    @Override
    String toString();
}
