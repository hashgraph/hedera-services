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
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountAmount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FeeAssessorTest {
	@Mock
	private HtsFeeAssessor htsFeeAssessor;
	@Mock
	private HbarFeeAssessor hbarFeeAssessor;
	@Mock
	private FractionalFeeAssessor fractionalFeeAssessor;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private FeeSchedulesManager feeSchedulesManager;
	@Mock
	private BalanceChangeManager balanceChangeManager;

	private FeeAssessor subject;

	@BeforeEach
	void setUp() {
		subject = new FeeAssessor(htsFeeAssessor, hbarFeeAssessor, fractionalFeeAssessor, dynamicProperties);
	}

	@Test
	void abortsOnExcessiveNesting() {
		given(dynamicProperties.maxCustomFeeDepth()).willReturn(1);

		assertEquals(
				CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH,
				subject.assess(2, fungibleTrigger, feeSchedulesManager, balanceChangeManager));
	}

	@Test
	void okOnNoFees() {
		givenFees(fungibleTokenId.asEntityId(), List.of());

		// when:
		final var result = subject.assess(0, fungibleTrigger, feeSchedulesManager, balanceChangeManager);

		// then:
		verifyNoInteractions(hbarFeeAssessor, htsFeeAssessor, fractionalFeeAssessor);
		assertEquals(OK, result);
	}

	@Test
	void useHbarAccessorAppropriately() {
		givenFees(fungibleTokenId.asEntityId(), List.of(hbarFee));

		// when:
		final var result = subject.assess(0, fungibleTrigger, feeSchedulesManager, balanceChangeManager);

		// then:
		verify(hbarFeeAssessor).assess(payer, hbarFee, balanceChangeManager);
		assertEquals(OK, result);
	}

	@Test
	void useFractionalAccessorAppropriately() {
		givenFees(fungibleTokenId.asEntityId(), List.of(fractionalFee));

		// when:
		final var result = subject.assess(0, fungibleTrigger, feeSchedulesManager, balanceChangeManager);

		// then:
		verify(fractionalFeeAssessor).assess(fungibleTrigger, fractionalFee, balanceChangeManager);
		assertEquals(OK, result);
	}

	@Test
	void useHtsAccessorAppropriately() {
		givenFees(fungibleTokenId.asEntityId(), List.of(htsFee));

		// when:
		final var result = subject.assess(0, fungibleTrigger, feeSchedulesManager, balanceChangeManager);

		// then:
		verify(htsFeeAssessor).assess(payer, htsFee, balanceChangeManager);
		assertEquals(OK, result);
	}

	@Test
	void abortsOnExcessiveChanges() {
		given(dynamicProperties.maxXferBalanceChanges()).willReturn(1);
		givenFees(fungibleTokenId.asEntityId(), List.of(hbarFee, htsFee, fractionalFee));
		given(balanceChangeManager.changesSoFar())
				.willReturn(1)
				.willReturn(2);

		// when:
		final var result = subject.assess(0, fungibleTrigger, feeSchedulesManager, balanceChangeManager);

		// then:
		verify(hbarFeeAssessor).assess(payer, hbarFee, balanceChangeManager);
		verify(htsFeeAssessor).assess(payer, htsFee, balanceChangeManager);
		verify(fractionalFeeAssessor, never()).assess(
				fungibleTrigger, fractionalFee, balanceChangeManager);
		assertEquals(CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS, result);
	}

	private void givenFees(EntityId token, List<FcCustomFee> customFees) {
		given(feeSchedulesManager.managedSchedulesFor(token)).willReturn(customFees);
	}

	private final long amountOfFungibleDebit = 1_000L;
	private final long amountOfHbarFee = 100_000L;
	private final long amountOfHtsFee = 10L;
	private final long minAmountOfFractionalFee = 1L;
	private final long maxAmountOfFractionalFee = 100L;
	private final long numerator = 1L;
	private final long denominator = 100L;
	private final Id payer = new Id(0, 1, 2);
	private final Id fungibleTokenId = new Id(1, 2, 3);
	private final EntityId feeDenom = new EntityId(6, 6, 6);
	private final EntityId hbarFeeCollector = new EntityId(2, 3, 4);
	private final EntityId htsFeeCollector = new EntityId(3, 4, 5);
	private final EntityId fractionalFeeCollector = new EntityId(4, 5, 6);
	private final AccountAmount fungibleDebit = AccountAmount.newBuilder()
			.setAccountID(payer.asGrpcAccount())
			.setAmount(amountOfFungibleDebit)
			.build();
	private final BalanceChange fungibleTrigger = BalanceChange.changingFtUnits(
			fungibleTokenId,
			fungibleTokenId.asGrpcToken(),
			fungibleDebit);
	private final FcCustomFee hbarFee = FcCustomFee.fixedFee(amountOfHbarFee, null, hbarFeeCollector);
	private final FcCustomFee htsFee = FcCustomFee.fixedFee(amountOfHtsFee, feeDenom, htsFeeCollector);
	private final FcCustomFee fractionalFee = FcCustomFee.fractionalFee(
			numerator,
			denominator,
			minAmountOfFractionalFee,
			maxAmountOfFractionalFee,
			fractionalFeeCollector);

}
