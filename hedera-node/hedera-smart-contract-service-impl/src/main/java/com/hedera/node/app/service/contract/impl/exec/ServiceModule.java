package com.hedera.node.app.service.contract.impl.exec;

import com.hedera.node.app.service.contract.impl.annotations.ServicesV030;
import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.v030.V030Module;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.Multibinds;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

import javax.inject.Singleton;
import java.util.Map;

import static java.util.Map.entry;

@Module(includes = {
        V030Module.class
})
public interface ServiceModule {
    @Binds
    @Singleton
    GasCalculator bindGasCalculator(@NonNull final CustomGasCalculator gasCalculator);

    @Multibinds
    Map<String, PrecompiledContract> bindHederaSystemContracts();

    @Provides
    @Singleton
    static Map<String, TransactionProcessor> provideTransactionProcessors(
            @ServicesV030 @NonNull final TransactionProcessor v030Processor
    ) {
        return Map.ofEntries(
                entry("v0.30", v030Processor));
    }
}
