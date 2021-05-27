package com.hedera.services.fees.charging;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.records.TxnIdRecentHistory;
import com.hedera.services.state.logic.AwareNodeDiligenceScreen;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.TransactionID;

import java.util.Map;

/**
 * Uses a (non-triggered) transaction's duplicate classification and
 * node due diligence screen to pick one of the three charging policies.
 *
 * Please see {@link FeeChargingPolicy} for details.
 */
public class TxnChargingPolicyAgent {
	private final FeeCalculator fees;
	private final TransactionContext txnCtx;
	private final AwareNodeDiligenceScreen nodeDiligenceScreen;
	private final Map<TransactionID, TxnIdRecentHistory> txnHistories;

	public TxnChargingPolicyAgent(
			FeeCalculator fees,
			TransactionContext txnCtx,
			AwareNodeDiligenceScreen nodeDiligenceScreen,
			Map<TransactionID, TxnIdRecentHistory> txnHistories
	) {
		this.fees = fees;
		this.txnCtx = txnCtx;
		this.nodeDiligenceScreen = nodeDiligenceScreen;
		this.txnHistories = txnHistories;
	}

	/**
	 * Returns {@code true} if {@code handleTransaction} can continue after policy application; {@code false} otherwise.
	 */
	public boolean applyPolicyFor(TxnAccessor accessor, long submittingMember) {
		throw new AssertionError("Not implemented!");
	}
}
