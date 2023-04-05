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

public class IllegalChildCountException extends IllegalArgumentException {
    public IllegalChildCountException(
            long classId, int version, int minimumChildCount, int maximumChildCount, int givenChildCount) {
        super(String.format(
                "Node with class ID %d(0x%08X) at version %d requires at least %d children and no "
                        + "more than %d children, but %d children were provided.",
                classId, classId, version, minimumChildCount, maximumChildCount, givenChildCount));
    }
}
