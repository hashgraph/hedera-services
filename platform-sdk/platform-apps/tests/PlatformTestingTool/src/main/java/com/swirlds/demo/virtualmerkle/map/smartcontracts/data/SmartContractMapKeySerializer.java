/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.virtualmerkle.map.smartcontracts.data;

import com.swirlds.merkledb.serialize.AbstractFixedSizeKeySerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This class is the serializer for {@link SmartContractMapKey}.
 */
public final class SmartContractMapKeySerializer extends AbstractFixedSizeKeySerializer<SmartContractMapKey> {

    private static final long CLASS_ID = 0x2d68463768cf4c5AL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    public SmartContractMapKeySerializer() {
        super(CLASS_ID, ClassVersion.ORIGINAL, SmartContractMapKey.getSizeInBytes(), 1, SmartContractMapKey::new);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final ByteBuffer buffer, final int dataVersion, final SmartContractMapKey keyToCompare)
            throws IOException {
        return keyToCompare.equals(buffer, dataVersion);
    }
}
