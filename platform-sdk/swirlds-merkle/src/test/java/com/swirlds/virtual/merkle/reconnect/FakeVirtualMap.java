/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.virtual.merkle.reconnect;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.impl.PartialBinaryMerkleInternal;
import com.swirlds.virtualmap.VirtualMap;

/**
 * Imitates a virtual map. Useful for spoofing the virtual map tree structure during a reconnect test.
 */
public class FakeVirtualMap extends PartialBinaryMerkleInternal implements MerkleInternal {
    @Override
    public long getClassId() {
        return VirtualMap.CLASS_ID;
    }

    @Override
    public int getVersion() {
        return VirtualMap.ClassVersion.ORIGINAL;
    }

    @Override
    public FakeVirtualMap copy() {
        throw new UnsupportedOperationException();
    }
}
