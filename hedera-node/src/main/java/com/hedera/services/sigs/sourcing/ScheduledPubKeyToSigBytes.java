package com.hedera.services.sigs.sourcing;

public class ScheduledPubKeyToSigBytes implements PubKeyToSigBytes {
	private final PubKeyToSigBytes scopedDelegate;
	private final PubKeyToSigBytes scheduledDelegate;

	public ScheduledPubKeyToSigBytes(
			PubKeyToSigBytes scopedDelegate,
			PubKeyToSigBytes scheduledDelegate
	) {
		this.scopedDelegate = scopedDelegate;
		this.scheduledDelegate = scheduledDelegate;
	}

	@Override
	public byte[] sigBytesFor(byte[] pubKey) throws Exception {
		return scopedDelegate.sigBytesFor(pubKey);
	}

	@Override
	public byte[] sigBytesForScheduled(byte[] pubKey) {
		try {
			return scheduledDelegate.sigBytesFor(pubKey);
		} catch (Exception ignore) {
			/* Since not all required keys might have been used to sign
			the scheduled transaction in this scope, it's permissible
			to have a prefix that's ambiguous for one of them. */
			return SigMapPubKeyToSigBytes.EMPTY_SIG;
		}
	}
}
