package com.hedera.services.context.properties;

import com.hederahashgraph.api.proto.java.AccountID;

public interface PropertiesProvider {

    int chainId();

    AccountID fundingAccount();

    boolean shouldEnableTraceability();

    int maxGasRefundPercentage();
}
