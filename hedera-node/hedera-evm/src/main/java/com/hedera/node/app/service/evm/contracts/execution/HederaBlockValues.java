/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.evm.contracts.execution;

import java.time.Instant;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;

/** Hedera adapted {@link BlockValues} */
public class HederaBlockValues implements BlockValues {
    protected final long gasLimit;
    protected final long blockNo;
    protected final Instant consTimestamp;

    public HederaBlockValues(final long gasLimit, final long blockNo, final Instant consTimestamp) {
        this.gasLimit = gasLimit;
        this.blockNo = blockNo;
        this.consTimestamp = consTimestamp;
    }

    @Override
    public long getGasLimit() {
        return gasLimit;
    }

    @Override
    public long getTimestamp() {
        return consTimestamp.getEpochSecond();
    }

    @Override
    public Optional<Wei> getBaseFee() {
        return Optional.of(Wei.ZERO);
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
