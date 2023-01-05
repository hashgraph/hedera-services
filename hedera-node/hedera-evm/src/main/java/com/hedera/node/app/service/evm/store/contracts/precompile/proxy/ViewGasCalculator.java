package com.hedera.node.app.service.evm.store.contracts.precompile.proxy;

import com.hederahashgraph.api.proto.java.Timestamp;

@FunctionalInterface
public interface ViewGasCalculator {
  long compute(final Timestamp now, final long minimumTinybarCost);
}
