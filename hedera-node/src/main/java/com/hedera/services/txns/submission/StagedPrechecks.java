package com.hedera.services.txns.submission;

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

	public ResponseCodeEnum systemScreen(SignedTxnAccessor accessor) {
		return systemPrecheck.screen(accessor);
	}

	ResponseCodeEnum validateSemantics(HederaFunctionality function, TransactionBody txn, ResponseCodeEnum failureType) {
		return semanticPrecheck.validate(function, txn, failureType);
	}

	TxnValidityAndFeeReq assessSolvency(SignedTxnAccessor accessor) {
		return solvencyPrecheck.assess(accessor);
	}

	public Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> assessStructure(Transaction signedTxn) {
		return structuralPrecheck.assess(signedTxn);
	}
}
