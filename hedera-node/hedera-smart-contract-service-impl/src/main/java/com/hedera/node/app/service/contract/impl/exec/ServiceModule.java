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

package com.hedera.node.app.service.contract.impl.exec;

import static java.util.Map.entry;

import com.hedera.node.app.service.contract.impl.annotations.ServicesV030;
import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.v030.V030Module;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.Multibinds;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

@Module(includes = {V030Module.class})
public interface ServiceModule {
    @Binds
    @Singleton
    GasCalculator bindGasCalculator(@NonNull final CustomGasCalculator gasCalculator);

    @Multibinds
    Map<Address, PrecompiledContract> bindHederaPrecompiles();

    @Provides
    @Singleton
    static Map<String, TransactionProcessor> provideTransactionProcessors(
            @ServicesV030 @NonNull final TransactionProcessor v030Processor) {
        return Map.ofEntries(entry("v0.30", v030Processor));
    }
}
