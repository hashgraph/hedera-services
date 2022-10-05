package com.hedera.services.evm.implementation.contracts.execution;

import com.hedera.services.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.services.evm.implementation.contracts.execution.traceability.HederaEvmTracer;
import com.hedera.services.evm.implementation.store.models.EvmAccount;
import com.hedera.services.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.services.evm.store.contracts.HederaEvmWorldState;
import com.hedera.services.stream.proto.SidecarType;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import javax.inject.Provider;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * Abstract processor of EVM transactions that prepares the {@link EVM} and all the peripherals upon
 * instantiation. Provides a base {@link EvmTxProcessor#execute(EvmAccount, Address, long, long, long,
 * Bytes, boolean, boolean, Address, BigInteger, long, EvmAccount)} method that handles the end-to-end
 * execution of an EVM transaction.
 */
abstract class HederaEvmTxProcessor {
  private static final int MAX_STACK_SIZE = 1024;
  private static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);

  private BlockMetaSource blockMetaSource;
  private HederaEvmMutableWorldState worldState;

  private final GasCalculator gasCalculator;
  private final PricesAndFeesProvider livePricesSource;
  private final Map<String, Provider<MessageCallProcessor>> mcps;
  private final Map<String, Provider<ContractCreationProcessor>> ccps;
  private AbstractMessageProcessor messageCallProcessor;
  private AbstractMessageProcessor contractCreationProcessor;
  private HederaEvmTransactionProcessingResult transactionProcessingResult;
  private OperationTracer tracer;
  protected final EvmProperties dynamicProperties;

  protected HederaEvmTxProcessor(
      final PricesAndFeesProvider livePricesSource,
      final EvmProperties dynamicProperties,
      final GasCalculator gasCalculator,
      final Map<String, Provider<MessageCallProcessor>> mcps,
      final Map<String, Provider<ContractCreationProcessor>> ccps) {
    this(null, livePricesSource, dynamicProperties, gasCalculator, mcps, ccps, null);
  }

  protected void setBlockMetaSource(final BlockMetaSource blockMetaSource) {
    this.blockMetaSource = blockMetaSource;
  }

  protected void setWorldState(final HederaEvmMutableWorldState worldState) {
    this.worldState = worldState;
  }

  protected void setOperationTracer(final OperationTracer tracer) {
    this.tracer = tracer;
  }

  protected HederaEvmTxProcessor(
      final HederaEvmMutableWorldState worldState,
      final PricesAndFeesProvider livePricesSource,
      final EvmProperties dynamicProperties,
      final GasCalculator gasCalculator,
      final Map<String, Provider<MessageCallProcessor>> mcps,
      final Map<String, Provider<ContractCreationProcessor>> ccps,
      final BlockMetaSource blockMetaSource) {
    this.worldState = worldState;
    this.livePricesSource = livePricesSource;
    this.dynamicProperties = dynamicProperties;
    this.gasCalculator = gasCalculator;

    this.mcps = mcps;
    this.ccps = ccps;
    this.messageCallProcessor = mcps.get(dynamicProperties.evmVersion()).get();
    this.contractCreationProcessor = ccps.get(dynamicProperties.evmVersion()).get();
    this.blockMetaSource = blockMetaSource;
  }

  /**
   * Executes the {@link MessageFrame} of the EVM transaction. Returns the result as {@link
   * TransactionProcessingResult}
   *
   * @param sender The origin {@link EvmAccount} that initiates the transaction
   * @param receiver the priority form of the receiving {@link Address} (i.e., EIP-1014 if
   *     present); or the newly created address
   * @param gasPrice GasPrice to use for gas calculations
   * @param gasLimit Externally provided gas limit
   * @param value transaction value
   * @param payload transaction payload. For Create transactions, the bytecode + constructor
   *     arguments
   * @param contractCreation if this is a contract creation transaction
   * @param isStatic Whether the execution is static
   * @param mirrorReceiver the mirror form of the receiving {@link Address}; or the newly created
   *     address
   * @return the result of the EVM execution returned as {@link TransactionProcessingResult}
   */
  protected void execute(
      final EvmAccount sender,
      final Address receiver,
      final long gasPrice,
      final long gasLimit,
      final long value,
      final Bytes payload,
      final boolean contractCreation,
      final boolean isStatic,
      final Address mirrorReceiver) {
    final long intrinsicGas =
        gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, contractCreation);

    final HederaEvmWorldState updater = (HederaEvmWorldState) worldState.updater();

    final var coinbase = dynamicProperties.fundingAccount();
    final var blockValues = blockMetaSource.computeBlockValues(gasLimit);
    final var gasAvailable = gasLimit - intrinsicGas;
    final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();

    final var valueAsWei = Wei.of(value);
    final var stackedUpdater = updater.updater();
    final var senderEvmAddress = sender.canonicalAddress();
    final MessageFrame.Builder commonInitialFrame =
        MessageFrame.builder()
            .messageFrameStack(messageFrameStack)
            .maxStackSize(MAX_STACK_SIZE)
            .worldUpdater(stackedUpdater)
            .initialGas(gasAvailable)
            .originator(senderEvmAddress)
            .gasPrice(Wei.of(gasPrice))
            .sender(senderEvmAddress)
            .value(valueAsWei)
            .apparentValue(valueAsWei)
            .blockValues(blockValues)
            .depth(0)
            .completer(unused -> {})
            .isStatic(isStatic)
            .miningBeneficiary(coinbase)
            .blockHashLookup(blockMetaSource::getBlockHash)
            .contextVariables(Map.of("HederaFunctionality", getFunctionType()));

    final MessageFrame initialFrame =
        buildInitialFrame(commonInitialFrame, receiver, payload, value);
    messageFrameStack.addFirst(initialFrame);

    if (dynamicProperties.dynamicEvmVersion()) {
      String evmVersion = dynamicProperties.evmVersion();
      messageCallProcessor = mcps.get(evmVersion).get();
      contractCreationProcessor = ccps.get(evmVersion).get();
    }

    while (!messageFrameStack.isEmpty()) {
      process(messageFrameStack.peekFirst(), tracer);
    }

    var gasUsedByTransaction = calculateGasUsedByTX(gasLimit, initialFrame);
    final long sbhRefund = updater.getSbhRefund();

    // Externalise result
    if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
      this.transactionProcessingResult = HederaEvmTransactionProcessingResult.successful(
          initialFrame.getLogs(),
          gasUsedByTransaction,
          sbhRefund,
          gasPrice,
          initialFrame.getOutputData(),
          mirrorReceiver);
    } else {
      this.transactionProcessingResult = HederaEvmTransactionProcessingResult.failed(
          gasUsedByTransaction,
          sbhRefund,
          gasPrice,
          initialFrame.getRevertReason(),
          initialFrame.getExceptionalHaltReason());
    }
  }

  public HederaEvmTransactionProcessingResult getResult() {
    return this.transactionProcessingResult;
  }

  private long calculateGasUsedByTX(final long txGasLimit, final MessageFrame initialFrame) {
    long gasUsedByTransaction = txGasLimit - initialFrame.getRemainingGas();
    /* Return leftover gas */
    final long selfDestructRefund =
        gasCalculator.getSelfDestructRefundAmount()
            * Math.min(
            initialFrame.getSelfDestructs().size(),
            gasUsedByTransaction / (gasCalculator.getMaxRefundQuotient()));

    gasUsedByTransaction =
        gasUsedByTransaction - selfDestructRefund - initialFrame.getGasRefund();

    final var maxRefundPercent = dynamicProperties.maxGasRefundPercentage();
    gasUsedByTransaction =
        Math.max(gasUsedByTransaction, txGasLimit - txGasLimit * maxRefundPercent / 100);

    return gasUsedByTransaction;
  }

  protected long gasPriceTinyBarsGiven(final Instant consensusTime, boolean isEthTxn) {
    return livePricesSource.currentGasPrice(
        consensusTime,
        isEthTxn ? HederaFunctionality.EthereumTransaction : getFunctionType());
  }

  protected abstract HederaFunctionality getFunctionType();

  protected abstract MessageFrame buildInitialFrame(
      MessageFrame.Builder baseInitialFrame, Address to, Bytes payload, final long value);

  protected void process(final MessageFrame frame, final OperationTracer operationTracer) {
    final AbstractMessageProcessor executor = getMessageProcessor(frame.getType());

    executor.process(frame, operationTracer);
  }

  private AbstractMessageProcessor getMessageProcessor(final MessageFrame.Type type) {
    return switch (type) {
      case MESSAGE_CALL -> messageCallProcessor;
      case CONTRACT_CREATION -> contractCreationProcessor;
    };
  }
}
