package com.hedera.node.app.service.contract.impl.exec;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

/**
 * Provides address checks used to customize behavior of Hedera {@link org.hyperledger.besu.evm.operation.Operation} overrides.
 */
public interface AddressChecks {
   boolean isPresent(@NonNull Address address, @NonNull WorldUpdater worldUpdater);
   boolean isSystemContract(@NonNull Address address);
}
