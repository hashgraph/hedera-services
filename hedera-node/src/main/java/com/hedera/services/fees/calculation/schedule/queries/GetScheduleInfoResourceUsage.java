package com.hedera.services.fees.calculation.schedule.queries;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;

import java.util.Map;

public class GetScheduleInfoResourceUsage implements QueryResourceUsageEstimator {
    @Override
    public boolean applicableTo(Query query) {
        return false;
    }

    @Override
    public FeeData usageGiven(Query query, StateView view) {
        return null;
    }

    @Override
    public FeeData usageGivenType(Query query, StateView view, ResponseType type) {
        return null;
    }

    @Override
    public FeeData usageGiven(Query query, StateView view, Map<String, Object> queryCtx) {
        return null;
    }
}
