/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.constructable.constructables.scannable;

import static com.swirlds.common.constructable.constructables.scannable.PrimitiveAndObjectConstructable.CLASS_ID;

import com.swirlds.common.constructable.ConstructableClass;
import com.swirlds.common.constructable.RuntimeConstructable;
import com.swirlds.common.constructable.constructors.PrimitiveAndObjectConstructor;

@ConstructableClass(value = CLASS_ID, constructorType = PrimitiveAndObjectConstructor.class)
public class PrimitiveAndObjectConstructable implements RuntimeConstructable {
    public static final long CLASS_ID = 0xab845f40cd4bd2bdL;

    private final long first;
    private final Integer second;

    public PrimitiveAndObjectConstructable(final long first, final Integer second) {
        this.first = first;
        this.second = second;
    }

    public long getFirst() {
        return first;
    }

    public Integer getSecond() {
        return second;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }
}
