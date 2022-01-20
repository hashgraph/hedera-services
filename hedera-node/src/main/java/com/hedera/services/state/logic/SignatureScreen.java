package com.hedera.services.state.logic;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.context.TransactionContext;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.Rationalization;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.crypto.TransactionSignature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.BiPredicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

@Singleton
public class SignatureScreen {
	private static final Logger log = LogManager.getLogger(SignatureScreen.class);

	private final Rationalization rationalization;
	private final PayerSigValidity payerSigValidity;
	private final MiscSpeedometers speedometers;
	private final TransactionContext txnCtx;
	private final BiPredicate<JKey, TransactionSignature> validityTest;

	@Inject
	public SignatureScreen(
			Rationalization rationalization,
			PayerSigValidity payerSigValidity,
			TransactionContext txnCtx,
			MiscSpeedometers speedometers,
			BiPredicate<JKey, TransactionSignature> validityTest
	) {
		this.txnCtx = txnCtx;
		this.validityTest = validityTest;
		this.speedometers = speedometers;
		this.rationalization = rationalization;
		this.payerSigValidity = payerSigValidity;
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
