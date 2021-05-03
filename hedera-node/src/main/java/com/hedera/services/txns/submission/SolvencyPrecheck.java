package com.hedera.services.txns.submission;

import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.FeeExemptions;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hedera.services.sigs.verification.PrecheckVerifier;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.fee.FeeObject;
import com.swirlds.fcmap.FCMap;

import java.util.function.Supplier;

import static com.hedera.services.txns.validation.PureValidation.queryableAccountStatus;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE_TYPE_MISMATCHING_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;

/**
 * Determines if the payer account set in the {@code TransactionID} is expected to be both
 * willing and able to pay the transaction fees.
 *
 * For more details, please see https://github.com/hashgraph/hedera-services/blob/master/docs/transaction-prechecks.md
 */
public class SolvencyPrecheck {
	private static final TxnValidityAndFeeReq VERIFIED_EXEMPT = new TxnValidityAndFeeReq(OK);
	private static final TxnValidityAndFeeReq LOST_PAYER_EXPIRATION_RACE = new TxnValidityAndFeeReq(FAIL_FEE);

	private final FeeExemptions feeExemptions;
	private final FeeCalculator feeCalculator;
	private final PrecheckVerifier precheckVerifier;
	private final Supplier<StateView> stateView;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	public SolvencyPrecheck(
			FeeExemptions feeExemptions,
			FeeCalculator feeCalculator,
			PrecheckVerifier precheckVerifier,
			Supplier<StateView> stateView,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts
	) {
		this.accounts = accounts;
		this.stateView = stateView;
		this.feeExemptions = feeExemptions;
		this.feeCalculator = feeCalculator;
		this.precheckVerifier = precheckVerifier;
	}

	TxnValidityAndFeeReq assess(SignedTxnAccessor accessor) {
		final var payerStatus = queryableAccountStatus(accessor.getPayer(), accounts.get());
		if (payerStatus != OK) {
			return new TxnValidityAndFeeReq(PAYER_ACCOUNT_NOT_FOUND);
		}

		final var sigsStatus = checkSigs(accessor);
		if (sigsStatus != OK) {
			return new TxnValidityAndFeeReq(sigsStatus);
		}

		if (feeExemptions.hasExemptPayer(accessor)) {
			return VERIFIED_EXEMPT;
		}

		return solvencyOfVerifiedPayer(accessor);
	}

	private TxnValidityAndFeeReq solvencyOfVerifiedPayer(SignedTxnAccessor accessor) {
		final var payerId = MerkleEntityId.fromAccountId(accessor.getPayer());
		final var payerAccount = accounts.get().get(payerId);

		try {
			final var now = accessor.getTxnId().getTransactionValidStart();
			final var payerKey = payerAccount.getKey();
			final var estimatedFees = feeCalculator.estimateFee(accessor, payerKey, stateView.get(), now);
			final var estimatedReqFee = totalOf(estimatedFees);

			if (accessor.getTxn().getTransactionFee() < estimatedReqFee) {
				return new TxnValidityAndFeeReq(INSUFFICIENT_TX_FEE, estimatedReqFee);
			}

			final var estimatedAdj = Math.min(0L, feeCalculator.estimatedNonFeePayerAdjustments(accessor, now));
			final var requiredPayerBalance = estimatedReqFee - estimatedAdj;
			if (payerAccount.getBalance() < requiredPayerBalance) {
				return new TxnValidityAndFeeReq(INSUFFICIENT_PAYER_BALANCE, estimatedReqFee);
			}

			return new TxnValidityAndFeeReq(OK, estimatedReqFee);
		} catch (Exception race) {
			return LOST_PAYER_EXPIRATION_RACE;
		}
	}

	private long totalOf(FeeObject fees) {
		return fees.getServiceFee() + fees.getNodeFee() + fees.getNetworkFee();
	}

	private ResponseCodeEnum checkSigs(SignedTxnAccessor accessor) {
		try {
			return precheckVerifier.hasNecessarySignatures(accessor) ? OK : INVALID_SIGNATURE;
		} catch (KeyPrefixMismatchException ignore) {
			return KEY_PREFIX_MISMATCH;
		} catch (InvalidAccountIDException ignore) {
			return INVALID_ACCOUNT_ID;
		} catch (Exception ignore) {
			return INVALID_SIGNATURE;
		}
	}
}
