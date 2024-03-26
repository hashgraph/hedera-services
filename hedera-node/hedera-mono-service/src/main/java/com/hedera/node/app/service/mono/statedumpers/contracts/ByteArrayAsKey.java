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

package com.hedera.node.app.service.mono.statedumpers.contracts;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;

/** Implements equality-of-content on a byte array so it can be used as a map key */
public record ByteArrayAsKey(@NonNull byte[] array) {

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof ByteArrayAsKey other && Arrays.equals(array, other.array);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }

    @Override
    public String toString() {
        return "ByteArrayAsKey{" + "array=" + Arrays.toString(array) + '}';
    }
}
