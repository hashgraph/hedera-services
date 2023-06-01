package com.hedera.node.app.service.contract.impl.infra;

import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.state.StorageSizeChange;
import com.hedera.node.app.spi.meta.bni.Scope;
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import java.util.List;

import static java.util.Objects.requireNonNull;

@TransactionScope
public class StorageSizeValidator {
    private final ContractsConfig contractsConfig;

    @Inject
    public StorageSizeValidator(@NonNull final ContractsConfig contractsConfig) {
        this.contractsConfig = requireNonNull(contractsConfig);
    }

    public void assertValid(
            final long totalSlotsUsed,
            @NonNull final Scope scope,
            @NonNull final List<StorageSizeChange> storageSizeChanges) {
        throw new AssertionError("Not implemented");
    }
}
