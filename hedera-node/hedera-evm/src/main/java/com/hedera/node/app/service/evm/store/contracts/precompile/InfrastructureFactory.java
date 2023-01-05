package com.hedera.node.app.service.evm.store.contracts.precompile;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectViewExecutor;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewExecutor;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import javax.inject.Inject;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class InfrastructureFactory {

  private final EvmEncodingFacade evmEncoder;

  @Inject
  public InfrastructureFactory(EvmEncodingFacade evmEncoder) {
    this.evmEncoder = evmEncoder;
  }

  public RedirectViewExecutor newRedirectExecutor(
      final Bytes input, final MessageFrame frame, final ViewGasCalculator gasCalculator) {
    return new RedirectViewExecutor(input, frame, evmEncoder, gasCalculator);
  }

  public ViewExecutor newViewExecutor(
      final Bytes input,
      final MessageFrame frame,
      final ViewGasCalculator gasCalculator) {
    return new ViewExecutor(input, frame, evmEncoder, gasCalculator);
  }
}
