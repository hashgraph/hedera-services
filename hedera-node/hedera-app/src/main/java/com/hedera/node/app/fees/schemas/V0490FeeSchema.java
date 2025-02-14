// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.fees.schemas;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.config.data.BootstrapConfig;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

public class V0490FeeSchema extends Schema {
    public static final String MIDNIGHT_RATES_STATE_KEY = "MIDNIGHT_RATES";

    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(49).patch(0).build();

    public V0490FeeSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.singleton(MIDNIGHT_RATES_STATE_KEY, ExchangeRateSet.PROTOBUF));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        final var isGenesis = ctx.previousVersion() == null;
        if (isGenesis) {
            // Set the initial exchange rates (from the bootstrap config) as the midnight rates
            final var midnightRatesState = ctx.newStates().getSingleton(MIDNIGHT_RATES_STATE_KEY);
            final var bootstrapConfig = ctx.appConfig().getConfigData(BootstrapConfig.class);
            final var exchangeRateSet = ExchangeRateSet.newBuilder()
                    .currentRate(ExchangeRate.newBuilder()
                            .centEquiv(bootstrapConfig.ratesCurrentCentEquiv())
                            .hbarEquiv(bootstrapConfig.ratesCurrentHbarEquiv())
                            .expirationTime(TimestampSeconds.newBuilder().seconds(bootstrapConfig.ratesCurrentExpiry()))
                            .build())
                    .nextRate(ExchangeRate.newBuilder()
                            .centEquiv(bootstrapConfig.ratesNextCentEquiv())
                            .hbarEquiv(bootstrapConfig.ratesNextHbarEquiv())
                            .expirationTime(TimestampSeconds.newBuilder().seconds(bootstrapConfig.ratesNextExpiry()))
                            .build())
                    .build();

            midnightRatesState.put(exchangeRateSet);
        }
    }
}
