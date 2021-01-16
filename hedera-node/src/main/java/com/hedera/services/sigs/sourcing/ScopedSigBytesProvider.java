package com.hedera.services.sigs.sourcing;

import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.Transaction;

public class ScopedSigBytesProvider implements PubKeyToSigBytesProvider {
	final PubKeyToSigBytes delegate;

	public ScopedSigBytesProvider(SignedTxnAccessor accessor) {
		switch (accessor.getFunction()) {
			case ScheduleSign:
				var scheduleSignSigMap = accessor.getTxn().getScheduleSign().getSigMap();
				delegate = new ScheduledPubKeyToSigBytes(
						new SigMapPubKeyToSigBytes(accessor.getSigMap()),
						new SigMapPubKeyToSigBytes(scheduleSignSigMap));
				break;
			case ScheduleCreate:
				var scheduleCreateSigMap = accessor.getTxn().getScheduleCreate().getSigMap();
				delegate = new ScheduledPubKeyToSigBytes(
						new SigMapPubKeyToSigBytes(accessor.getSigMap()),
						new SigMapPubKeyToSigBytes(scheduleCreateSigMap));
				break;
			default:
				delegate = new SigMapPubKeyToSigBytes(accessor.getSigMap());
		}
	}

	@Override
	public PubKeyToSigBytes payerSigBytesFor(Transaction ignore) {
		return delegate;
	}

	@Override
	public PubKeyToSigBytes otherPartiesSigBytesFor(Transaction ignore) {
		return delegate;
	}

	@Override
	public PubKeyToSigBytes allPartiesSigBytesFor(Transaction ignore) {
		return delegate;
	}
}
