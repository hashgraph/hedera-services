package com.hedera.node.app.fees;

import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.HashMap;
import java.util.function.Supplier;

import static com.hedera.node.app.service.mono.utils.MiscUtils.asTimestamp;

@Singleton
public class MonoFeeCalculator implements FeeCalculator {
    private final Supplier<StateView> stateViews;
    private final UsagePricesProvider resourceCosts;
    private final com.hedera.node.app.service.mono.fees.FeeCalculator fees;

    @Inject
    public MonoFeeCalculator(
            Supplier<StateView> stateViews,
            UsagePricesProvider resourceCosts,
            com.hedera.node.app.service.mono.fees.FeeCalculator fees) {
        this.stateViews = stateViews;
        this.resourceCosts = resourceCosts;
        this.fees = fees;
    }

    @Override
    public long computePayment(HederaFunctionality function, Query query, Instant now) {
        final var timeNow = asTimestamp(now);
        return totalOf(fees.computePayment(
                query,
                resourceCosts.defaultPricesGiven(function, timeNow),
                stateViews.get(),
                timeNow,
                new HashMap<>()));
    }

    private long totalOf(final FeeObject costs) {
        return costs.getNetworkFee() + costs.getServiceFee() + costs.getNodeFee();
    }
}
