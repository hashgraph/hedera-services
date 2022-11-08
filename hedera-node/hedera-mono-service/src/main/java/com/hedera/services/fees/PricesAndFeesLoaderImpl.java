package com.hedera.services.fees;

import com.hedera.services.evm.contracts.loader.PricesAndFeesLoader;
import com.hedera.services.fees.calculation.BasicFcfsUsagePrices;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;

import javax.inject.Inject;
import java.util.EnumMap;
import java.util.Map;

public class PricesAndFeesLoaderImpl implements PricesAndFeesLoader {
    private Timestamp currFunctionUsagePricesExpiry;
    private Timestamp nextFunctionUsagePricesExpiry;
    private EnumMap<HederaFunctionality, Map<SubType, FeeData>> currFunctionUsagePrices;
    private EnumMap<HederaFunctionality, Map<SubType, FeeData>> nextFunctionUsagePrices;

    private final BasicFcfsUsagePrices usagePrices;
    private final BasicHbarCentExchange exchange;

    @Inject
    public PricesAndFeesLoaderImpl(BasicFcfsUsagePrices usagePrices, BasicHbarCentExchange exchange) {
        this.usagePrices = usagePrices;
        this.exchange = exchange;
    }


    @Override
    public Timestamp currFunctionUsagePricesExpiry() {
        return null;
    }

    @Override
    public Timestamp nextFunctionUsagePricesExpiry() {
        return null;
    }

    @Override
    public EnumMap<HederaFunctionality, Map<SubType, FeeData>> currFunctionUsagePrices() {
        return null;
    }

    @Override
    public EnumMap<HederaFunctionality, Map<SubType, FeeData>> nextFunctionUsagePrices() {
        return null;
    }

    @Override
    public CurrentAndNextFeeSchedule getFeeSchedules(long now) {
        CurrentAndNextFeeSchedule feeSchedule = null;
        usagePrices.loadPriceSchedules();

        return feeSchedule;
    }
}
