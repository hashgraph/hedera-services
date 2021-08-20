package com.hedera.services.state.logic;

import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class KeyActivationScreen {
	private final InHandleActivationHelper activationHelper;
	private final Predicate<ResponseCodeEnum> terminalSigStatusTest;
	private final BiPredicate<JKey, TransactionSignature> validityTest;

	public KeyActivationScreen(
			InHandleActivationHelper activationHelper,
			Predicate<ResponseCodeEnum> terminalSigStatusTest,
			BiPredicate<JKey, TransactionSignature> validityTest
	) {
		this.validityTest = validityTest;
		this.activationHelper = activationHelper;
		this.terminalSigStatusTest = terminalSigStatusTest;
	}

	public boolean reqKeysAreActiveGiven(ResponseCodeEnum sigStatus) {
		throw new AssertionError("Not implemented");
	}
}
