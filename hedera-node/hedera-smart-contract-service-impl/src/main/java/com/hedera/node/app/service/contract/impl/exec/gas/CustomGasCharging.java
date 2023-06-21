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

package com.hedera.node.app.service.contract.impl.exec.gas;

import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

@Singleton
public class CustomGasCharging {
    private final GasCalculator gasCalculator;

    public CustomGasCharging(@NonNull final GasCalculator gasCalculator) {
        this.gasCalculator = gasCalculator;
    }

    /**
     * Tries to charge gas for the given transaction based on the pre-fetched sender and relayer accounts,
     * within the given context and world updater.
     *
     * @param sender  the sender account
     * @param relayer the relayer account
     * @param context the context of the transaction, including the network gas price
     * @param worldUpdater the world updater for the transaction
     * @param transaction the transaction to charge gas for
     * @return the result of the gas charging
     * @throws HandleException if the gas charging fails fo
     */
    public long chargeGasAllowance(
            @NonNull final HederaEvmAccount sender,
            @Nullable final HederaEvmAccount relayer,
            @NonNull final HederaEvmContext context,
            @NonNull final HederaWorldUpdater worldUpdater,
            @NonNull final HederaEvmTransaction transaction) {
        if (context.staticCall()) {
            return 0L;
        }
        throw new AssertionError("Not implemented");
    }
}
