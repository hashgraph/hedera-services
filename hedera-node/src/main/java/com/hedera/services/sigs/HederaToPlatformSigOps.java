package com.hedera.services.sigs;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.sigs.factories.BodySigningSigFactory;
import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigStatusOrderResultFactory;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytesProvider;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.VerificationStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiFunction;
import java.util.function.Function;

import static com.hedera.services.sigs.PlatformSigOps.*;
import static com.hedera.services.sigs.utils.StatusUtils.successFor;
import static com.hedera.services.legacy.crypto.SignatureStatusCode.SUCCESS;

/**
 * Provides two operations that act in-place on the {@link Signature} list of a Swirlds
 * {@link com.swirlds.common.Transaction} whose contents are known to be a valid
 * Hedera gRPC {@link Transaction}.
 *
 * <p>These operations allow Hedera Services use the Swirlds Platform to efficiently
 * verify <i>many</i> of the cryptographic signatures in its gRPC transactions. (There
 * are still cases where Hedera Services does a single-threaded verification itself.)
 *
 * <p>The two operations happen, in order, for each platform txn added to the hashgraph,
 * and have roughly these behaviors:
 * <ol>
 *     <li> First, {@code expandIn} checks which Hedera keys must have active signatures
 *     for the wrapped gRPC txn to be valid; and creates the cryptographic signatures
 *     at the bases of the signing hierarchies for these keys. It then asks the Swirlds
 *     Platform to efficiently verify these cryptographic signatures, by setting them
 *     in the sigs list of the platform txn.
 *     </li> Next, {@code rationalizeIn} checks if the relevant Hedera keys have changed
 *     since the call to {@code expandIn}. If they have changed, it replays the logic
 *     from {@code expandIn} to update the sigs list of the platform txn. In any case,
 *     {@rationalizeIn} then uses synchronous verifications to ensure no sig in the list
 *     is left with an {@code UNKNOWN} verification status.
 * </ol>
 * The behavior on exceptional conditions varies a bit between {@code expandIn} and
 * {@code rationalizeIn}, and is given in detail below.
 *
 * @author Michael Tinker
 * @see JKey
 */
public class HederaToPlatformSigOps {
	private static final Logger log = LogManager.getLogger(HederaToPlatformSigOps.class);

	public final static SigStatusOrderResultFactory PRE_HANDLE_SUMMARY_FACTORY =
			new SigStatusOrderResultFactory(false);

	private HederaToPlatformSigOps(){
		throw new IllegalStateException("Utility Class");
	}

	/**
	 * Try to set the {@link Signature} list on the accessible platform txn to exactly
	 * the base-level signatures of the signing hierarchy for each Hedera
	 * {@link JKey} required to sign the wrapped gRPC txn.
	 * (Signatures for the payer always come first.)
	 *
	 * <p>Exceptional conditions are treated as follows:
	 * <ul>
	 *     <li>If an error occurs while determining the Hedera signing keys,
	 *     abort processing and return a {@link SignatureStatus} representing this
	 *     error.</li>
	 *     <li>If an error occurs while creating the platform {@link Signature}
	 *     objects for either the payer or the entities in non-payer roles, ignore
	 *     it silently. </li>
	 * </ul>
	 *
	 * @param txnAccessor the accessor for the platform txn.
	 * @param keyOrderer facility for listing Hedera keys required to sign the gRPC txn.
	 * @param sigsProvider source of crypto sigs for the simple keys in the Hedera key leaves.
	 * @return a representation of the outcome.
	 */
	public static SignatureStatus expandIn(
			PlatformTxnAccessor txnAccessor,
			HederaSigningOrder keyOrderer,
			PubKeyToSigBytesProvider sigsProvider
	) {
		txnAccessor.getPlatformTxn().clear();

		return new Expansion(txnAccessor, keyOrderer, sigsProvider).execute();
	}

