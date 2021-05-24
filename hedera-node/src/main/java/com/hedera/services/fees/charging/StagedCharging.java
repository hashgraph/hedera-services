package com.hedera.services.fees.charging;

import com.hederahashgraph.fee.FeeObject;

public interface StagedCharging {
	void setFees(FeeObject fees);

	void chargePayerAllFees();
	void chargePayerServiceFee();
	void chargePayerNetworkAndUpToNodeFee();
	void chargeSubmittingNodeUpToNetworkFee();

	boolean canPayerAffordAllFees();
	boolean canPayerAffordNetworkFee();
	boolean canPayerAffordServiceFee();
	boolean isPayerWillingToCoverAllFees();
	boolean isPayerWillingToCoverNetworkFee();
	boolean isPayerWillingToCoverServiceFee();
}
