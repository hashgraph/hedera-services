package com.hedera.node.app.service.evm.store.contracts.precompile;

import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.isTokenProxyRedirect;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.isViewFunction;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class EvmHTSPrecompiledContract {

  public Pair<Long, Bytes> computeCosted(final Bytes input, final MessageFrame frame) {
        if (isTokenProxyRedirect(input)) {
          final var executor =
              infrastructureFactory.newRedirectExecutor(
                  input, frame, precompilePricingUtils::computeViewFunctionGas);
          return executor.computeCosted();
        } else if (isViewFunction(input)) {
          final var executor =
              infrastructureFactory.newViewExecutor(
                  input,
                  frame,
                  precompilePricingUtils::computeViewFunctionGas,
                  currentView);
          return executor.computeCosted();
        }
  }

}
