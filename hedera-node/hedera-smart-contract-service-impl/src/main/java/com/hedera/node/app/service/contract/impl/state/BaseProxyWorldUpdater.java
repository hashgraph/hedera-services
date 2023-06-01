package com.hedera.node.app.service.contract.impl.state;

import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.infra.RentCalculator;
import com.hedera.node.app.service.contract.impl.infra.StorageLinkedLists;
import com.hedera.node.app.service.contract.impl.infra.StorageSizeValidator;
import com.hedera.node.app.spi.meta.bni.Scope;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;

@TransactionScope
public class BaseProxyWorldUpdater extends ProxyWorldUpdater {
    private final RentCalculator rentCalculator;
    private final StorageLinkedLists linkedLists;
    private final StorageSizeValidator storageSizeValidator;

    @Inject
    public BaseProxyWorldUpdater(
            @NonNull final Scope scope,
            @NonNull final EvmFrameStateFactory evmFrameStateFactory,
            @NonNull final RentCalculator rentCalculator,
            @NonNull final StorageLinkedLists linkedLists,
            @NonNull final StorageSizeValidator storageSizeValidator) {
        super(scope, evmFrameStateFactory, null);
        this.linkedLists = linkedLists;
        this.rentCalculator = rentCalculator;
        this.storageSizeValidator = storageSizeValidator;
    }

    /**
     * Before committing the changes to the base scope via {@code super.commit()}, does the following steps:
     * <ol>
     *     <li>Gets the list of pending storage changes and summarizes their effect on size.</li>
     *     <li>Validates the effects on size are legal.</li>
     *     <li>For each increase in storage size, calculates rent and tries to charge the allocating contract.</li>
     *     <li>"Rewrites" the pending storage changes to preserve per-contract linked lists.</li>
     * </ol>
     */
    @Override
    public void commit() {
        super.commit();
    }
}
