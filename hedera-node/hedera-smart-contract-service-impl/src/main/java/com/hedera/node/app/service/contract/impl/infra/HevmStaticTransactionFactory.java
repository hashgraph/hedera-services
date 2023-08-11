package com.hedera.node.app.service.contract.impl.infra;

import com.hedera.hapi.node.contract.ContractCallLocalQuery;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.service.contract.impl.annotations.QueryScope;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;

@QueryScope
public class HevmStaticTransactionFactory {
    private final ContractsConfig contractsConfig;
    private final ReadableAccountStore accountStore;

    @Inject
    public HevmStaticTransactionFactory(
            @NonNull final ContractsConfig contractsConfig,
            @NonNull final ReadableAccountStore accountStore) {
        this.contractsConfig = contractsConfig;
        this.accountStore = accountStore;
    }

    /**
     * Given a {@link Query}, creates the implied {@link HederaEvmTransaction}.
     *
     * @param query the {@link ContractCallLocalQuery} to convert
     * @return the implied {@link HederaEvmTransaction}
     */
    public HederaEvmTransaction fromHapiQuery(@NonNull final Query query) {
        throw new AssertionError("Not implemented");
    }
}
