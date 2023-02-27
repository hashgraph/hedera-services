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

package com.swirlds.common.merkle.exceptions;

/**
 * This exception is thrown when a child is added to a MerkleInternal node that is not of the expected type.
 */
public class IllegalChildTypeException extends IllegalArgumentException {
    public IllegalChildTypeException(int index, long classId, final String parentClassName) {
        super(String.format(
                "Invalid class ID %d(0x%08X) at index %d for parent with class %s",
                classId, classId, index, parentClassName));
    }
}
