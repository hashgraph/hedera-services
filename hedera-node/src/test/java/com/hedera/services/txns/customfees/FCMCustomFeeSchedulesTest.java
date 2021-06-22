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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
public class FCMCustomFeeSchedulesTest {
	private FCMCustomFeeSchedules subject;
	FCMap<MerkleEntityId, MerkleToken> tokenFCMap = new FCMap<>();

	private final EntityId tokenA = new EntityId(0,0,1);
	private final EntityId tokenB = new EntityId(0,0,2);
	private final EntityId feeCollector = new EntityId(0,0,3);
	private final MerkleToken tokenAValue = new MerkleToken();
	private final MerkleToken tokenBValue = new MerkleToken();

	List<CustomFee> tokenAFees = List.of(CustomFee.fixedFee(20L, tokenA, feeCollector));
	List<CustomFee> tokenBFees = List.of(CustomFee.fixedFee(40L, tokenB, feeCollector));

	@Test
	void validateLookUpScheduleFor() {
		//setup:
		tokenAValue.setFeeSchedule(tokenAFees);
		tokenBValue.setFeeSchedule(tokenBFees);

		//given:
		tokenFCMap.put(tokenA.asMerkle(), tokenAValue);
		tokenFCMap.put(tokenB.asMerkle(), tokenBValue);

		//then:
		subject = new FCMCustomFeeSchedules(() -> tokenFCMap);

		final var tokenAFees = subject.lookupScheduleFor(tokenA);
		final var tokenBFees = subject.lookupScheduleFor(tokenB);

		//expect:
		Assertions.assertEquals(tokenAValue.getFeeSchedule(), tokenAFees);
		Assertions.assertEquals(tokenBValue.getFeeSchedule(), tokenBFees);
	}
}
