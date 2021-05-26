package com.hedera.services.txns.submission;

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

import com.hedera.services.context.CurrentPlatformStatus;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.queries.validation.QueryFeeCheck;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.tuple.Pair;

import java.util.EnumSet;
import java.util.Optional;

import static com.hedera.services.txns.submission.PresolvencyFlaws.WELL_KNOWN_FLAWS;
import static com.hedera.services.txns.submission.PresolvencyFlaws.responseForFlawed;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.swirlds.common.PlatformStatus.ACTIVE;

/**
 * Implements the appropriate stages of precheck for a transaction to be submitted to the
 * network, either a top-level transaction or a {@code CryptoTransfer} query payment.
 *
 * For more details, please see https://github.com/hashgraph/hedera-services/blob/master/docs/transaction-prechecks.md
 */
public class TransactionPrecheck {
	private final QueryFeeCheck queryFeeCheck;
	private final StagedPrechecks stagedPrechecks;
	private final CurrentPlatformStatus currentPlatformStatus;

	private static final EnumSet<Characteristic> TOP_LEVEL_CHARACTERISTICS =
			EnumSet.of(Characteristic.MUST_PASS_SYSTEM_SCREEN);
	private static final EnumSet<Characteristic> QUERY_PAYMENT_CHARACTERISTICS =
			EnumSet.of(Characteristic.MUST_BE_CRYPTO_TRANSFER, Characteristic.MUST_BE_SOLVENT_FOR_SVC_FEES);

	public TransactionPrecheck(
			QueryFeeCheck queryFeeCheck,
			StagedPrechecks stagedPrechecks,
			CurrentPlatformStatus currentPlatformStatus
	) {
		this.queryFeeCheck = queryFeeCheck;
		this.stagedPrechecks = stagedPrechecks;
		this.currentPlatformStatus = currentPlatformStatus;
	}

	public Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> performForTopLevel(Transaction signedTxn) {
		return performance(signedTxn, TOP_LEVEL_CHARACTERISTICS);
	}

	public Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> performForQueryPayment(Transaction signedTxn) {
		final var prelim = performance(signedTxn, QUERY_PAYMENT_CHARACTERISTICS);
		final var prelimOutcome = prelim.getLeft();
		if (prelimOutcome.getValidity() != OK || prelim.getRight().isEmpty()) {
			return prelim;
		}

		final var xferTxn = prelim.getRight().get().getTxn();
		final var xfersStatus = queryFeeCheck.validateQueryPaymentTransfers(xferTxn);
		if (xfersStatus != OK) {
			return failureFor(new TxnValidityAndFeeReq(xfersStatus, prelimOutcome.getRequiredFee()));
		}
		return prelim;
	}

	private Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> performance(
			Transaction signedTxn,
			EnumSet<Characteristic> characteristics
	) {
		if (currentPlatformStatus.get() != ACTIVE) {
			return WELL_KNOWN_FLAWS.get(PLATFORM_NOT_ACTIVE);
		}

		final var structuralAssessment = stagedPrechecks.assessStructure(signedTxn);
		if (structuralAssessment.getLeft().getValidity() != OK || structuralAssessment.getRight().isEmpty()) {
			return structuralAssessment;
		}

		/* We can now safely proceed to the next four stages of precheck. */
		final var accessor = structuralAssessment.getRight().get();
		final var txn = accessor.getTxn();

		final var syntaxStatus = stagedPrechecks.validateSyntax(txn);
		if (syntaxStatus != OK) {
			return responseForFlawed(syntaxStatus);
		}

		final var semanticStatus = checkSemantics(accessor.getFunction(), txn, characteristics);
		if (semanticStatus != OK) {
			return responseForFlawed(semanticStatus);
		}

		final var solvencyStatus = characteristics.contains(Characteristic.MUST_BE_SOLVENT_FOR_SVC_FEES)
				? stagedPrechecks.assessSolvencyWithSvcFees(accessor)
				: stagedPrechecks.assessSolvencySansSvcFees(accessor);
		if (solvencyStatus.getValidity() != OK) {
			return failureFor(solvencyStatus);
		}

		if (characteristics.contains(Characteristic.MUST_PASS_SYSTEM_SCREEN)) {
			final var systemStatus = stagedPrechecks.systemScreen(accessor);
			if (systemStatus != OK) {
				return failureFor(new TxnValidityAndFeeReq(systemStatus, solvencyStatus.getRequiredFee()));
			}
		}

		return Pair.of(solvencyStatus, Optional.of(accessor));
	}

	private Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> failureFor(TxnValidityAndFeeReq feeReqStatus) {
		return Pair.of(feeReqStatus, Optional.empty());
	}

	private ResponseCodeEnum checkSemantics(
			HederaFunctionality function,
			TransactionBody txn,
			EnumSet<Characteristic> characteristics
	) {
		return characteristics.contains(Characteristic.MUST_BE_CRYPTO_TRANSFER)
				? stagedPrechecks.validateSemantics(CryptoTransfer, txn, INSUFFICIENT_TX_FEE)
				: stagedPrechecks.validateSemantics(function, txn, NOT_SUPPORTED);
	}

	private enum Characteristic {
		MUST_BE_CRYPTO_TRANSFER,
		MUST_PASS_SYSTEM_SCREEN,
		MUST_BE_SOLVENT_FOR_SVC_FEES
	}
}
