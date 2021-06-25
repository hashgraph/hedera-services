package com.hedera.services.grpc.marshalling;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.PureTransferSemanticChecks;
import com.hedera.services.state.submerkle.AssessedCustomFee;
import com.hedera.services.state.submerkle.CustomFee;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.hedera.test.utils.IdUtils.adjustFrom;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.IdUtils.hbarChange;
import static com.hedera.test.utils.IdUtils.tokenChange;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ImpliedTransfersMarshalTest {
	private CryptoTransferTransactionBody op;

	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private PureTransferSemanticChecks transferSemanticChecks;

	@Mock
	private CustomFeeSchedules customFeeSchedules;

	@Mock
	private CustomFeeSchedules newCustomFeeSchedules;

	private ImpliedTransfersMarshal subject;

	private final AccountID aModel = asAccount("1.2.3");
	private final AccountID bModel = asAccount("2.3.4");
	private final AccountID cModel = asAccount("3.4.5");
	private final AccountID payer = asAccount("5.6.7");
	private final Id token = new Id(0, 0, 75231);
	private final Id anotherToken = new Id(0, 0, 75232);
	private final Id yetAnotherToken = new Id(0, 0, 75233);
	private final TokenID anId = asToken("0.0.75231");
	private final TokenID anotherId = asToken("0.0.75232");
	private final TokenID yetAnotherId = asToken("0.0.75233");
	private final AccountID a = asAccount("1.2.3");
	private final AccountID b = asAccount("2.3.4");
	private final AccountID c = asAccount("3.4.5");

	private final long aHbarChange = -100L;
	private final long bHbarChange = +50L;
	private final long cHbarChange = +50L;
	private final long aAnotherTokenChange = -50L;
	private final long bAnotherTokenChange = +25L;
	private final long cAnotherTokenChange = +25L;
	private final long bTokenChange = -100L;
	private final long cTokenChange = +100L;
	private final long aYetAnotherTokenChange = -15L;
	private final long bYetAnotherTokenChange = +15L;
	private final long customFeeChangeToFeeCollector = +20L;
	private final long customFeeChangeFromPayer = -20L;

	private final int maxExplicitHbarAdjusts = 5;
	private final int maxExplicitTokenAdjusts = 50;

	private final EntityId customFeeToken = new EntityId(0, 0, 123);
	private final EntityId customFeeCollector = new EntityId(0, 0, 124);
	final List<Pair<Id, List<CustomFee>>> entityCustomFees = List.of(
			Pair.of(customFeeToken.asId(), new ArrayList<>()));
	final List<Pair<EntityId, List<CustomFee>>> newCustomFeeChanges = List.of(
			Pair.of(customFeeToken, List.of(CustomFee.fixedFee(10L, customFeeToken, customFeeCollector))));
	private final List<AssessedCustomFee> assessedCustomFees = List.of(
			new AssessedCustomFee(customFeeCollector, customFeeToken, 123L));

	private final long numerator = 2L;
	private final long denominator = 100L;
	private final long minimumUnitsToCollect = 20L;
	private final long maximumUnitsToCollect = 100L;
	EntityId feeCollector = EntityId.fromGrpcAccountId(aModel);

	@BeforeEach
	void setUp() {
		subject = new ImpliedTransfersMarshal(dynamicProperties, transferSemanticChecks, customFeeSchedules);
	}

	@Test
	void validatesXfers() {
		setupFixtureOp();
		final var expectedMeta = new ImpliedTransfersMeta(
				maxExplicitHbarAdjusts, maxExplicitTokenAdjusts, TRANSFER_LIST_SIZE_LIMIT_EXCEEDED,
				Collections.emptyList());

		given(dynamicProperties.maxTransferListSize()).willReturn(maxExplicitHbarAdjusts);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxExplicitTokenAdjusts);
		// and:
		given(transferSemanticChecks.fullPureValidation(
				maxExplicitHbarAdjusts,
				maxExplicitTokenAdjusts,
				op.getTransfers(),
				op.getTokenTransfersList())).willReturn(TRANSFER_LIST_SIZE_LIMIT_EXCEEDED);

		// when:
		final var result = subject.unmarshalFromGrpc(op, bModel);

		// then:
		assertEquals(result.getMeta(), expectedMeta);
	}

	@Test
	void getsExpectedList() {
		setupFixtureOp();
		// and:
		final List<BalanceChange> expectedChanges = List.of(new BalanceChange[] {
						hbarChange(aModel,
								aHbarChange + (3 * customFeeChangeToFeeCollector)),
						hbarChange(bModel, bHbarChange),
						hbarChange(cModel, cHbarChange),
						tokenChange(anotherToken, aModel, aAnotherTokenChange),
						tokenChange(anotherToken, bModel, bAnotherTokenChange),
						tokenChange(anotherToken, cModel, cAnotherTokenChange),
						updateCodeForInsufficientBalance(hbarChange(payer,
								3 * customFeeChangeFromPayer)),
						tokenChange(token, bModel, bTokenChange),
						tokenChange(token, cModel, cTokenChange),
						tokenChange(yetAnotherToken, aModel, aYetAnotherTokenChange),
						tokenChange(yetAnotherToken, bModel, bYetAnotherTokenChange),
				}
		);
		final List<CustomFee> customFee = getFixedCustomFee();
		final List<Pair<Id, List<CustomFee>>> expectedCustomFeeChanges =
				List.of(Pair.of(anotherToken, customFee),
						Pair.of(token, customFee),
						Pair.of(yetAnotherToken, customFee));
		final List<AssessedCustomFee> expectedAssessedCustomFees = List.of(
				new AssessedCustomFee(EntityId.fromGrpcAccountId(aModel), customFeeChangeToFeeCollector),
				new AssessedCustomFee(EntityId.fromGrpcAccountId(aModel), customFeeChangeToFeeCollector),
				new AssessedCustomFee(EntityId.fromGrpcAccountId(aModel), customFeeChangeToFeeCollector));

		// and:
		final var expectedMeta = new ImpliedTransfersMeta(maxExplicitHbarAdjusts, maxExplicitTokenAdjusts,
				OK, expectedCustomFeeChanges);

		given(dynamicProperties.maxTransferListSize()).willReturn(maxExplicitHbarAdjusts);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxExplicitTokenAdjusts);
		given(transferSemanticChecks.fullPureValidation(
				maxExplicitHbarAdjusts,
				maxExplicitTokenAdjusts,
				op.getTransfers(),
				op.getTokenTransfersList())).willReturn(OK);
		given(customFeeSchedules.lookupScheduleFor(any())).willReturn(customFee);

		// when:
		final var result = subject.unmarshalFromGrpc(op, payer);

		// then:
		assertEquals(expectedMeta, result.getMeta());
		assertEquals(expectedChanges, result.getAllBalanceChanges());
		assertEquals(expectedCustomFeeChanges, result.getTokenFeeSchedules());
		assertEquals(expectedAssessedCustomFees, result.getAssessedCustomFees());
	}

	@Test
	void metaObjectContractSanityChecks() {
		// given:
		final var oneMeta = new ImpliedTransfersMeta(3, 4, OK, entityCustomFees);
		final var twoMeta = new ImpliedTransfersMeta(1, 2, TOKEN_WAS_DELETED, Collections.emptyList());
		// and:
		final var oneRepr = "ImpliedTransfersMeta{code=OK, maxExplicitHbarAdjusts=3, " +
				"maxExplicitTokenAdjusts=4, customFeeSchedulesUsedInMarshal=[(Id{shard=0, realm=0, num=123},[])" +
				"]}";
		final var twoRepr = "ImpliedTransfersMeta{code=TOKEN_WAS_DELETED, " +
				"maxExplicitHbarAdjusts=1, maxExplicitTokenAdjusts=2, customFeeSchedulesUsedInMarshal=[]}";

		// expect:
		assertNotEquals(oneMeta, twoMeta);
		assertNotEquals(oneMeta.hashCode(), twoMeta.hashCode());
		// and:
		assertEquals(oneRepr, oneMeta.toString());
		assertEquals(twoRepr, twoMeta.toString());
	}

	@Test
	void impliedXfersObjectContractSanityChecks() {
		// given:
		final var twoChanges = List.of(tokenChange(
				new Id(1, 2, 3),
				asAccount("4.5.6"),
				7));
		final var oneImpliedXfers = ImpliedTransfers.invalid(3, 4, TOKEN_WAS_DELETED);
		final var twoImpliedXfers = ImpliedTransfers.valid(1, 100, twoChanges,
				entityCustomFees, assessedCustomFees);
		// and:
		final var oneRepr = "ImpliedTransfers{meta=ImpliedTransfersMeta{code=TOKEN_WAS_DELETED, " +
				"maxExplicitHbarAdjusts=3, maxExplicitTokenAdjusts=4, customFeeSchedulesUsedInMarshal=[]}, " +
				"changes=[]," +
				" " +
				"tokenFeeSchedules=[], assessedCustomFees=[]}";
		final var twoRepr = "ImpliedTransfers{meta=ImpliedTransfersMeta{code=OK, maxExplicitHbarAdjusts=1, " +
				"maxExplicitTokenAdjusts=100, customFeeSchedulesUsedInMarshal=[(Id{shard=0, realm=0, num=123},[])]}," +
				" changes=[BalanceChange{token=Id{shard=1, realm=2, num=3}, account=Id{shard=4, realm=5, num=6}," +
				" units=7, codeForInsufficientBalance=INSUFFICIENT_TOKEN_BALANCE}], " +
				"tokenFeeSchedules=[(Id{shard=0, realm=0, num=123},[])], " +
				"assessedCustomFees=[AssessedCustomFee{token=EntityId{shard=0, realm=0, num=123}, " +
				"account=EntityId{shard=0, realm=0, num=124}, units=123}]}";

		// expect:
		assertNotEquals(oneImpliedXfers, twoImpliedXfers);
		assertNotEquals(oneImpliedXfers.hashCode(), twoImpliedXfers.hashCode());
		// and:
		assertEquals(oneRepr, oneImpliedXfers.toString());
		assertEquals(twoRepr, twoImpliedXfers.toString());
	}

	@Test
	void handlesEasyCase() {
		// given:
		long reasonable = 1_234_567L;
		long n = 10;
		long d = 9;
		// and:
		final var expected = reasonable * n / d;

		// expect:
		assertEquals(expected, subject.safeFractionMultiply(n, d, reasonable));
	}

	@Test
	void fallsBackToArbitraryPrecisionIfNeeded() {
		// given:
		long huge = Long.MAX_VALUE / 2;
		long n = 10;
		long d = 9;
		// and:
		final var expected = BigInteger.valueOf(huge)
				.multiply(BigInteger.valueOf(n))
				.divide(BigInteger.valueOf(d))
				.longValueExact();

		// expect:
		assertEquals(expected, subject.safeFractionMultiply(n, d, huge));
	}

	@Test
	void propagatesArithmeticExceptionOnOverflow() {
		// given:
		long huge = Long.MAX_VALUE - 1;
		long n = 10;
		long d = 9;

		// expect:
		assertThrows(ArithmeticException.class, () -> subject.safeFractionMultiply(n, d, huge));
	}

	@Test
	void metaRecognizesIdenticalConditions() {
		// given:
		final var meta = new ImpliedTransfersMeta(3, 4, OK, entityCustomFees);

		given(dynamicProperties.maxTransferListSize()).willReturn(3);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(4);

		// expect:
		assertTrue(meta.wasDerivedFrom(dynamicProperties, customFeeSchedules));

		//modify customFeeChanges to see test fails
		given(newCustomFeeSchedules.lookupScheduleFor(any())).willReturn(newCustomFeeChanges.get(0).getValue());
		assertFalse(meta.wasDerivedFrom(dynamicProperties, newCustomFeeSchedules));

		// and:
		given(dynamicProperties.maxTransferListSize()).willReturn(2);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(4);

		// expect:
		assertFalse(meta.wasDerivedFrom(dynamicProperties, customFeeSchedules));

		// and:
		given(dynamicProperties.maxTransferListSize()).willReturn(3);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(3);

		// expect:
		assertFalse(meta.wasDerivedFrom(dynamicProperties, customFeeSchedules));
	}

	@Test
	void translatesOverflowFromExcessiveSumming() {
		op = CryptoTransferTransactionBody.newBuilder()
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, Long.MAX_VALUE),
								adjustFrom(b, Long.MAX_VALUE / 2)
						))).build();

		// and:
		final var expected = ImpliedTransfers.invalid(
				maxExplicitHbarAdjusts, maxExplicitTokenAdjusts, CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE);

		given(dynamicProperties.maxTransferListSize()).willReturn(maxExplicitHbarAdjusts);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxExplicitTokenAdjusts);
		given(transferSemanticChecks.fullPureValidation(
				maxExplicitHbarAdjusts,
				maxExplicitTokenAdjusts,
				op.getTransfers(),
				op.getTokenTransfersList())).willReturn(OK);

		// when:
		final var actual = subject.unmarshalFromGrpc(op, payer);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	void translatesOverflowFromFractionalCalc() {
		op = CryptoTransferTransactionBody.newBuilder()
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, cHbarChange)
						))).build();
		// and:
		final var customFee = getOverflowingFractionalCustomFee();

		// and:
		final var expected = ImpliedTransfers.invalid(
				maxExplicitHbarAdjusts, maxExplicitTokenAdjusts, CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE);

		given(dynamicProperties.maxTransferListSize()).willReturn(maxExplicitHbarAdjusts);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxExplicitTokenAdjusts);
		given(transferSemanticChecks.fullPureValidation(
				maxExplicitHbarAdjusts,
				maxExplicitTokenAdjusts,
				op.getTransfers(),
				op.getTokenTransfersList())).willReturn(OK);
		given(customFeeSchedules.lookupScheduleFor(any())).willReturn(customFee);

		// when:
		final var actual = subject.unmarshalFromGrpc(op, payer);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	void getsExpectedListWithFractionalCustomFee() {
		op = CryptoTransferTransactionBody.newBuilder()
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, cHbarChange)
						))).build();
		// and:
		final var expectedFractionalFee = Math.min(
				Math.max(numerator * cHbarChange / denominator, minimumUnitsToCollect), maximumUnitsToCollect);
		final var expectedChanges = List.of(new BalanceChange[] {
				tokenChange(anotherToken, aModel, cHbarChange + expectedFractionalFee),
				updateCodeForInsufficientBalance(tokenChange(anotherToken, payer, -expectedFractionalFee)) });

		final var customFee = getFractionalCustomFee();
		final var expectedCustomFeeChanges =
				List.of(Pair.of(anotherToken, customFee));
		final var expectedAssessedCustomFees = List.of(
				new AssessedCustomFee(EntityId.fromGrpcAccountId(aModel), anotherToken.asEntityId(),
						expectedFractionalFee));

		// and:
		final var expectedMeta = new ImpliedTransfersMeta(maxExplicitHbarAdjusts, maxExplicitTokenAdjusts,
				OK, expectedCustomFeeChanges);

		given(dynamicProperties.maxTransferListSize()).willReturn(maxExplicitHbarAdjusts);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxExplicitTokenAdjusts);
		given(transferSemanticChecks.fullPureValidation(
				maxExplicitHbarAdjusts,
				maxExplicitTokenAdjusts,
				op.getTransfers(),
				op.getTokenTransfersList())).willReturn(OK);
		given(customFeeSchedules.lookupScheduleFor(any())).willReturn(customFee);

		// when:
		final var result = subject.unmarshalFromGrpc(op, payer);

		// then:
		assertEquals(expectedMeta, result.getMeta());
		assertEquals(expectedChanges, result.getAllBalanceChanges());
		assertEquals(expectedCustomFeeChanges, result.getTokenFeeSchedules());
		assertEquals(expectedAssessedCustomFees, result.getAssessedCustomFees());
	}

	@Test
	void throwsForFractionalZeroDenominator() {
		op = CryptoTransferTransactionBody.newBuilder()
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, cHbarChange)
						))).build();
		// and:

		var exception = assertThrows(IllegalArgumentException.class,
				() -> CustomFee.fractionalFee(numerator, 0, minimumUnitsToCollect,
						maximumUnitsToCollect, feeCollector));
		assertEquals("Division by zero is not allowed", exception.getMessage());
	}

	@Test
	void getsExpectedListWithFixedCustomFeeNonNullDenomination() {
		op = CryptoTransferTransactionBody.newBuilder()
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, cHbarChange)
						))).build();
		// and:
		final var expectedChanges = List.of(new BalanceChange[] {
				tokenChange(anotherToken, aModel, cHbarChange),
				tokenChange(token, aModel, 20L),
				updateCodeForInsufficientBalance(tokenChange(token, payer, -20L)) });

		final var customFee = getFixedCustomFeeNonNullDenom();
		final var expectedCustomFeeChanges =
				List.of(Pair.of(anotherToken, customFee));
		final var expectedAssessedCustomFees = List.of(
				new AssessedCustomFee(EntityId.fromGrpcAccountId(aModel), token.asEntityId(), 20L));

		// and:
		final var expectedMeta = new ImpliedTransfersMeta(maxExplicitHbarAdjusts, maxExplicitTokenAdjusts,
				OK, expectedCustomFeeChanges);

		given(dynamicProperties.maxTransferListSize()).willReturn(maxExplicitHbarAdjusts);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxExplicitTokenAdjusts);
		given(transferSemanticChecks.fullPureValidation(
				maxExplicitHbarAdjusts,
				maxExplicitTokenAdjusts,
				op.getTransfers(),
				op.getTokenTransfersList())).willReturn(OK);
		given(customFeeSchedules.lookupScheduleFor(anotherToken.asEntityId())).willReturn(customFee);

		// when:
		final var result = subject.unmarshalFromGrpc(op, payer);

		// then:
		assertEquals(expectedMeta, result.getMeta());
		assertEquals(expectedChanges, result.getAllBalanceChanges());
		assertEquals(expectedCustomFeeChanges, result.getTokenFeeSchedules());
		assertEquals(expectedAssessedCustomFees, result.getAssessedCustomFees());
	}

	private void setupFixtureOp() {
		var hbarAdjusts = TransferList.newBuilder()
				.addAccountAmounts(adjustFrom(a, -100))
				.addAccountAmounts(adjustFrom(b, 50))
				.addAccountAmounts(adjustFrom(c, 50))
				.build();
		op = CryptoTransferTransactionBody.newBuilder()
				.setTransfers(hbarAdjusts)
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, -50),
								adjustFrom(b, 25),
								adjustFrom(c, 25)
						)))
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anId)
						.addAllTransfers(List.of(
								adjustFrom(b, -100),
								adjustFrom(c, 100)
						)))
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(yetAnotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, -15),
								adjustFrom(b, 15)
						)))
				.build();
	}

	private List<CustomFee> getFixedCustomFee() {
		return List.of(
				CustomFee.fixedFee(20L, null, feeCollector)
		);
	}

	private List<CustomFee> getFixedCustomFeeNonNullDenom() {
		return List.of(
				CustomFee.fixedFee(20L, token.asEntityId(), feeCollector)
		);
	}

	private List<CustomFee> getFractionalCustomFee() {
		return List.of(
				CustomFee.fractionalFee(
						numerator, denominator, minimumUnitsToCollect, maximumUnitsToCollect, feeCollector)
		);
	}

	private List<CustomFee> getOverflowingFractionalCustomFee() {
		return List.of(
				CustomFee.fractionalFee(
						Long.MAX_VALUE, 2, minimumUnitsToCollect, maximumUnitsToCollect, feeCollector)
		);
	}

	private BalanceChange updateCodeForInsufficientBalance(BalanceChange change) {
		change.setCodeForInsufficientBalance(INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE);
		return change;
	}
}
