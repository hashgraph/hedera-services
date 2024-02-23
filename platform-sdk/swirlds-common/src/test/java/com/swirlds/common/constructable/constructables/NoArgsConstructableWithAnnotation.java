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

package com.swirlds.common.constructable.constructables;

import static com.swirlds.common.constructable.constructables.NoArgsConstructableWithAnnotation.CLASS_ID;

import com.swirlds.common.constructable.ConstructableClass;
import com.swirlds.common.constructable.RuntimeConstructable;

@ConstructableClass(value = CLASS_ID)
public class NoArgsConstructableWithAnnotation implements RuntimeConstructable {
    public static final long CLASS_ID = 0xab0fcb634195d777L;

    @Override
    public long getClassId() {
        return CLASS_ID;
    }
}
