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

import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HevmTransactionFactory {
    @Inject
    public HevmTransactionFactory() {
        // Dagger2
    }

    /**
     * Given a {@link TransactionBody}, creates the implied {@link HederaEvmTransaction}.
     *
     * @param body the {@link TransactionBody} to convert
     * @return the implied {@link HederaEvmTransaction}
     * @throws IllegalArgumentException if the {@link TransactionBody} is not a contract operation
     */
    public HederaEvmTransaction fromHapiTransaction(@NonNull final TransactionBody body) {
        return switch (body.data().kind()) {
            case CONTRACT_CREATE_INSTANCE -> fromHapiCreate(body.contractCreateInstanceOrThrow());
            case CONTRACT_CALL -> fromHapiCall(body.contractCallOrThrow());
            case ETHEREUM_TRANSACTION -> fromHapiEthereum(body.ethereumTransactionOrThrow());
            default -> throw new IllegalArgumentException("Not a contract operation");
        };
    }

    private HederaEvmTransaction fromHapiCall(@NonNull final ContractCallTransactionBody body) {
        throw new AssertionError("Not implemented");
    }

    private HederaEvmTransaction fromHapiCreate(@NonNull final ContractCreateTransactionBody body) {
        throw new AssertionError("Not implemented");
    }

    private HederaEvmTransaction fromHapiEthereum(@NonNull final EthereumTransactionBody body) {
        throw new AssertionError("Not implemented");
    }
}
