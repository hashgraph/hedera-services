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

import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigStatusOrderResultFactory;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiFunction;
import java.util.function.Function;

import static com.hedera.services.legacy.crypto.SignatureStatusCode.SUCCESS;
import static com.hedera.services.sigs.PlatformSigOps.createEd25519PlatformSigsFrom;
import static com.hedera.services.sigs.utils.StatusUtils.successFor;

class Expansion {
	private static final Logger log = LogManager.getLogger(Expansion.class);

	private final PubKeyToSigBytes pkToSigFn;
	private final HederaSigningOrder keyOrderer;
	private final PlatformTxnAccessor txnAccessor;
	private final TxnScopedPlatformSigFactory sigFactory;

	public Expansion(
			PlatformTxnAccessor txnAccessor,
			HederaSigningOrder keyOrderer,
			PubKeyToSigBytes pkToSigFn,
			TxnScopedPlatformSigFactory sigFactory
	) {
		this.txnAccessor = txnAccessor;
		this.sigFactory = sigFactory;
		this.keyOrderer = keyOrderer;
		this.pkToSigFn = pkToSigFn;
	}

	public SignatureStatus execute() {
		log.debug("Expanding crypto sigs from Hedera sigs for txn {}...", txnAccessor::getSignedTxn4Log);
		var payerStatus = expand(pkToSigFn, keyOrderer::keysForPayer);
		if ( SUCCESS != payerStatus.getStatusCode() ) {
			if (log.isDebugEnabled()) {
				log.debug(
						"Failed expanding Hedera payer sigs for txn {}: {}",
						txnAccessor.getTxnId(),
						payerStatus);
			}
			return payerStatus;
		}
		var otherStatus = expand(pkToSigFn, keyOrderer::keysForOtherParties);
		if ( SUCCESS != otherStatus.getStatusCode() ) {
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
			PubKeyToSigBytes pkToSigFn,
			BiFunction<TransactionBody, SigStatusOrderResultFactory, SigningOrderResult<SignatureStatus>> keysFn
	) {
		var orderResult = keysFn.apply(txnAccessor.getTxn(), HederaToPlatformSigOps.PRE_HANDLE_SUMMARY_FACTORY);
		if (orderResult.hasErrorReport()) {
			return orderResult.getErrorReport();
		}

		var creationResult = createEd25519PlatformSigsFrom(orderResult.getOrderedKeys(), pkToSigFn, sigFactory);
		if (!creationResult.hasFailed()) {
			txnAccessor.getPlatformTxn().addAll(creationResult.getPlatformSigs().toArray(new TransactionSignature[0]));
		}
		/* Ignore sig creation failures. */
		return successFor(false, txnAccessor);
	}
}
