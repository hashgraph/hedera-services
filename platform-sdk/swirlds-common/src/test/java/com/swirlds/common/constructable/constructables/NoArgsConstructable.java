// SPDX-License-Identifier: Apache-2.0
package com.swirlds.common.constructable.constructables;

import com.swirlds.common.constructable.RuntimeConstructable;

public class NoArgsConstructable implements RuntimeConstructable {
    public static final long CLASS_ID = 0x508db0a39e0e8e05L;

    @Override
    public long getClassId() {
        return CLASS_ID;
    }
}
