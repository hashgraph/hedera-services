package com.hedera.node.app.fees;

import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Timestamp;

/**
 * Interface for fee calculation. Currently, it is only used to compute payments
 * for Queries. It will be enhanced to be used for transactions as well in the future.
 */
public interface FeeAccumulator {
    FeeObject computePayment(final HederaFunctionality functionality, final Query query, Timestamp now);
}
