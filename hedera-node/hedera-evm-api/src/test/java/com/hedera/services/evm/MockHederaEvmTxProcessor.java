package com.hedera.services.evm;

import com.hedera.services.evm.contracts.execution.BlockMetaSource;
import com.hedera.services.evm.contracts.execution.EvmProperties;
import com.hedera.services.evm.contracts.execution.HederaEvmTxProcessor;
import com.hedera.services.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.services.evm.store.contracts.HederaEvmMutableWorldState;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.util.Map;
import javax.inject.Provider;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.Builder;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

public class MockHederaEvmTxProcessor extends HederaEvmTxProcessor {

  protected MockHederaEvmTxProcessor(
      final HederaEvmMutableWorldState worldState,
      final PricesAndFeesProvider livePricesSource,
      final EvmProperties dynamicProperties,
      final GasCalculator gasCalculator,
      final Map<String, Provider<MessageCallProcessor>> mcps,
      final Map<String, Provider<ContractCreationProcessor>> ccps,
      final BlockMetaSource blockMetaSource) {
    super(worldState, livePricesSource, dynamicProperties, gasCalculator, mcps, ccps, blockMetaSource);
  }

  @Override
  protected HederaFunctionality getFunctionType() {
    return HederaFunctionality.ContractCall;
  }

  @Override
  public MessageFrame buildInitialFrame(Builder baseInitialFrame, Address to, Bytes payload,
      long value) {
    return baseInitialFrame
        .type(MessageFrame.Type.MESSAGE_CALL)
        .address(to)
        .contract(to)
        .inputData(payload)
        .code(Code.EMPTY)
        .build();
  }
}
