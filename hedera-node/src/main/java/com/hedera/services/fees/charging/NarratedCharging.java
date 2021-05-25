package com.hedera.services.fees.charging;

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hederahashgraph.fee.FeeObject;

/**
 * Defines the checks and charging actions we need to apply the Services fee policy.
 */
public interface NarratedCharging {
	void setFees(FeeObject fees);
	void resetForTxn(MerkleEntityId payerId, long submittingNodeId, long offeredTotalFee);

	boolean canPayerAffordAllFees();
	boolean canPayerAffordNetworkFee();
	boolean canPayerAffordServiceFee();
	boolean isPayerWillingToCoverAllFees();
	boolean isPayerWillingToCoverNetworkFee();
	boolean isPayerWillingToCoverServiceFee();

	void chargePayerAllFees();
	void chargePayerServiceFee();
	void chargePayerNetworkAndUpToNodeFee();
	void chargeSubmittingNodeUpToNetworkFee();
}
