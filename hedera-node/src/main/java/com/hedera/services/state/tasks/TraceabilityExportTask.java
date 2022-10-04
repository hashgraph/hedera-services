package com.hedera.services.state.tasks;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.store.contracts.EntityAccess;
import com.hedera.services.throttling.ExpiryThrottle;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.experimental.categories.Categories;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.function.Supplier;

public class TraceabilityExportTask implements SystemTask {
    private final EntityAccess entityAccess;
    private final ExpiryThrottle expiryThrottle;
    private final TransactionContext txnCtx;
    private final GlobalDynamicProperties dynamicProperties;
    private final Supplier<MerkleNetworkContext> networkCtx;
    private final Supplier<VirtualMap<ContractKey, IterableContractValue>> contractStorage;

    @Inject
    public TraceabilityExportTask(
            final EntityAccess entityAccess,
            final ExpiryThrottle expiryThrottle,
            final TransactionContext txnCtx,
            final GlobalDynamicProperties dynamicProperties,
            final Supplier<MerkleNetworkContext> networkCtx,
            final Supplier<VirtualMap<ContractKey, IterableContractValue>> contractStorage) {
        this.entityAccess = entityAccess;
        this.expiryThrottle = expiryThrottle;
        this.txnCtx = txnCtx;
        this.dynamicProperties = dynamicProperties;
        this.networkCtx = networkCtx;
        this.contractStorage = contractStorage;
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public SystemTaskResult process(long literalNum, Instant now) {
        return null;
    }
}
