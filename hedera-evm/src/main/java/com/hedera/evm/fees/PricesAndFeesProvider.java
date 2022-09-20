package com.hedera.evm.fees;

import com.hedera.evm.store.contracts.precompile.Precompile;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.fee.FeeObject;

import java.time.Instant;

public interface PricesAndFeesProvider {
    FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at);
    ExchangeRate rate(Timestamp at);
    long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at);
    FeeObject getRedirectQueryFee(FeeData usagePrices, Timestamp at);
    long gasFeeInTinybars(final Instant consensusTime, final Precompile precompile);
}
