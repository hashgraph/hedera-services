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

package com.hedera.node.app.service.contract.impl.hevm;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.ethHashFrom;

import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import javax.inject.Inject;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.frame.BlockValues;

/**
 * A {@link HederaEvmBlocks} implementation that uses the {@link HandleContext} to get
 * block information.
 */
@TransactionScope
public class HandleContextHevmBlocks implements HederaEvmBlocks {
    private final HandleContext context;

    @Inject
    public HandleContextHevmBlocks(@NonNull final HandleContext context) {
        this.context = Objects.requireNonNull(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash blockHashOf(final long blockNo) {
        final var hederaBlockHash = context.blockRecordInfo().blockHashByBlockNumber(blockNo);
        return hederaBlockHash == null ? UNAVAILABLE_BLOCK_HASH : ethHashFrom(hederaBlockHash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlockValues blockValuesOf(final long gasLimit) {
        return HevmBlockValues.from(context.blockRecordInfo(), gasLimit);
    }
}
