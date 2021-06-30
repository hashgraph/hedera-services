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
import com.hedera.services.state.submerkle.CustomFee;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import proto.CustomFeesOuterClass;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class FcmCustomFeeSchedulesTest {
	private FcmCustomFeeSchedules subject;
	FCMap<MerkleEntityId, MerkleToken> tokenFCMap = new FCMap<>();

	private final EntityId tokenA = new EntityId(0,0,1);
	private final EntityId tokenB = new EntityId(0,0,2);
	private final EntityId feeCollector = new EntityId(0,0,3);
	private final EntityId missingToken = new EntityId(0,0,4);
	private final MerkleToken tokenAValue = new MerkleToken();
	private final MerkleToken tokenBValue = new MerkleToken();

	@BeforeEach
	void setUp() {
		//setup:
		final var tokenAFees = CustomFeesOuterClass.CustomFees
				.newBuilder()
				.addCustomFees(CustomFee.fixedFee(20L, tokenA, feeCollector).asGrpc());
		final var tokenBFees = CustomFeesOuterClass.CustomFees
				.newBuilder()
				.addCustomFees(CustomFee.fixedFee(40L, tokenB, feeCollector).asGrpc());
		tokenAValue.setFeeScheduleFrom(tokenAFees);
		tokenBValue.setFeeScheduleFrom(tokenBFees);

		tokenFCMap.put(tokenA.asMerkle(), tokenAValue);
		tokenFCMap.put(tokenB.asMerkle(), tokenBValue);
		subject = new FcmCustomFeeSchedules(() -> tokenFCMap);
	}

	@Test
	void validateLookUpScheduleFor() {
		//then:
		final var tokenAFees = subject.lookupScheduleFor(tokenA);
		final var tokenBFees = subject.lookupScheduleFor(tokenB);
		final var missingTokenFees = subject.lookupScheduleFor(missingToken);

		//expect:
		Assertions.assertEquals(tokenAValue.customFeeSchedule(), tokenAFees);
		Assertions.assertEquals(tokenBValue.customFeeSchedule(), tokenBFees);
		Assertions.assertEquals(new ArrayList<>(), missingTokenFees);
	}

	@Test
	void getterWorks() {
		Assertions.assertEquals(tokenFCMap, subject.getTokens().get());
	}

	@Test
	void testObjectContract() {
		// given:
		FCMap<MerkleEntityId, MerkleToken> secondFCMap = new FCMap<>();
		MerkleToken token = new MerkleToken();
		final var missingFees = CustomFeesOuterClass.CustomFees
				.newBuilder()
				.addCustomFees(CustomFee.fixedFee(50L, missingToken, feeCollector).asGrpc());
		token.setFeeScheduleFrom(missingFees);
		secondFCMap.put(missingToken.asMerkle(), new MerkleToken());
		final var fees1 = new FcmCustomFeeSchedules(() -> tokenFCMap);
		final var fees2 = new FcmCustomFeeSchedules(() -> secondFCMap);

		// expect:
		assertNotEquals(fees1, fees2);
		assertNotEquals(fees1.hashCode(), fees2.hashCode());
	}
}
