// SPDX-License-Identifier: Apache-2.0
package com.swirlds.common.constructable.constructables.scannable;

import static com.swirlds.common.constructable.constructables.scannable.ConstructableRecord.CLASS_ID;

import com.swirlds.common.constructable.ConstructableClass;
import com.swirlds.common.constructable.RuntimeConstructable;
import com.swirlds.common.constructable.constructors.RecordConstructor;

@ConstructableClass(value = CLASS_ID, constructorType = RecordConstructor.class)
public record ConstructableRecord(String string) implements RuntimeConstructable {
    public static final long CLASS_ID = 0x1ffe2ed217d39a8L;

    @Override
    public long getClassId() {
        return CLASS_ID;
    }
}
