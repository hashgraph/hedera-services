package com.hedera.services.state.logic;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;

public class KeyActivationScreen {
	private final TransactionContext txnCtx;
	private final InHandleActivationHelper activationHelper;
	private final Predicate<ResponseCodeEnum> terminalSigStatusTest;
	private final BiPredicate<JKey, TransactionSignature> validityTest;

	public KeyActivationScreen(
			TransactionContext txnCtx,
			InHandleActivationHelper activationHelper,
			Predicate<ResponseCodeEnum> terminalSigStatusTest,
			BiPredicate<JKey, TransactionSignature> validityTest
	) {
		this.txnCtx = txnCtx;
		this.validityTest = validityTest;
		this.activationHelper = activationHelper;
		this.terminalSigStatusTest = terminalSigStatusTest;
	}

	public boolean reqKeysAreActiveGiven(ResponseCodeEnum sigStatus) {
		if (terminalSigStatusTest.test(sigStatus)) {
			txnCtx.setStatus(sigStatus);
			return false;
		}
		if (!activationHelper.areOtherPartiesActive(validityTest)) {
			txnCtx.setStatus(INVALID_SIGNATURE);
			return false;
		}
		return true;
	}
}
