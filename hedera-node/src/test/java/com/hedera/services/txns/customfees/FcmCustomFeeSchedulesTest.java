package com.hedera.services.txns.customfees;

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

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(MockitoExtension.class)
class FcmCustomFeeSchedulesTest {
	private FcmCustomFeeSchedules subject;
	FCMap<MerkleEntityId, MerkleToken> tokenFCMap = new FCMap<>();

	private final EntityId aTreasury = new EntityId(10, 11, 12);
	private final EntityId bTreasury = new EntityId(11, 12, 13);
	private final EntityId tokenA = new EntityId(0,0,1);
	private final EntityId tokenB = new EntityId(0,0,2);
	private final EntityId feeCollector = new EntityId(0,0,3);
	private final EntityId missingToken = new EntityId(0,0,4);
	private final MerkleToken aToken = new MerkleToken();
	private final MerkleToken bToken = new MerkleToken();

	@BeforeEach
	void setUp() {
		// setup:
		final var tokenAFees = List.of(FcCustomFee.fixedFee(20L, tokenA, feeCollector).asGrpc());
		final var tokenBFees = List.of(FcCustomFee.fixedFee(40L, tokenB, feeCollector).asGrpc());
		aToken.setFeeScheduleFrom(tokenAFees, null);
		aToken.setTreasury(aTreasury);
		bToken.setFeeScheduleFrom(tokenBFees, null);
		bToken.setTreasury(bTreasury);

		tokenFCMap.put(tokenA.asMerkle(), aToken);
		tokenFCMap.put(tokenB.asMerkle(), bToken);
		subject = new FcmCustomFeeSchedules(() -> tokenFCMap);
	}

	@Test
	void validateLookUpScheduleFor() {
		// then:
		final var tokenAFees = subject.lookupMetaFor(tokenA);
		final var tokenBFees = subject.lookupMetaFor(tokenB);
		final var missingTokenFees = subject.lookupMetaFor(missingToken);

		// expect:
		assertEquals(aToken.customFeeSchedule(), tokenAFees.getCustomFees());
		assertEquals(aTreasury, tokenAFees.getTreasuryId().asEntityId());
		assertEquals(bToken.customFeeSchedule(), tokenBFees.getCustomFees());
		assertEquals(bTreasury, tokenBFees.getTreasuryId().asEntityId());
		assertSame(Collections.emptyList(), missingTokenFees.getCustomFees());
	}

	@Test
	void getterWorks() {
		assertEquals(tokenFCMap, subject.getTokens().get());
	}

	@Test
	void testObjectContract() {
		// given:
		FCMap<MerkleEntityId, MerkleToken> secondFCMap = new FCMap<>();
		MerkleToken token = new MerkleToken();
		final var missingFees = List.of(
				FcCustomFee.fixedFee(50L, missingToken, feeCollector).asGrpc());
		token.setFeeScheduleFrom(missingFees, null);
		secondFCMap.put(missingToken.asMerkle(), new MerkleToken());
		final var fees1 = new FcmCustomFeeSchedules(() -> tokenFCMap);
		final var fees2 = new FcmCustomFeeSchedules(() -> secondFCMap);

		// expect:
		assertNotEquals(fees1, fees2);
		assertNotEquals(fees1.hashCode(), fees2.hashCode());
	}
}
