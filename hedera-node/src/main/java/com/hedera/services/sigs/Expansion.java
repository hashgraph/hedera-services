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

import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.order.LinkedRefs;
import com.hedera.services.sigs.order.SigRequirements;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.order.SigningOrderResultFactory;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.sigs.PlatformSigOps.createCryptoSigsFrom;
import static com.hedera.services.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

class Expansion {
	private static final Logger log = LogManager.getLogger(Expansion.class);

	private final PubKeyToSigBytes pkToSigFn;
	private final SigRequirements keyOrderer;
	private final PlatformTxnAccessor txnAccessor;
	private final TxnScopedPlatformSigFactory sigFactory;

	private final LinkedRefs linkedRefs =  new LinkedRefs();

	public Expansion(
			final PlatformTxnAccessor txnAccessor,
			final SigRequirements keyOrderer,
			final PubKeyToSigBytes pkToSigFn,
			final TxnScopedPlatformSigFactory sigFactory
	) {
		this.txnAccessor = txnAccessor;
		this.sigFactory = sigFactory;
		this.keyOrderer = keyOrderer;
		this.pkToSigFn = pkToSigFn;
	}

	public ResponseCodeEnum execute() {
		log.debug("Expanding crypto sigs from Hedera sigs for txn {}...", txnAccessor::getSignedTxnWrapper);
		final var payerStatus = expand(pkToSigFn, keyOrderer::keysForPayer);
		txnAccessor.setLinkedRefs(linkedRefs);
		if (payerStatus != OK) {
			if (log.isDebugEnabled()) {
				log.debug(
						"Failed expanding Hedera payer sigs for txn {}: {}", txnAccessor.getTxnId(), payerStatus);
			}
			return payerStatus;
		}
		final var otherStatus = expand(pkToSigFn, keyOrderer::keysForOtherParties);
		if (otherStatus != OK) {
			if (log.isDebugEnabled()) {
				log.debug(
						"Failed expanding other Hedera sigs for txn {}: {}", txnAccessor.getTxnId(), otherStatus);
			}
			return otherStatus;
		}

		if (pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()) {
			pkToSigFn.forEachUnusedSigWithFullPrefix((type, pubKey, sig) ->
					txnAccessor.getPlatformTxn().add(sigFactory.signAppropriately(type, pubKey, sig)));
		}

		return OK;
	}

	private ResponseCodeEnum expand(final PubKeyToSigBytes pkToSigFn, final SigReqsFunction sigReqsFn) {
		var orderResult = sigReqsFn.apply(
				txnAccessor.getTxn(), CODE_ORDER_RESULT_FACTORY, linkedRefs);
		if (orderResult.hasErrorReport()) {
			return orderResult.getErrorReport();
		}

		final var creationResult = createCryptoSigsFrom(orderResult.getOrderedKeys(), pkToSigFn, sigFactory);
		if (!creationResult.hasFailed()) {
			txnAccessor.getPlatformTxn().addAll(creationResult.getPlatformSigs().toArray(new TransactionSignature[0]));
		}
		/* Ignore sig creation failures. */
		return OK;
	}

	interface SigReqsFunction {
		SigningOrderResult<ResponseCodeEnum> apply(
				TransactionBody txn,
				SigningOrderResultFactory<ResponseCodeEnum> factory,
				LinkedRefs linkedRefs);
	}
}
