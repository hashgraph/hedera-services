package com.hedera.services.evm.implementation.contracts.execution;

import com.hedera.services.stream.proto.SidecarType;
import java.util.Set;
import org.hyperledger.besu.datatypes.Address;

public interface EvmProperties {

  String evmVersion();
  Address fundingAccount();
  boolean dynamicEvmVersion();
  Set<SidecarType> enabledSidecars();
  int maxGasRefundPercentage();

}
