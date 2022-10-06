/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.logic.BlockManager;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.frame.BlockValues;

@Singleton
public class InHandleBlockMetaSource implements BlockMetaSource {
    private final BlockManager blockManager;
    private final TransactionContext txnCtx;

    @Inject
    public InHandleBlockMetaSource(
            final BlockManager blockManager, final TransactionContext txnCtx) {
        this.blockManager = blockManager;
        this.txnCtx = txnCtx;
    }

    @Override
    public Hash getBlockHash(final long blockNo) {
        return blockManager.getBlockHash(blockNo);
    }

    @Override
    public BlockValues computeBlockValues(final long gasLimit) {
        return blockManager.computeBlockValues(txnCtx.consensusTime(), gasLimit);
    }
}
