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

import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;

/**
 * A wrapper object to improve readability of {@code TransactionPrecheck}.
 */
public class StagedPrechecks {
	private final SyntaxPrecheck syntaxPrecheck;
	private final SystemPrecheck systemPrecheck;
	private final SemanticPrecheck semanticPrecheck;
	private final SolvencyPrecheck solvencyPrecheck;
	private final StructuralPrecheck structuralPrecheck;

	public StagedPrechecks(
			SyntaxPrecheck syntaxPrecheck,
			SystemPrecheck systemPrecheck,
			SemanticPrecheck semanticPrecheck,
			SolvencyPrecheck solvencyPrecheck,
			StructuralPrecheck structuralPrecheck
	) {
		this.syntaxPrecheck = syntaxPrecheck;
		this.systemPrecheck = systemPrecheck;
		this.semanticPrecheck = semanticPrecheck;
		this.solvencyPrecheck = solvencyPrecheck;
		this.structuralPrecheck = structuralPrecheck;
	}

	ResponseCodeEnum validateSyntax(TransactionBody txn) {
		return syntaxPrecheck.validate(txn);
	}

	ResponseCodeEnum systemScreen(SignedTxnAccessor accessor) {
		return systemPrecheck.screen(accessor);
	}

	ResponseCodeEnum validateSemantics(HederaFunctionality function, TransactionBody txn, ResponseCodeEnum failureType) {
		return semanticPrecheck.validate(function, txn, failureType);
	}

	TxnValidityAndFeeReq assessSolvencySansSvcFees(SignedTxnAccessor accessor) {
		return solvencyPrecheck.assessSansSvcFees(accessor);
	}

	TxnValidityAndFeeReq assessSolvencyWithSvcFees(SignedTxnAccessor accessor) {
		return solvencyPrecheck.assessWithSvcFees(accessor);
	}

	Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> assessStructure(Transaction signedTxn) {
		return structuralPrecheck.assess(signedTxn);
	}
}
