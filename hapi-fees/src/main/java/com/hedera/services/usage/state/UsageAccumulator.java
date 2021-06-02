package com.hedera.services.usage.state;

import com.hedera.services.usage.SigUsage;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ACCOUNT_AMT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RECEIPT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_BODY_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_RECORD_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.INT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.RECEIPT_STORAGE_TIME_SEC;

/**
 * Accumulates an estimate of the resources used by a HAPI operation.
 *
 * Resources are used by three service providers:
 * <ol>
 * 	<li>The network for providing gossip, consensus, and short-term storage of e.g. receipts; and,</li>
 * 	<li>The node that submitted the transaction, for providing prechecks and client communication; and,</li>
 * 	<li>The service actually performed by the </li>
 * </ol>
 */
public class UsageAccumulator {
	private long bpt;
	private long bpr;
	private long sbpr;
	private long vpt;
	private long gas;
	/* For storage resources, we traditionally begin with a finer-grained
	* estimate in units of seconds rather than hours, since expiration
	* times are given in seconds since the (consensus) epoch. */
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

	public long getBpt() {
		return bpt;
	}

	public long getBpr() {
		return bpr;
	}

	public long getSbpr() {
		return sbpr;
	}

	public long getVpt() {
		return vpt;
	}

	public long getGas() {
		return gas;
	}

	public long getRbs() {
		return rbs;
	}

	public long getSbs() {
		return sbs;
	}

	public long getNetworkRbs() {
		return networkRbs;
	}

	/* Provider-scoped usage estimates (all provider usages */
}
