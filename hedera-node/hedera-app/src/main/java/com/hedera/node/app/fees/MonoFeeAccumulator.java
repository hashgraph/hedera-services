package com.hedera.node.app.fees;

import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Timestamp;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.function.Supplier;

/**
 * Adapter for {@link UsageBasedFeeCalculator} to be used in {@link QueryWorkflow}.
 * This class is currently calling mono-service code and will be replaced with a
 * new implementation as per design.
 */
@Singleton
public class MonoFeeAccumulator implements FeeAccumulator {
    private final UsageBasedFeeCalculator feeCalculator;
    private final UsagePricesProvider resourceCosts;
    private final Supplier<StateView> stateView;
    @Inject
    public MonoFeeAccumulator(final UsageBasedFeeCalculator feeCalculator,
                              final UsagePricesProvider resourceCosts,
                              final Supplier<StateView> stateView) {
        this.feeCalculator = feeCalculator;
        this.resourceCosts = resourceCosts;
        this.stateView = stateView;
    }
    @Override
    public FeeObject computePayment(HederaFunctionality functionality, Query query, Timestamp now) {
        final var usagePrices = resourceCosts.defaultPricesGiven(functionality, now);
        return feeCalculator.computePayment(query, usagePrices,  stateView.get(), now, new HashMap());
    }
}
