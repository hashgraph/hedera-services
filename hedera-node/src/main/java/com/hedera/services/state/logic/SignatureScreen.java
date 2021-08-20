package com.hedera.services.state.logic;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.Rationalization;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.crypto.TransactionSignature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiPredicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class SignatureScreen {
	private static final Logger log = LogManager.getLogger(SignatureScreen.class);

	private final Rationalization rationalization;
	private final PayerSigValidity payerSigValidity;
	private final MiscSpeedometers speedometers;
	private final TransactionContext txnCtx;
	private final NetworkCtxManager networkCtxManager;
	private final BiPredicate<JKey, TransactionSignature> validityTest;

	public SignatureScreen(
			Rationalization rationalization,
			PayerSigValidity payerSigValidity,
			TransactionContext txnCtx,
			NetworkCtxManager networkCtxManager,
			MiscSpeedometers speedometers,
			BiPredicate<JKey, TransactionSignature> validityTest
	) {
		this.txnCtx = txnCtx;
		this.validityTest = validityTest;
		this.speedometers = speedometers;
		this.rationalization = rationalization;
		this.payerSigValidity = payerSigValidity;
		this.networkCtxManager = networkCtxManager;
	}

	public ResponseCodeEnum applyTo(TxnAccessor accessor) {
		rationalization.performFor(accessor);

		final var sigStatus = rationalization.finalStatus();
		if (sigStatus == OK) {
			if (rationalization.usedSyncVerification()) {
				speedometers.cycleSyncVerifications();
			} else {
				speedometers.cycleAsyncVerifications();
			}
		}

		if (hasActivePayerSig(accessor)) {
			txnCtx.payerSigIsKnownActive();
			networkCtxManager.prepareForIncorporating(accessor);
		}

		return sigStatus;
	}

	private boolean hasActivePayerSig(TxnAccessor accessor) {
		try {
			return payerSigValidity.test(accessor, validityTest);
		} catch (Exception unknown) {
			log.warn("Unhandled exception while testing payer sig activation", unknown);
		}
		return false;
	}
}
