package com.hedera.evm.execution;

import com.hedera.services.stream.proto.SidecarType;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Set;

public interface EvmProperties {

  public AccountID fundingAccount();
  public Set<SidecarType> enabledSidecars();

}