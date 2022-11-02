package com.hedera.services.evm.contracts.execution;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;

import java.time.Instant;

import static com.hedera.services.evm.contracts.execution.utils.PricesAndFeesUtils.gasPriceInTinybars;

public class PricesAndFeesImpl implements PricesAndFeesProvider {
    @Override
    public FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at) {
        return null;
    }

    @Override
    public ExchangeRate rate(Timestamp now) {
        return null;
    }

    @Override
    public long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at) {
        var rates = rate(at);
        var prices = defaultPricesGiven(function, at);
        return gasPriceInTinybars(prices, rates);
    }

    @Override
    public long currentGasPrice(Instant now, HederaFunctionality function) {
        return 0;
    }
}
