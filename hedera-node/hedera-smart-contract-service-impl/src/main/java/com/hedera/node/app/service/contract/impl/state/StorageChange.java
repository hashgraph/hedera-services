package com.hedera.node.app.service.contract.impl.state;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.units.bigints.UInt256;

import static java.util.Objects.requireNonNull;

public record StorageChange(@NonNull UInt256 key, @NonNull UInt256 oldValue, @NonNull UInt256 newValue) {
    public StorageChange {
        requireNonNull(key, "Key cannot be null");
        requireNonNull(oldValue, "Old value cannot be null");
        requireNonNull(newValue, "New value cannot be null");
    }
}
