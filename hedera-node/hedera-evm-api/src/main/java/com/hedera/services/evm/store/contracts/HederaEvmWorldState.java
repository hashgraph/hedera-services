package com.hedera.services.evm.store.contracts;

import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public interface HederaEvmWorldState extends HederaWorldUpdater, HederaEvmMutableWorldState {

  Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> getFinalStateChanges();

}

