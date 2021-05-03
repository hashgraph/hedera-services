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
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.PlatformStatus;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ArgumentConverter;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.hedera.services.txns.submission.PresolvencyFlaws.WELL_KNOWN_FLAWS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_TOO_MANY_LAYERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TransactionPrecheckTest {
	private final long reqFee = 1234L;

	@Mock
	private QueryFeeCheck queryFeeCheck;
	@Mock
	private CurrentPlatformStatus currentPlatformStatus;
	@Mock
	private SystemPrecheck systemPrecheck;
	@Mock
	private SyntaxPrecheck syntaxPrecheck;
	@Mock
	private SemanticPrecheck semanticPrecheck;
	@Mock
	private SolvencyPrecheck solvencyPrecheck;
	@Mock
	private StructuralPrecheck structuralPrecheck;

	private TransactionPrecheck subject;


	@BeforeEach
	void setUp() {
		var stagedPrechecks = new StagedPrechecks(
				syntaxPrecheck,
				systemPrecheck,
				semanticPrecheck,
				solvencyPrecheck,
				structuralPrecheck);
		subject = new TransactionPrecheck(queryFeeCheck, stagedPrechecks, currentPlatformStatus);
	}

	@Test
	void abortsOnInactivePlatform() {
		given(currentPlatformStatus.get()).willReturn(PlatformStatus.MAINTENANCE);

		// when:
		var topLevelResponse = subject.performForTopLevel(Transaction.getDefaultInstance());
		var queryPaymentResponse = subject.performForQueryPayment(Transaction.getDefaultInstance());

		// then:
		assertFailure(PLATFORM_NOT_ACTIVE, topLevelResponse);
		assertFailure(PLATFORM_NOT_ACTIVE, queryPaymentResponse);
	}

	@Test
	void abortsOnStructuralFlaw() {
		givenActivePlatform();
		given(structuralPrecheck.assess(any())).willReturn(WELL_KNOWN_FLAWS.get(TRANSACTION_TOO_MANY_LAYERS));

		// when:
		var topLevelResponse = subject.performForTopLevel(Transaction.getDefaultInstance());
		var queryPaymentResponse = subject.performForQueryPayment(Transaction.getDefaultInstance());

		// then:
		assertFailure(TRANSACTION_TOO_MANY_LAYERS, topLevelResponse);
		assertFailure(TRANSACTION_TOO_MANY_LAYERS, queryPaymentResponse);
	}

	@ParameterizedTest
	@CsvSource({
			"INVALID_TRANSACTION_ID",
			"TRANSACTION_ID_FIELD_NOT_ALLOWED",
			"DUPLICATE_TRANSACTION",
			"INSUFFICIENT_TX_FEE",
			"PAYER_ACCOUNT_NOT_FOUND",
			"INVALID_NODE_ACCOUNT",
			"MEMO_TOO_LONG",
			"INVALID_ZERO_BYTE_IN_STRING",
			"INVALID_TRANSACTION_DURATION",
			"TRANSACTION_EXPIRED",
			"INVALID_TRANSACTION_START"
	})
	void abortsOnSyntaxError(@ConvertWith(ResponseCodeConverter.class) ResponseCodeEnum syntaxError) {
		givenActivePlatform();
		givenStructuralSoundness();
		given(syntaxPrecheck.validate(any())).willReturn(syntaxError);

		// when:
		var topLevelResponse = subject.performForTopLevel(Transaction.getDefaultInstance());
		var queryPaymentResponse = subject.performForQueryPayment(Transaction.getDefaultInstance());

		// then:
		assertFailure(syntaxError, topLevelResponse);
		assertFailure(syntaxError, queryPaymentResponse);
	}

	@Test
	void abortsOnSemanticErrorForTopLevel() {
		givenActivePlatform();
		givenStructuralSoundness();
		givenValidSyntax();
		given(semanticPrecheck.validate(any(), any(), eq(NOT_SUPPORTED))).willReturn(NOT_SUPPORTED);

		// when:
		var topLevelResponse = subject.performForTopLevel(Transaction.getDefaultInstance());

		// then:
		assertFailure(NOT_SUPPORTED, topLevelResponse);
	}

	@Test
	void abortsOnSemanticErrorForQueryPayment() {
		givenActivePlatform();
		givenStructuralSoundness();
		givenValidSyntax();
		given(semanticPrecheck.validate(eq(CryptoTransfer), any(), eq(INSUFFICIENT_TX_FEE)))
				.willReturn(INSUFFICIENT_TX_FEE);

		// when:
		var queryPaymentResponse = subject.performForQueryPayment(Transaction.getDefaultInstance());

		// then:
		assertFailure(INSUFFICIENT_TX_FEE, queryPaymentResponse);
	}

	@Test
	void presolvencyFlawsCanResolveEvenUnexpectedError() {
		// given:
		var response = PresolvencyFlaws.responseForFlawed(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

		// then:
		assertFailure(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, response);
	}

	@Test
	void abortsOnInsolvencyForTopLevel() {
		givenActivePlatform();
		givenStructuralSoundness();
		givenValidSyntax();
		givenValidSemantics();
		given(solvencyPrecheck.assessSansSvcFees(any()))
				.willReturn(new TxnValidityAndFeeReq(INSUFFICIENT_TX_FEE, reqFee));

		// when:
		var topLevelResponse = subject.performForTopLevel(Transaction.getDefaultInstance());

		// then:
		assertFailure(INSUFFICIENT_TX_FEE, reqFee, topLevelResponse);
	}

	@Test
	void abortsOnInsolvencyForQueryPayment() {
		givenActivePlatform();
		givenStructuralSoundness();
		givenValidSyntax();
		givenValidSemantics();
		given(solvencyPrecheck.assessWithSvcFees(any()))
				.willReturn(new TxnValidityAndFeeReq(INSUFFICIENT_TX_FEE, reqFee));

		// when:
		var queryPaymentResponse = subject.performForQueryPayment(Transaction.getDefaultInstance());

		// then:
		assertFailure(INSUFFICIENT_TX_FEE, reqFee, queryPaymentResponse);
	}

	@Test
	void abortsOnFailedSystemChecksForTopLevel() {
		givenActivePlatform();
		givenStructuralSoundness();
		givenValidSyntax();
		givenValidSemantics();
		givenNodeAndNetworkSolvency();
		given(systemPrecheck.screen(any())).willReturn(BUSY);

		// when:
		var topLevelResponse = subject.performForTopLevel(Transaction.getDefaultInstance());

		// then:
		assertFailure(BUSY, reqFee, topLevelResponse);
	}

	@Test
	void doesntPerformSystemChecksForQueryPayments() {
		givenActivePlatform();
		givenStructuralSoundness();
		givenValidSyntax();
		givenValidSemantics();
		givenFullSolvency();
		givenValidQueryPaymentXfers();

		// when:
		var queryPaymentResponse = subject.performForQueryPayment(Transaction.getDefaultInstance());

		// then:
		assertSuccess(reqFee, queryPaymentResponse);
	}

	@Test
	void rejectsInvalidQueryPaymentXfers() {
		givenActivePlatform();
		givenStructuralSoundness();
		givenValidSyntax();
		givenValidSemantics();
		givenFullSolvency();
		given(queryFeeCheck.validateQueryPaymentTransfers(any())).willReturn(INSUFFICIENT_PAYER_BALANCE);

		// when:
		var queryPaymentResponse = subject.performForQueryPayment(Transaction.getDefaultInstance());

		// then:
		assertFailure(INSUFFICIENT_PAYER_BALANCE, reqFee, queryPaymentResponse);
	}

	private void givenValidQueryPaymentXfers() {
		given(queryFeeCheck.validateQueryPaymentTransfers(any())).willReturn(OK);
	}

	private void givenActivePlatform() {
		given(currentPlatformStatus.get()).willReturn(PlatformStatus.ACTIVE);
	}

	private void givenStructuralSoundness() {
		given(structuralPrecheck.assess(any()))
				.willReturn(Pair.of(
						new TxnValidityAndFeeReq(OK),
						Optional.of(SignedTxnAccessor.uncheckedFrom(Transaction.getDefaultInstance())))
				);
	}

	private void givenFullSolvency() {
		given(solvencyPrecheck.assessWithSvcFees(any())).willReturn(new TxnValidityAndFeeReq(OK, reqFee));
	}

	private void givenNodeAndNetworkSolvency() {
		given(solvencyPrecheck.assessSansSvcFees(any())).willReturn(new TxnValidityAndFeeReq(OK, reqFee));
	}

	private void givenValidSyntax() {
		given(syntaxPrecheck.validate(any())).willReturn(OK);
	}

	private void givenValidSemantics() {
		given(semanticPrecheck.validate(any(), any(), any())).willReturn(OK);
	}

	private void assertSuccess(long reqFee, Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> response) {
		assertEquals(OK, response.getLeft().getValidity());
		assertEquals(reqFee, response.getLeft().getRequiredFee());
		assertTrue(response.getRight().isPresent());
	}

	private void assertFailure(
			ResponseCodeEnum abort,
			Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> response
	) {
		assertDetailFailure(abort, 0L, response);
	}

	private void assertFailure(
			ResponseCodeEnum abort,
			long reqFee,
			Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> response
	) {
		assertDetailFailure(abort, reqFee, response);
	}

	private void assertDetailFailure(
			ResponseCodeEnum abort,
			long expectedFeeReq,
			Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> response
	) {
		var req = response.getLeft();
		assertEquals(abort, req.getValidity());
		assertEquals(expectedFeeReq, req.getRequiredFee());
		assertTrue(response.getRight().isEmpty());
	}

	static final class ResponseCodeConverter implements ArgumentConverter {
		@Override
		public Object convert(Object arg, ParameterContext parameterContext) throws ArgumentConversionException {
			return ResponseCodeEnum.valueOf((String) arg);
		}
	}
}
