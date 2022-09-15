package com.hedera.evm.execution;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.time.Instant;

public interface LivePricesSource {

  public long currentGasPrice(final Instant now, final HederaFunctionality function);

}
