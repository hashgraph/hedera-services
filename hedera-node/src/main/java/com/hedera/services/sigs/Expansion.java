package com.hedera.services.sigs;

import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigStatusOrderResultFactory;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytesProvider;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.Transaction;
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

	private final PlatformTxnAccessor txnAccessor;
	private final HederaSigningOrder keyOrderer;
	private final PubKeyToSigBytesProvider sigsProvider;
	private final TxnScopedPlatformSigFactory sigFactory;

	public Expansion(
			PlatformTxnAccessor txnAccessor,
			HederaSigningOrder keyOrderer,
			PubKeyToSigBytesProvider sigsProvider,
			Function<SignedTxnAccessor, TxnScopedPlatformSigFactory> sigFactoryCreator
	) {
		this.txnAccessor = txnAccessor;
		this.keyOrderer = keyOrderer;
		this.sigsProvider = sigsProvider;

		sigFactory = sigFactoryCreator.apply(txnAccessor);
	}

	public SignatureStatus execute() {
		log.debug("Expanding crypto sigs from Hedera sigs for txn {}...", txnAccessor::getSignedTxn4Log);
		var payerStatus = expand(sigsProvider::payerSigBytesFor, keyOrderer::keysForPayer);
		if ( SUCCESS != payerStatus.getStatusCode() ) {
			if (log.isDebugEnabled()) {
				log.debug(
						"Failed expanding Hedera payer sigs for txn {}: {}",
						txnAccessor.getTxnId(),
						payerStatus);
			}
			return payerStatus;
		}
		var otherStatus = expand(sigsProvider::otherPartiesSigBytesFor, keyOrderer::keysForOtherParties);
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
			Function<Transaction, PubKeyToSigBytes> sigsFn,
			BiFunction<TransactionBody, SigStatusOrderResultFactory, SigningOrderResult<SignatureStatus>> keysFn
	) {
		var orderResult = keysFn.apply(txnAccessor.getTxn(), HederaToPlatformSigOps.PRE_HANDLE_SUMMARY_FACTORY);
		if (orderResult.hasErrorReport()) {
			return orderResult.getErrorReport();
		}

		var creationResult = createEd25519PlatformSigsFrom(
				orderResult.getOrderedKeys(), sigsFn.apply(txnAccessor.getBackwardCompatibleSignedTxn()), sigFactory);
		if (!creationResult.hasFailed()) {
			txnAccessor.getPlatformTxn().addAll(creationResult.getPlatformSigs().toArray(new TransactionSignature[0]));
		}
		/* Ignore sig creation failures. */
		return successFor(false, txnAccessor);
	}
}
