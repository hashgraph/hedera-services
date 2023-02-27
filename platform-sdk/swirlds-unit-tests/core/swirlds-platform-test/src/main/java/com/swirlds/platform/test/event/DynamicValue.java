/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.event;

import java.util.Random;

/**
 * This functional interface is used to configure event generation with a non-static value of type T.
 */
public interface DynamicValue<T> {

    /**
     * Generate the next value.
     *
     * This function should not make modifications to any objects in external scopes.
     *
     * @param random
     * 		if the value is not randomized this can be ignored. Otherwise this must be the only source of
     * 		of randomness used by this method.
     * @param eventIndex
     * 		the index of the next event to be created
     * @param previousValue
     * 		the value returned by this method at the previous index. Returns null if there was no previous call.
     */
    T get(Random random, long eventIndex, T previousValue);
}
