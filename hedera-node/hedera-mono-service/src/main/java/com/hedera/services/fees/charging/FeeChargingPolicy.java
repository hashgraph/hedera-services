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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.fee.FeeObject;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides the transaction fee-charging policy for the processing logic. The policy offers four
 * basic entry points:
 *
 * <ol>
 *   <li>For a txn whose submitting node seemed to ignore due diligence (e.g. submitted a txn with
 *       an impermissible valid duration); and,
 *   <li>For a txn that looks to have been submitted responsibly, but is a duplicate of a txn
 *       already submitted by a different node; and,
 *   <li>For a triggered txn; and,
 *   <li>For a txn that was submitted responsibly, and is believed unique.
 * </ol>
 */
@Singleton
public class FeeChargingPolicy {
    private final NarratedCharging narratedCharging;

    @Inject
    public FeeChargingPolicy(NarratedCharging narratedCharging) {
        this.narratedCharging = narratedCharging;
    }

    /**
     * Apply the fee charging policy to a txn that was submitted responsibly, and believed unique.
     *
     * @param fees the fee to charge
     * @return the outcome of applying the policy
     */
    public ResponseCodeEnum apply(FeeObject fees) {
        return chargePendingSolvency(fees);
    }

    /**
     * Apply the fee charging policy to a txn that was submitted responsibly, but is a duplicate of
     * a txn already submitted by a different node.
     *
     * @param fees the fee to charge
     * @return the outcome of applying the policy
     */
    public ResponseCodeEnum applyForDuplicate(FeeObject fees) {
        final var feesForDuplicate = new FeeObject(fees.getNodeFee(), fees.getNetworkFee(), 0L);

        return chargePendingSolvency(feesForDuplicate);
    }

    /**
     * Apply the fee charging policy to a txn that was submitted responsibly, but is a triggered txn
     * rather than a parent txn requiring node precheck work.
     *
     * @param fees the fee to charge
     * @return the outcome of applying the policy
     */
    public ResponseCodeEnum applyForTriggered(FeeObject fees) {
        narratedCharging.setFees(fees);

        if (!narratedCharging.isPayerWillingToCoverServiceFee()) {
            return INSUFFICIENT_TX_FEE;
        } else if (!narratedCharging.canPayerAffordServiceFee()) {
            return INSUFFICIENT_PAYER_BALANCE;
        } else {
            narratedCharging.chargePayerServiceFee();
            return OK;
        }
    }

    /**
     * Apply the fee charging policy to a txn that looks to have been submitted without performing
     * basic due diligence.
     *
     * @param fees the fee to charge
     * @return the outcome of applying the policy
     */
    public ResponseCodeEnum applyForIgnoredDueDiligence(FeeObject fees) {
        narratedCharging.setFees(fees);
        narratedCharging.chargeSubmittingNodeUpToNetworkFee();
        return OK;
    }

    void refundPayerServiceFee() {
        narratedCharging.refundPayerServiceFee();
    }

    private ResponseCodeEnum chargePendingSolvency(FeeObject fees) {
        narratedCharging.setFees(fees);

        if (!narratedCharging.isPayerWillingToCoverNetworkFee()) {
            narratedCharging.chargeSubmittingNodeUpToNetworkFee();
            return INSUFFICIENT_TX_FEE;
        } else if (!narratedCharging.canPayerAffordNetworkFee()) {
            narratedCharging.chargeSubmittingNodeUpToNetworkFee();
            return INSUFFICIENT_PAYER_BALANCE;
        } else {
            return chargeGivenNodeDueDiligence();
        }
    }

    private ResponseCodeEnum chargeGivenNodeDueDiligence() {
        if (!narratedCharging.isPayerWillingToCoverAllFees()) {
            narratedCharging.chargePayerNetworkAndUpToNodeFee();
            return INSUFFICIENT_TX_FEE;
        } else if (!narratedCharging.canPayerAffordAllFees()) {
            narratedCharging.chargePayerNetworkAndUpToNodeFee();
            return INSUFFICIENT_PAYER_BALANCE;
        } else {
            narratedCharging.chargePayerAllFees();
            return OK;
        }
    }
}
