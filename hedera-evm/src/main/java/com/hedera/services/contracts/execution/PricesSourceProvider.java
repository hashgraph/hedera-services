package com.hedera.services.contracts.execution;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;

/**
 * Provides methods for price calculation based on a given {@link Timestamp}
 */
public interface PricesSourceProvider {

    FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at);

    ExchangeRate rate(Timestamp at);
}
