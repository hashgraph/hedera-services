/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
            @NonNull final ContractsConfig contractsConfig, @NonNull final ReadableAccountStore accountStore) {
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
