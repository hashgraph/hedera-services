/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.BlockValues;

public record HederaEvmContext(long gasPrice, boolean staticCall, HederaEvmCode code, HederaEvmBlocks blocks) {
    public Code load(@NonNull final Address contract) {
        return code.load(Objects.requireNonNull(contract));
    }

    public Code loadIfPresent(@NonNull final Address contract) {
        return code.loadIfPresent(Objects.requireNonNull(contract));
    }

    public BlockValues blockValuesOf(final long gasLimit) {
        return blocks.blockValuesOf(gasLimit);
    }

    public boolean isNoopGasContext() {
        return staticCall || gasPrice == 0;
    }
}
