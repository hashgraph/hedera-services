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
package com.hedera.node.app.throttle;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Keeps track of the amount of usage of different throttle categories (by {@code id}), and returns
 * whether the throttle has been exceeded after applying the given incremental amount.
 */
public interface ThrottleAccumulator {

    /**
     * Increments the throttle associated with functionality's {@code id} and returns whether the
     * throttle has been exceeded. If there is no throttle associated with {@code functionality},
     * then an {@link IllegalArgumentException} will be thrown. This is to prevent bugs where some
     * code accidentally specified a throttle but a corresponding throttle was never configured,
     * leading to an open-throttle situation (i.e. an un-throttled attack vector).
     *
     * @param functionality The ID of the throttle to increment and check. This must exist.
     * @return true if the throttle has been exceeded, false otherwise.
     * @throws NullPointerException if (@code functionality} is {@code null}
     * @throws IllegalArgumentException if no throttle exists for {@code functionality}
     */
    boolean shouldThrottle(@NonNull final HederaFunctionality functionality);
}
