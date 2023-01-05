package com.hedera.node.app.service.evm.store.contracts.precompile.proxy;

import static com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectViewExecutor.asSecondsTimestamp;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class ViewExecutor {

  public static final long MINIMUM_TINYBARS_COST = 100;

  private final Bytes input;
  private final MessageFrame frame;
  private final EvmEncodingFacade evmEncoder;
  private final ViewGasCalculator gasCalculator;

  public ViewExecutor(
      final Bytes input,
      final MessageFrame frame,
      final EvmEncodingFacade evmEncoder,
      final ViewGasCalculator gasCalculator) {
    this.input = input;
    this.frame = frame;
    this.evmEncoder = evmEncoder;
    this.gasCalculator = gasCalculator;
  }

  public Pair<Long, Bytes> computeCosted() {
    final var now = asSecondsTimestamp(frame.getBlockValues().getTimestamp());
    final var costInGas = gasCalculator.compute(now, MINIMUM_TINYBARS_COST);

    final var selector = input.getInt(0);
    try {
      final var answer = answerGiven(selector);
      return Pair.of(costInGas, answer);
    } catch (final InvalidTransactionException e) {
      if (e.isReverting()) {
        frame.setRevertReason(e.getRevertReason());
        frame.setState(MessageFrame.State.REVERT);
      }
      return Pair.of(costInGas, null);
    }
  }

  private Bytes answerGiven(final int selector) {

    return Bytes.EMPTY;
  }

}
