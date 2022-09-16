package com.hedera.evm.execution;

import com.hedera.services.stream.proto.SidecarType;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;

public interface EvmProperties {

  public AccountID fundingAccount();
  public Set<SidecarType> enabledSidecars();
  public Bytes32 chainIdBytes32();
  public int maxGasRefundPercentage();

}