package com.hedera.node.app.service.evm.store.contracts.precompile;

import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.isTokenProxyRedirect;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.isViewFunction;

import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import javax.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class EvmHTSPrecompiledContract {

  private final InfrastructureFactory infrastructureFactory;

  @Inject
  public EvmHTSPrecompiledContract(InfrastructureFactory infrastructureFactory) {
    this.infrastructureFactory = infrastructureFactory;
  }

  public Pair<Long, Bytes> computeCosted(final Bytes input, final MessageFrame frame, ViewGasCalculator viewGasCalculator) {
        if (isTokenProxyRedirect(input)) {
          final var executor =
              infrastructureFactory.newRedirectExecutor(
                  input, frame, viewGasCalculator);
          return executor.computeCosted();
        } else if (isViewFunction(input)) {
          final var executor =
              infrastructureFactory.newViewExecutor(
                  input,
                  frame,
                  viewGasCalculator);
          return executor.computeCosted();
        }

    return Pair.of(-1L, Bytes.EMPTY);
  }

}
