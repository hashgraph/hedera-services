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
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.commons.lang3.tuple.Pair;

import java.util.EnumSet;
import java.util.Optional;

import static com.hedera.services.txns.submission.PresolvencyFlaws.PRESOLVENCY_FLAWS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.swirlds.common.PlatformStatus.ACTIVE;

public class Precheck {
	private final SyntaxPrecheck syntaxPrecheck;
	private final StructuralPrecheck structuralPrecheck;
	private final CurrentPlatformStatus currentPlatformStatus;

	private static final EnumSet<PrecheckCharacteristics> TOP_LEVEL_CHARACTERISTICS =
			EnumSet.allOf(PrecheckCharacteristics.class);
	private static final EnumSet<PrecheckCharacteristics> QUERY_PAYMENT_CHARACTERISTICS =
			EnumSet.noneOf(PrecheckCharacteristics.class);

	public Precheck(
			SyntaxPrecheck syntaxPrecheck,
			StructuralPrecheck structuralPrecheck,
			CurrentPlatformStatus currentPlatformStatus
	) {
		this.syntaxPrecheck = syntaxPrecheck;
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
			return PRESOLVENCY_FLAWS.get(PLATFORM_NOT_ACTIVE);
		}

		var structuralAssessment = structuralPrecheck.assess(signedTxn);
		if (structuralAssessment.getLeft().getValidity() != OK) {
			return structuralAssessment;
		}

		var accessor = structuralAssessment.getRight().get();
		var txn = accessor.getTxn();
		var syntacticValidity = syntaxPrecheck.validate(txn);
		if (syntacticValidity != OK) {
			return PresolvencyFlaws.responseFor(syntacticValidity);
		}

		throw new AssertionError("Not implemented!");
	}

	private enum PrecheckCharacteristics {
		CHECK_THROTTLES,
		CHECK_API_PERMISSIONS
	}
}
