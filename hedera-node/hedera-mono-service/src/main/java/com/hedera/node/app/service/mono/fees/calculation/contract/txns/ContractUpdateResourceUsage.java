/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.fees.calculation.contract.txns;

import static com.hedera.node.app.service.mono.fees.calculation.FeeCalcUtils.lookupAccountExpiry;
import static com.hedera.node.app.service.mono.utils.EntityNum.fromContractId;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asTimestamp;
import static com.hedera.node.app.spi.fees.Fees.CONSTANT_FEE_DATA;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.hapi.utils.fee.SmartContractFeeBuilder;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.calculation.TxnResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ContractUpdateResourceUsage implements TxnResourceUsageEstimator {
    private static final Logger log = LogManager.getLogger(ContractUpdateResourceUsage.class);

    private final SmartContractFeeBuilder usageEstimator;

    @Inject
    public ContractUpdateResourceUsage(SmartContractFeeBuilder usageEstimator) {
        this.usageEstimator = usageEstimator;
    }

    @Override
    public boolean applicableTo(TransactionBody txn) {
        return txn.hasContractUpdateInstance();
    }

    @Override
    public FeeData usageGiven(TransactionBody txn, SigValueObj sigUsage, StateView view) {
        try {
            final var id = fromContractId(txn.getContractUpdateInstance().getContractID());
            Timestamp expiry = lookupAccountExpiry(id, view.accounts());
            return usageEstimator.getContractUpdateTxFeeMatrices(txn, expiry, sigUsage);
        } catch (Exception e) {
            log.debug("Unable to deduce ContractUpdate usage for {}, using defaults", txn.getTransactionID(), e);
            return FeeData.getDefaultInstance();
        }
    }

    /**
     * Returns the estimated resource usage for the given txn relative to the given state of the
     * world.
     * @param txn the txn in question
     * @param sigUsage the signature usage
     * @param contract the contract
     * @return the estimated resource usage
     */
    public FeeData usageGiven(@NonNull TransactionBody txn, @NonNull SigValueObj sigUsage, @Nullable Account contract) {
        if (contract == null) {
            return CONSTANT_FEE_DATA;
        }
        Timestamp expiry = asTimestamp(contract.expirationSecond());
        return usageEstimator.getContractUpdateTxFeeMatrices(txn, expiry, sigUsage);
    }
}
