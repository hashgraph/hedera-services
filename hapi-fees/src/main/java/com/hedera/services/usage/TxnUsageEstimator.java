package com.hedera.services.usage;

import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;

public abstract class TxnUsageEstimator<T extends TxnUsageEstimator<T>> {
	static int SIG_MAP = 1;
	static int TXN_BODY = 1 << 1;
	static int NUM_PAYER_KEYS = 1 << 2;

	static final int REQUIRED_FIELDS_COUNT = 3;
	static final int ALL_SET = (1 << REQUIRED_FIELDS_COUNT) - 1;

	private int known = 0;
	private int numPayerKeys = 0;
	private long bpt, vpt, rbh, sbh, gas, tv, networkRbh;
	private SignatureMap sigMap;
	private TransactionBody txn;

	private final EstimatorUtils utils;

	protected TxnUsageEstimator(EstimatorUtils utils) {
		this.utils = utils;
	}

	abstract protected T self();

	public T withTxn(TransactionBody txn) {
		known |= TXN_BODY;
		this.txn = txn;
		return self();
	}

	public T withSigMap(SignatureMap sigMap) {
		known |= SIG_MAP;
		this.sigMap = sigMap;
		return self();
	}

	public T withNumPayerKeys(int numPayerKeys) {
		this.numPayerKeys = numPayerKeys;
		known |= NUM_PAYER_KEYS;
		return self();
	}

	public FeeData get() {
		throwIfNotAllSet();

		var sigUsage = new SigUsage(sigMap.getSigPairCount(), sigMap.getSerializedSize(), numPayerKeys);

		var usage = utils.newBaseEstimate(txn, sigUsage);
		customize(usage);

		return utils.withDefaultPartitioning(usage.build(), networkRbh, numPayerKeys);
	}

	private void customize(FeeComponents.Builder usage) {
		usage.setBpt(usage.getBpt() + bpt)
				.setVpt(usage.getVpt() + vpt)
				.setRbh(usage.getRbh() + rbh)
				.setSbh(usage.getSbh() + sbh)
				.setGas(usage.getGas() + gas)
				.setTv(usage.getTv() + tv);
		this.networkRbh += utils.baseNetworkRbh();
	}

	protected T plusBpt(long bpt) {
		this.bpt += bpt;
		return self();
	}

	protected T plusVpt(long vpt) {
		this.vpt += vpt;
		return self();
	}

	protected T plusRbh(long rbh) {
		this.rbh += rbh;
		return self();
	}

	protected T plusSbh(long sbh) {
		this.sbh += sbh;
		return self();
	}

	protected T plusGas(long gas) {
		this.gas += gas;
		return self();
	}

	protected T plusTv(long tv) {
		this.tv += tv;
		return self();
	}

	protected T plusNetworkRbh(long networkRbh) {
		this.networkRbh += networkRbh;
		return self();
	}

	private void throwIfNotAllSet() {
		if (known != ALL_SET) {
			throw new IllegalStateException(
					String.format("Not all required fields are available - mask is %d not %d", known, ALL_SET));
		}
	}
}
