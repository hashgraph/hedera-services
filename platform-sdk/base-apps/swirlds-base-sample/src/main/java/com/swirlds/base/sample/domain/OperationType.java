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

package com.swirlds.base.sample.domain;

import java.util.function.BiFunction;

public enum OperationType {
    ADDITION(Math::addExact),
    DEDUCTION(Math::subtractExact);

    private final BiFunction<Integer, Integer, Integer> operation;

    OperationType(BiFunction<Integer, Integer, Integer> operation) {
        this.operation = operation;
    }

    public int apply(int arg1, int arg2) {
        return this.operation.apply(arg1, arg2);
    }
}
