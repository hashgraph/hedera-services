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
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.legacy.crypto.SignatureStatusCode;
import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigStatusOrderResultFactory;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.utils.RationalizedSigMeta;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static com.hedera.services.legacy.crypto.SignatureStatusCode.SUCCESS;
import static com.hedera.services.sigs.PlatformSigOps.createEd25519PlatformSigsFrom;
import static com.hedera.services.sigs.factories.PlatformSigFactory.allVaryingMaterialEquals;
import static com.hedera.services.sigs.utils.StatusUtils.successFor;
import static com.swirlds.common.crypto.VerificationStatus.UNKNOWN;

public class Rationalization {
	private static final Logger log = LogManager.getLogger(Rationalization.class);

	public final static SigStatusOrderResultFactory IN_HANDLE_SUMMARY_FACTORY =
			new SigStatusOrderResultFactory(true);

	private final TxnAccessor txnAccessor;
	private final SyncVerifier syncVerifier;
	private final PubKeyToSigBytes pkToSigFn;
	private final HederaSigningOrder keyOrderer;
	private final TxnScopedPlatformSigFactory sigFactory;

	private JKey reqPayerSig = null;
	private List<JKey> reqOthersSigs = null;
	private List<TransactionSignature> txnSigs;
	private SigningOrderResult<SignatureStatus> lastOrderResult;

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

	public SignatureStatus execute() {
		boolean verifiedSync = false;
		SignatureStatus otherFailure = null;
		List<TransactionSignature> realPayerSigs = new ArrayList<>(), realOtherPartySigs = new ArrayList<>();

		final var payerStatus = expandIn(realPayerSigs, keyOrderer::keysForPayer);
		if (!SUCCESS.equals(payerStatus.getStatusCode())) {
			txnAccessor.setSigMeta(RationalizedSigMeta.noneAvailable());
			return payerStatus;
		}
		reqPayerSig = lastOrderResult.getPayerKey();

		final var otherPartiesStatus = expandIn(realOtherPartySigs, keyOrderer::keysForOtherParties);
		if (!SUCCESS.equals(otherPartiesStatus.getStatusCode())) {
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

		if (otherFailure != null) {
			return otherFailure;
		} else {
			return verifiedSync ? syncSuccess() : asyncSuccess();
		}
	}

	private void makeRationalizedMetaAccessible() {
		if (reqOthersSigs == null) {
			txnAccessor.setSigMeta(RationalizedSigMeta.forPayerOnly(reqPayerSig, txnSigs));
		} else {
			txnAccessor.setSigMeta(RationalizedSigMeta.forPayerAndOthers(reqPayerSig, reqOthersSigs, txnSigs));
		}
	}

	private List<TransactionSignature> rationalize(List<TransactionSignature> realSigs, int startingAt) {
		try {
			var candidateSigs = txnSigs.subList(startingAt, startingAt + realSigs.size());
			if (allVaryingMaterialEquals(candidateSigs, realSigs) && allStatusesAreKnown(candidateSigs)) {
				return candidateSigs;
			}
		} catch (IndexOutOfBoundsException ignore) { }
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

	private SignatureStatus expandIn(
			List<TransactionSignature> target,
			BiFunction<TransactionBody, SigStatusOrderResultFactory, SigningOrderResult<SignatureStatus>> keysFn
	) {
		lastOrderResult = keysFn.apply(txnAccessor.getTxn(), IN_HANDLE_SUMMARY_FACTORY);
		if (lastOrderResult.hasErrorReport()) {
			return lastOrderResult.getErrorReport();
		}
		final var creation = createEd25519PlatformSigsFrom(lastOrderResult.getOrderedKeys(), pkToSigFn, sigFactory);
		if (creation.hasFailed()) {
			return creation.asSignatureStatus(true, txnAccessor.getTxnId());
		}
		target.addAll(creation.getPlatformSigs());
		return successFor(true, txnAccessor);
	}

	private SignatureStatus syncSuccess() {
		return success(SignatureStatusCode.SUCCESS_VERIFY_SYNC);
	}

	private SignatureStatus asyncSuccess() {
		return success(SignatureStatusCode.SUCCESS_VERIFY_ASYNC);
	}

	private SignatureStatus success(SignatureStatusCode code) {
		return new SignatureStatus(
				code, ResponseCodeEnum.OK,
				true, txnAccessor.getTxnId(),
				null, null, null, null);
	}
}
