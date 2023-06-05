package com.hedera.node.app.service.contract.impl.exec.v030;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

public class SystemAgnosticAddressChecks implements AddressChecks {
    @Override
    public boolean isPresent(@NonNull Address address, @NonNull WorldUpdater worldUpdater) {
        throw new AssertionError("Not implemented");
    }

    @Override
    public boolean isSystemContract(@NonNull Address address) {
        throw new AssertionError("Not implemented");
    }
}
