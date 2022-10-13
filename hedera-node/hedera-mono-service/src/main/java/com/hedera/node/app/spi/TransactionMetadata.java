package com.hedera.node.app.spi;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.Transaction;

import java.util.Collections;
import java.util.List;

/**
 * Metadata collected when transactions are handled as part of "pre-handle". This happens with multiple background threads.
 * Any state read or computed as part of this pre-handle, including any errors, are captured in the TransactionMetadata.
 * This is then made available to the transaction during the "handle" phase as part of the HandleContext.
 */
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
