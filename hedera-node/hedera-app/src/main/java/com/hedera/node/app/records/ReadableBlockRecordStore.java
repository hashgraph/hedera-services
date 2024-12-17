/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.records;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.node.app.records.schemas.V0490BlockRecordSchema;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

public class ReadableBlockRecordStore {
    /** The underlying data storage class that holds the block info data. */
    private final ReadableSingletonState<BlockInfo> blockInfo;

    public ReadableBlockRecordStore(@NonNull final ReadableStates states) {
        this.blockInfo = requireNonNull(states.getSingleton(V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY));
    }

    /**
     * Returns information about the currently-ongoing and latest completed record blocks
     */
    @NonNull
    public BlockInfo getLastBlockInfo() {
        return blockInfo.get();
    }
}
