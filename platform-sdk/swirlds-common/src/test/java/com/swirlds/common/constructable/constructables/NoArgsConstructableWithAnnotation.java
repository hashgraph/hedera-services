// SPDX-License-Identifier: Apache-2.0
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
