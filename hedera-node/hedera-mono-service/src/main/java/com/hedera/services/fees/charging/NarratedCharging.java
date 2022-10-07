/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees.charging;

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.fee.FeeObject;

/** Defines the checks and charging actions we need to apply the Services fee policy. */
public interface NarratedCharging {
    void setLedger(HederaLedger ledger);

    void setFees(FeeObject fees);

    void resetForTxn(TxnAccessor accessor, long submittingNodeId);

    boolean canPayerAffordAllFees();

    boolean canPayerAffordNetworkFee();

    boolean canPayerAffordServiceFee();

    boolean isPayerWillingToCoverAllFees();

    boolean isPayerWillingToCoverNetworkFee();

    boolean isPayerWillingToCoverServiceFee();

    void chargePayerAllFees();

    void chargePayerServiceFee();

    void refundPayerServiceFee();

    void chargePayerNetworkAndUpToNodeFee();

    void chargeSubmittingNodeUpToNetworkFee();

    long totalFeesChargedToPayer();
}
