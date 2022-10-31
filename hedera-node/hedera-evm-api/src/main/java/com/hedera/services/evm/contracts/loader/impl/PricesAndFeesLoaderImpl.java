package com.hedera.services.evm.contracts.loader.impl;

import com.hedera.services.evm.contracts.loader.PricesAndFeesLoader;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;

import java.util.EnumMap;
import java.util.Map;

public class PricesAndFeesLoaderImpl implements PricesAndFeesLoader {
    @Override
    public EnumMap<HederaFunctionality, Map<SubType, FeeData>> getCurrFunctionUsagePrices() {
        return null;
    }

    @Override
    public EnumMap<HederaFunctionality, Map<SubType, FeeData>> getNextFunctionUsagePrices() {
        return null;
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
    public ExchangeRateSet getExchangeRates() {
        return null;
    }

    @Override
    public CurrentAndNextFeeSchedule feeSchedules() {
        return null;
    }
}
