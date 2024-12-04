/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.gas;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@link DispatchGasCalculator} that calculates the gas requirement for the dispatch, given the transaction body,
 * payer, {@link SystemContractGasCalculator} and the {@link Enhancement} in the dispatch context
 */
@FunctionalInterface
public interface DispatchGasCalculator {
    /**
     * Given a transaction body to be dispatched, along with the effective payer, the
     * {@link SystemContractGasCalculator}, and the {@link Enhancement} in the dispatch context;
     * returns the gas requirement for the dispatch.
     *
     * @param body the transaction body to be dispatched
     * @param systemContractGasCalculator the {@link SystemContractGasCalculator} in the dispatch context
     * @param enhancement the {@link Enhancement} in the dispatch context
     * @param payerId the synthetic payer of the transaction
     * @return the gas requirement for the dispatch
     */
    long gasRequirement(
            @NonNull TransactionBody body,
            @NonNull SystemContractGasCalculator systemContractGasCalculator,
            @NonNull Enhancement enhancement,
            @NonNull AccountID payerId);
}
