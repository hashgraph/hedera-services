package com.hedera.services.evm.contracts.execution;

import java.time.Instant;
import com.hederahashgraph.api.proto.java.HederaFunctionality;

public interface PricesAndFeesProvider {
  long currentGasPrice(final Instant now, final HederaFunctionality function);
}
