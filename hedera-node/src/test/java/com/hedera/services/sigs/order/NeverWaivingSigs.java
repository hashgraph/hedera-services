package com.hedera.services.sigs.order;

import com.hederahashgraph.api.proto.java.TransactionBody;

public enum NeverWaivingSigs implements SignatureWaivers {
	NEVER_WAIVING_SIGS;

	@Override
	public boolean isAppendFileWaclWaived(TransactionBody fileAppendTxn) {
		return false;
	}

	@Override
	public boolean isTargetFileWaclWaived(TransactionBody fileUpdateTxn) {
		return false;
	}

	@Override
	public boolean isNewFileWaclWaived(TransactionBody fileUpdateTxn) {
		return false;
	}

	@Override
	public boolean isTargetAccountKeyWaived(TransactionBody cryptoUpdateTxn) {
		return false;
	}

	@Override
	public boolean isNewAccountKeyWaived(TransactionBody cryptoUpdateTxn) {
		return false;
	}
}
