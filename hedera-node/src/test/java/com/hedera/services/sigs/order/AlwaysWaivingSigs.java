package com.hedera.services.sigs.order;

import com.hederahashgraph.api.proto.java.TransactionBody;

public enum AlwaysWaivingSigs implements SignatureWaivers {
	ALWAYS_WAIVING_SIGS;

	@Override
	public boolean isAppendFileWaclWaived(TransactionBody fileAppendTxn) {
		return true;
	}

	@Override
	public boolean isTargetFileWaclWaived(TransactionBody fileUpdateTxn) {
		return true;
	}

	@Override
	public boolean isNewFileWaclWaived(TransactionBody fileUpdateTxn) {
		return true;
	}

	@Override
	public boolean isTargetAccountKeyWaived(TransactionBody cryptoUpdateTxn) {
		return true;
	}

	@Override
	public boolean isNewAccountKeyWaived(TransactionBody cryptoUpdateTxn) {
		return true;
	}
}
