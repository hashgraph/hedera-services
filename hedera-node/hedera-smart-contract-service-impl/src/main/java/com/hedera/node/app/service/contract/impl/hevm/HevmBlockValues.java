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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;

/**
 * @param gasLimit the effective gas limit
 * @param blockNo the block number
 * @param blockTime the block time
 */
public record HevmBlockValues(long gasLimit, long blockNo, @NonNull Timestamp blockTime) implements BlockValues {
    private static final Optional<Wei> ZERO_BASE_FEE = Optional.of(Wei.ZERO);

    /**
     * @param blockRecordInfo the block record info
     * @param gasLimit the effective gas limit
     * @return the hedera evm block values
     */
    public static HevmBlockValues from(@NonNull final BlockRecordInfo blockRecordInfo, final long gasLimit) {
        requireNonNull(blockRecordInfo);
        return new HevmBlockValues(gasLimit, blockRecordInfo.blockNo(), blockRecordInfo.blockTimestamp());
    }

    /**
     * @param gasLimit the effective gas limit
     * @param blockNo the block number
     * @param blockTime the block time
     */
    public HevmBlockValues {
        requireNonNull(blockTime);
    }

    @Override
    public long getGasLimit() {
        return gasLimit;
    }

    @Override
    public long getTimestamp() {
        return blockTime.seconds();
    }

    @Override
    public Optional<Wei> getBaseFee() {
        return ZERO_BASE_FEE;
    }

    @Override
    public Bytes getDifficultyBytes() {
        return Bytes.EMPTY;
    }

    @Override
    public long getNumber() {
        return blockNo;
    }
}
