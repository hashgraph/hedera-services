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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction.NOT_APPLICABLE;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static org.apache.tuweni.bytes.Bytes.EMPTY;

import com.hedera.hapi.node.contract.ContractCallLocalQuery;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.service.contract.impl.annotations.QueryScope;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

@QueryScope
public class HevmStaticTransactionFactory {
    private static final long INTRINSIC_GAS_LOWER_BOUND = 21_000L;
    private final ContractsConfig contractsConfig;
    private final GasCalculator gasCalculator;
    private final ReadableAccountStore accountStore;

    @Inject
    public HevmStaticTransactionFactory(
            @NonNull final ContractsConfig contractsConfig,
            @NonNull final GasCalculator gasCalculator,
            @NonNull final ReadableAccountStore accountStore) {
        this.contractsConfig = contractsConfig;
        this.gasCalculator = gasCalculator;
        this.accountStore = accountStore;
    }

    /**
     * Given a {@link Query}, creates the implied {@link HederaEvmTransaction}.
     *
     * @param query the {@link ContractCallLocalQuery} to convert
     * @return the implied {@link HederaEvmTransaction}
     */
    @NonNull
    public HederaEvmTransaction fromHapiQuery(@NonNull final Query query) {
        final var op = query.contractCallLocalOrThrow();
        assertValidCall(op);
        final var senderId = op.hasSenderId()
                ? op.senderId()
                : op.headerOrThrow()
                        .paymentOrThrow()
                        .bodyOrThrow()
                        .transactionIDOrThrow()
                        .accountIDOrThrow();
        return new HederaEvmTransaction(
                senderId,
                null,
                op.contractIDOrThrow(),
                NOT_APPLICABLE,
                op.functionParameters(),
                null,
                0L,
                op.gas(),
                1L,
                0L,
                null);
    }

    private void assertValidCall(@NonNull final ContractCallLocalQuery body) {
        final var minGasLimit =
                Math.max(INTRINSIC_GAS_LOWER_BOUND, gasCalculator.transactionIntrinsicGasCost(EMPTY, false));
        validateTrue(body.gas() >= minGasLimit, INSUFFICIENT_GAS);
        validateTrue(body.gas() <= contractsConfig.maxGasPerSec(), MAX_GAS_LIMIT_EXCEEDED);
    }
}