	/**
	 * First, ensure the {@link Signature} list on the accessible platform txn contains
	 * exactly the base-level signatures of the signing hierarchy for each Hedera
	 * {@link JKey} required to sign the wrapped gRPC txn.
	 * Second, ensure the {@link VerificationStatus} for each of these base-level
	 * signatures is not {@code UNKNOWN}, performing a synchronous verification if
	 * necessary.
	 *
	 * <p>Exceptional conditions are treated as follows:
	 * <ul>
	 *     <li>If an error occurs while determining the Hedera signing keys,
	 *     abort processing and return a {@link SignatureStatus} representing this
	 *     error.</li>
	 *     <li>If an error occurs while creating the platform {@link Signature}
	 *     objects for either the payer or the entities in non-payer roles, abort
	 *     processing and return a {@link SignatureStatus} representing this error.</li>
	 * </ul>
	 *
	 * @param txnAccessor the accessor for the platform txn.
	 * @param syncVerifier facility for synchronously verifying a cryptographic signature.
	 * @param keyOrderer facility for listing Hedera keys required to sign the gRPC txn.
	 * @param sigsProvider source of crypto sigs for the simple keys in the Hedera key leaves.
	 * @return a representation of the outcome.
	 */
	public static SignatureStatus rationalizeIn(
			PlatformTxnAccessor txnAccessor,
			SyncVerifier syncVerifier,
			HederaSigningOrder keyOrderer,
			PubKeyToSigBytesProvider sigsProvider
	) {
		return new Rationalization(txnAccessor, syncVerifier, keyOrderer, sigsProvider).execute();
	}

	private static class Expansion {
		private final PlatformTxnAccessor txnAccessor;
		private final HederaSigningOrder keyOrderer;
		private final PubKeyToSigBytesProvider sigsProvider;
		private final TxnScopedPlatformSigFactory sigFactory;

		public Expansion(
				PlatformTxnAccessor txnAccessor,
				HederaSigningOrder keyOrderer,
				PubKeyToSigBytesProvider sigsProvider
		) {
			this.txnAccessor = txnAccessor;
			this.keyOrderer = keyOrderer;
			this.sigsProvider = sigsProvider;

			sigFactory = new BodySigningSigFactory(txnAccessor.getTxnBytes());
		}

		public SignatureStatus execute() {
			log.debug("Expanding crypto sigs from Hedera sigs for txn {}...", txnAccessor::getSignedTxn4Log);
			SignatureStatus payerStatus = expand(sigsProvider::payerSigBytesFor, keyOrderer::keysForPayer);
			if (!SUCCESS.name().equals( payerStatus.getStatusCode().name())) {
				if (log.isDebugEnabled()) {
					log.debug(
							"Failed expanding Hedera payer sigs for txn {}: {}",
							txnAccessor.getTxnId(),
							payerStatus);
				}
				return payerStatus;
			}
			SignatureStatus otherStatus = expand(sigsProvider::otherPartiesSigBytesFor, keyOrderer::keysForOtherParties);
			if (!SUCCESS.name().equals( otherStatus.getStatusCode().name())) {
				if (log.isDebugEnabled()) {
					log.debug(
							"Failed expanding other Hedera sigs for txn {}: {}",
							txnAccessor.getTxnId(),
							otherStatus);
				}
			}
			return otherStatus;
		}

		private SignatureStatus expand(
				Function<Transaction, PubKeyToSigBytes> sigsFn,
				BiFunction<TransactionBody, SigStatusOrderResultFactory, SigningOrderResult<SignatureStatus>> keysFn
		) {
			SigningOrderResult<SignatureStatus> orderResult =
					keysFn.apply(txnAccessor.getTxn(), PRE_HANDLE_SUMMARY_FACTORY);
			if (orderResult.hasErrorReport()) {
				return orderResult.getErrorReport();
			}

			PlatformSigsCreationResult creationResult = createEd25519PlatformSigsFrom(
					orderResult.getOrderedKeys(), sigsFn.apply(txnAccessor.getSignedTxn()), sigFactory);
			if (!creationResult.hasFailed()) {
				txnAccessor.getPlatformTxn().addAll(creationResult.getPlatformSigs().toArray(new Signature[0]));
			}
			/* Ignore sig creation failures. */
			return successFor(false, txnAccessor);
		}
	}
}
