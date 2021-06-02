package com.hedera.services.usage.state;

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.SingletonEstimatorUtils;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ACCOUNT_AMT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RECEIPT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_BODY_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_RECORD_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.HRS_DIVISOR;
import static com.hederahashgraph.fee.FeeBuilder.INT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.RECEIPT_STORAGE_TIME_SEC;

/**
 * Accumulates an estimate of the resources used by a HAPI operation.
 *
 * Resources are consumed by three service providers,
 * <ol>
 * 	<li>The network when providing gossip, consensus, and short-term storage of receipts.</li>
 * 	<li>The node when communicating with the client, performing prechecks, and submitting to the network.</li>
 * 	<li>The network when performing the logical service itself.</li>
 * </ol>
 *
 * The key fact is that the estimated resource usage for all three service providers
 * is a pure function of the <b>same</b> base usage estimates, for eight types
 * of resources:
 * <ol>
 *     <li>Network capacity needed to submit an operation to the network.
 *     Units are <tt>bpt</tt> (“bytes per transaction”).</li>
 *     <li>Network capacity needed to return information from memory in response to an operation.
 *     Units are <tt>bpt</tt> (“bytes per transaction”).</li>
 *     <li>Network capacity needed to return information from disk in response to an operation.
 *     Units are <tt>sbpr</tt> (“storage bytes per response”).</li>
 *     <li>RAM needed to persist an operation’s effects on consensus state, for as long as such effects are visible.
 *     Units are <tt>rbh</tt> (“RAM byte-hours”).</li>
 *     <li>Disk space needed to persist the operation’s effect on consensus state, for as long as such effects are visible.
 *     Units are sbh (“storage byte-hours”).</li>
 *     <li>Computation needed to verify a Ed25519 cryptographic signature.
 *     Units are <tt>vpt</tt> (“verifications per transaction”).</li>
 *     <li>Computation needed for incremental execution of a Solidity smart contract.
 *     Units are <tt>gas</tt>.</li>
 * </ol>
 */
public class UsageAccumulator {
	private long bpt;
	private long bpr;
	private long sbpr;
	private long vpt;
	private long gas;
	/* For storage resources, we use a finer-grained estimate in
	* units of seconds rather than hours, since expiration times
	* are given in seconds since the (consensus) epoch. */
	private long rbs;
	private long sbs;
	private long networkRbs;

	public void resetForTransaction(int memoBytes, int numTransfers, SigUsage sigUsage) {
		gas = sbs = sbpr = 0;

		bpr = INT_SIZE;
		vpt = sigUsage.numSigs();
		bpt = BASIC_TX_BODY_SIZE + memoBytes + sigUsage.sigsSize();
		rbs = RECEIPT_STORAGE_TIME_SEC * (BASIC_TX_RECORD_SIZE + memoBytes + BASIC_ACCOUNT_AMT_SIZE * numTransfers);

		networkRbs = RECEIPT_STORAGE_TIME_SEC * BASIC_RECEIPT_SIZE;
	}

	/* Resource accumulator methods */

	public void addBpt(long amount) {
		bpt += amount;
	}

	public void addBpr(long amount) {
		bpr += amount;
	}

	public void addSbpr(long amount) {
		sbpr += amount;
	}

	public void addVpt(long amount) {
		vpt += amount;
	}

	public void addGas(long amount) {
		gas += amount;
	}

	public void addRbs(long amount) {
		rbs += amount;
	}

	public void addSbs(long amount) {
		sbs += amount;
	}

	public void addNetworkRbs(long amount) {
		networkRbs += amount;
	}

	/* Provider-scoped usage estimates (pure functions of the total resource usage) */

	/* -- NETWORK -- */
	public long getNetworkBpt() {
		return bpt;
	}

	public long getNetworkBpr() {
		throw new AssertionError("Not implemented!");
	}

	public long getNetworkSbpr() {
		throw new AssertionError("Not implemented!");
	}

	public long getNetworkVpt() {
		return vpt;
	}

	public long getNetworkGas() {
		throw new AssertionError("Not implemented!");
	}

	public long getNetworkRbh() {
		return ESTIMATOR_UTILS.nonDegenerateDiv(networkRbs, HRS_DIVISOR);
	}

	public long getNetworkSbh() {
		throw new AssertionError("Not implemented!");
	}

	/* -- NODE -- */
	public long getNodeBpt() {
		throw new AssertionError("Not implemented!");
	}

	public long getNodeBpr() {
		throw new AssertionError("Not implemented!");
	}

	public long getNodeSbpr() {
		throw new AssertionError("Not implemented!");
	}

	public long getNodeVpt() {
		throw new AssertionError("Not implemented!");
	}

	public long getNodeGas() {
		throw new AssertionError("Not implemented!");
	}

	public long getNodeRbh() {
		throw new AssertionError("Not implemented!");
	}

	public long getNodeSbh() {
		throw new AssertionError("Not implemented!");
	}

	/* -- SERVICE -- */
	public long getServiceBpt() {
		throw new AssertionError("Not implemented!");
	}

	public long getServiceBpr() {
		throw new AssertionError("Not implemented!");
	}

	public long getServiceSbpr() {
		throw new AssertionError("Not implemented!");
	}

	public long getServiceVpt() {
		throw new AssertionError("Not implemented!");
	}

	public long getServiceGas() {
		throw new AssertionError("Not implemented!");
	}

	public long getServiceRbh() {
		throw new AssertionError("Not implemented!");
	}

	public long getServiceSbh() {
		throw new AssertionError("Not implemented!");
	}

	/* Helpers for test coverage */
	long getBpt() {
		return bpt;
	}

	long getBpr() {
		return bpr;
	}

	long getSbpr() {
		return sbpr;
	}

	long getVpt() {
		return vpt;
	}

	long getGas() {
		return gas;
	}

	long getRbs() {
		return rbs;
	}

	long getSbs() {
		return sbs;
	}

	long getNetworkRbs() {
		return networkRbs;
	}
}
