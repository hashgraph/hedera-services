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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.swirlds.common.PlatformStatus.ACTIVE;

public class Precheck {
	private final SyntaxPrecheck syntaxPrecheck;
	private final SemanticPrecheck semanticPrecheck;
	private final StructuralPrecheck structuralPrecheck;
	private final CurrentPlatformStatus currentPlatformStatus;

	private static final EnumSet<PrecheckCharacteristics> TOP_LEVEL_CHARACTERISTICS =
			EnumSet.of(PrecheckCharacteristics.SHOULD_INCLUDE_SYSTEM_CHECKS);
	private static final EnumSet<PrecheckCharacteristics> QUERY_PAYMENT_CHARACTERISTICS =
			EnumSet.of(PrecheckCharacteristics.MUST_BE_CRYPTO_TRANSFER);

	public Precheck(
			SyntaxPrecheck syntaxPrecheck,
			SemanticPrecheck semanticPrecheck,
			StructuralPrecheck structuralPrecheck,
			CurrentPlatformStatus currentPlatformStatus
	) {
		this.syntaxPrecheck = syntaxPrecheck;
		this.semanticPrecheck = semanticPrecheck;
		this.structuralPrecheck = structuralPrecheck;
		this.currentPlatformStatus = currentPlatformStatus;
	}

	public Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> performForTopLevel(Transaction signedTxn) {
		return performance(signedTxn, TOP_LEVEL_CHARACTERISTICS);
	}

	public Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> performForQueryPayment(Transaction signedTxn) {
		return performance(signedTxn, QUERY_PAYMENT_CHARACTERISTICS);
	}

	private Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> performance(
			Transaction signedTxn,
			EnumSet<PrecheckCharacteristics> characteristics
	) {
		if (currentPlatformStatus.get() != ACTIVE) {
			return WELL_KNOWN_FLAWS.get(PLATFORM_NOT_ACTIVE);
		}

		/* Structure */
		final var structuralAssessment = structuralPrecheck.assess(signedTxn);
		if (structuralAssessment.getLeft().getValidity() != OK) {
			return structuralAssessment;
		}

		final var accessor = structuralAssessment.getRight().get();
		final var txn = accessor.getTxn();

		/* Syntax */
		final var syntaxStatus = syntaxPrecheck.validate(txn);
		if (syntaxStatus != OK) {
			return responseForFlawed(syntaxStatus);
		}

		/* Semantics */
		final var semanticStatus = checkSemantics(accessor.getFunction(), txn, characteristics);
		if (semanticStatus != OK) {
			return responseForFlawed(semanticStatus);
		}

		throw new AssertionError("Not implemented!");
	}

	private ResponseCodeEnum checkSemantics(
			HederaFunctionality function,
			TransactionBody txn,
			EnumSet<PrecheckCharacteristics> characteristics
	) {
		return characteristics.contains(PrecheckCharacteristics.MUST_BE_CRYPTO_TRANSFER)
				? semanticPrecheck.validate(CryptoTransfer, txn, INSUFFICIENT_TX_FEE)
				: semanticPrecheck.validate(function, txn, NOT_SUPPORTED);
	}

	private enum PrecheckCharacteristics {
		MUST_BE_CRYPTO_TRANSFER,
		SHOULD_INCLUDE_SYSTEM_CHECKS
	}
}
