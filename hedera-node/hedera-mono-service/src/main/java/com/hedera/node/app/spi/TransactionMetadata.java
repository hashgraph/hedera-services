package com.hedera.node.app.spi;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.Transaction;

import java.util.Collections;
import java.util.List;

public class TransactionMetadata {
	private final boolean failed;
	private final Transaction tx;
	private final JKey payerSig;
	private final List<JKey> otherSigs;

	public TransactionMetadata(
			final Transaction tx,
			final boolean failed,
			final JKey payerSig,
			final List<JKey> otherSigs) {
		this.tx = tx;
		this.failed = failed;
		this.payerSig = payerSig;
		this.otherSigs = otherSigs;
	}

	public TransactionMetadata(
			final Transaction tx,
			final boolean failed,
			final JKey payerSig) {
		this(tx, failed, payerSig, Collections.emptyList());
	}

	public Transaction transaction() {
		return tx;
	}

	public boolean failed() {
		return failed;
	}

	public JKey getPayerSig() {
		return payerSig;
	}

	public List<JKey> getOthersSigs() {
		return otherSigs;
	}
}
