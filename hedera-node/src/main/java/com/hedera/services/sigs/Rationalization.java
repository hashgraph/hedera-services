package com.hedera.services.sigs;

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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.order.CodeOrderResultFactory;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.utils.RationalizedSigMeta;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static com.hedera.services.sigs.PlatformSigOps.createEd25519PlatformSigsFrom;
import static com.hedera.services.sigs.factories.PlatformSigFactory.allVaryingMaterialEquals;
import static com.hedera.services.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.swirlds.common.crypto.VerificationStatus.UNKNOWN;

public class Rationalization {
	private final TxnAccessor txnAccessor;
	private final SyncVerifier syncVerifier;
	private final PubKeyToSigBytes pkToSigFn;
	private final HederaSigningOrder keyOrderer;
	private final TxnScopedPlatformSigFactory sigFactory;

	private JKey reqPayerSig = null;
	private boolean verifiedSync = false;
	private List<JKey> reqOthersSigs = null;
	private ResponseCodeEnum finalStatus;
	private List<TransactionSignature> txnSigs;
	private SigningOrderResult<ResponseCodeEnum> lastOrderResult;

	public Rationalization(
			TxnAccessor txnAccessor,
			SyncVerifier syncVerifier,
			HederaSigningOrder keyOrderer,
			PubKeyToSigBytes pkToSigFn,
			TxnScopedPlatformSigFactory sigFactory
	) {
		this.pkToSigFn = pkToSigFn;
		this.keyOrderer = keyOrderer;
		this.sigFactory = sigFactory;
		this.txnAccessor = txnAccessor;
		this.syncVerifier = syncVerifier;

		txnSigs = txnAccessor.getPlatformTxn().getSignatures();
	}

	public ResponseCodeEnum finalStatus() {
		return finalStatus;
	}

	public boolean usedSyncVerification() {
		return verifiedSync;
	}

	public void execute() {
		ResponseCodeEnum otherFailure = null;
		List<TransactionSignature> realPayerSigs = new ArrayList<>(), realOtherPartySigs = new ArrayList<>();

		final var payerStatus = expandIn(realPayerSigs, keyOrderer::keysForPayer);
		if (payerStatus != OK) {
			txnAccessor.setSigMeta(RationalizedSigMeta.noneAvailable());
			finalStatus = payerStatus;
			return;
		}
		reqPayerSig = lastOrderResult.getPayerKey();

		final var otherPartiesStatus = expandIn(realOtherPartySigs, keyOrderer::keysForOtherParties);
		if (otherPartiesStatus != OK) {
			otherFailure = otherPartiesStatus;
		} else {
			reqOthersSigs = lastOrderResult.getOrderedKeys();
		}

		final var rationalizedPayerSigs = rationalize(realPayerSigs, 0);
		final var rationalizedOtherPartySigs = rationalize(realOtherPartySigs, realPayerSigs.size());
		if (rationalizedPayerSigs == realPayerSigs || rationalizedOtherPartySigs == realOtherPartySigs) {
			txnSigs = new ArrayList<>();
			txnSigs.addAll(rationalizedPayerSigs);
			txnSigs.addAll(rationalizedOtherPartySigs);
			verifiedSync = true;
		}

		makeRationalizedMetaAccessible();

		finalStatus = (otherFailure != null) ? otherFailure : OK;
	}

	private void makeRationalizedMetaAccessible() {
		if (reqOthersSigs == null) {
			txnAccessor.setSigMeta(RationalizedSigMeta.forPayerOnly(reqPayerSig, txnSigs));
		} else {
			txnAccessor.setSigMeta(RationalizedSigMeta.forPayerAndOthers(reqPayerSig, reqOthersSigs, txnSigs));
		}
	}

	private List<TransactionSignature> rationalize(List<TransactionSignature> realSigs, int startingAt) {
		final var maxSubListEnd = txnSigs.size();
		final var requestedSubListEnd = startingAt + realSigs.size();
		if (requestedSubListEnd <= maxSubListEnd) {
			var candidateSigs = txnSigs.subList(startingAt, startingAt + realSigs.size());
			if (allVaryingMaterialEquals(candidateSigs, realSigs) && allStatusesAreKnown(candidateSigs)) {
				return candidateSigs;
			}
		}
		syncVerifier.verifySync(realSigs);
		return realSigs;
	}

	private boolean allStatusesAreKnown(List<TransactionSignature> sigs) {
		for (final var sig : sigs) {
			if (sig.getSignatureStatus() == UNKNOWN) {
				return false;
			}
		}
		return true;
	}

	private ResponseCodeEnum expandIn(
			List<TransactionSignature> target,
			BiFunction<TransactionBody, CodeOrderResultFactory, SigningOrderResult<ResponseCodeEnum>> keysFn
	) {
		lastOrderResult = keysFn.apply(txnAccessor.getTxn(), CODE_ORDER_RESULT_FACTORY);
		if (lastOrderResult.hasErrorReport()) {
			return lastOrderResult.getErrorReport();
		}
		final var creation = createEd25519PlatformSigsFrom(lastOrderResult.getOrderedKeys(), pkToSigFn, sigFactory);
		if (creation.hasFailed()) {
			return creation.asCode();
		}
		target.addAll(creation.getPlatformSigs());
		return OK;
	}
}
