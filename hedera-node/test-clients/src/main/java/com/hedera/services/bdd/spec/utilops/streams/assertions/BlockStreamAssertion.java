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

package com.hedera.services.bdd.spec.utilops.streams.assertions;

import com.hedera.hapi.block.stream.Block;
import com.hedera.services.bdd.spec.HapiSpec;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implements an assertion about one or more {@link com.hedera.hapi.block.stream.Block}'s that should appear in the
 * block stream during---or shortly after---execution of a {@link HapiSpec}.
 *
 * <p>Typical implementations will be stateful, and will be constructed with their "parent" {@link HapiSpec}.
 */
@FunctionalInterface
public interface BlockStreamAssertion {
    /**
     * Updates the assertion's state based on a relevant {@link Block}, throwing an {@link AssertionError} if a
     * failure state is reached; or returning true if the assertion has reached a success state.
     *
     * @param block the block to test
     * @throws AssertionError if the assertion has failed
     * @return true if the assertion has succeeded
     */
    boolean test(@NonNull Block block) throws AssertionError;
}
