/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.execution;

import com.hedera.services.evm.contracts.execution.BlockMetaSource;
import com.hedera.services.evm.contracts.execution.HederaBlockValues;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import java.time.Instant;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.frame.BlockValues;

/**
 * A {@link BlockMetaSource} that gets its information from a particular {@link
 * MerkleNetworkContext}, which ideally will be an immutable instance from the latest signed state.
 *
 * <p>The important thing is that, unlike {@link InHandleBlockMetaSource}, here the {@code
 * computeBlockValues()} implementation has no side effects on the state of the {@link
 * com.hedera.services.state.logic.BlockManager} singleton that manages block metadata in the
 * working network context.
 */
public class StaticBlockMetaSource implements BlockMetaSource {
    private final MerkleNetworkContext networkCtx;

    public StaticBlockMetaSource(final MerkleNetworkContext networkCtx) {
        this.networkCtx = networkCtx;
    }

    public static StaticBlockMetaSource from(final MerkleNetworkContext networkCtx) {
        return new StaticBlockMetaSource(networkCtx);
    }

    @Override
    public Hash getBlockHash(final long blockNo) {
        return networkCtx.getBlockHashByNumber(blockNo);
    }

    @Override
    public BlockValues computeBlockValues(final long gasLimit) {
        final var nominalBlockNo = networkCtx.getAlignmentBlockNo();
        var nominalTimestamp = networkCtx.firstConsTimeOfCurrentBlock();
        // This is exceedingly unlikely in practice, as it requires a ContractCallLocal query to run
        // as exactly the first network interaction immediately after a genesis reset; as there's no
        // impact on consensus state, falling back to Instant.now() is acceptable
        if (nominalTimestamp == null) {
            nominalTimestamp = Instant.now();
        }
        return new HederaBlockValues(gasLimit, nominalBlockNo, nominalTimestamp);
    }
}
