package com.hedera.node.app.fees;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;

import java.time.Instant;

public interface FeeCalculator {
    long computePayment(HederaFunctionality function, Query query, Instant now);
}
