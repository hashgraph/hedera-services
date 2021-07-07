package com.hedera.services.fees.calculation.utils;

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
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.services.state.submerkle.FcCustomFee.fixedFee;
import static com.hedera.services.state.submerkle.FcCustomFee.fractionalFee;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
class OpUsageCtxHelperTest {
	private final long now = 1_234_567L;
	private final MerkleToken extant = new MerkleToken(now, 1, 2,
			"Three", "FOUR", false, true,
			EntityId.MISSING_ENTITY_ID);
	private final TokenID target = IdUtils.asToken("1.2.3");
	private final TokenOpsUsage tokenOpsUsage = new TokenOpsUsage();

	@Mock
	private FCMap<MerkleEntityId, MerkleToken> tokens;

	private OpUsageCtxHelper subject;

	@BeforeEach
	void setUp() {
		subject = new OpUsageCtxHelper(() -> tokens);
	}

	@Test
	void returnsZerosForMissingToken() {
		// setup:
		extant.setFeeSchedule(fcFees());

		// when:
		final var ctx = subject.ctxForFeeScheduleUpdate(op());

		// then:
		assertEquals(0L, ctx.expiry());
		assertEquals(0, ctx.numBytesInFeeScheduleRepr());
	}

	@Test
	void returnsExpectedCtxForExtantToken() {
		// setup:
		extant.setFeeSchedule(fcFees());
		final var expBytes = tokenOpsUsage.bytesNeededToRepr(1, 2, 3);

		given(tokens.get(MerkleEntityId.fromTokenId(target))).willReturn(extant);

		// when:
		final var ctx = subject.ctxForFeeScheduleUpdate(op());

		// then:
		assertEquals(now, ctx.expiry());
		assertEquals(expBytes, ctx.numBytesInFeeScheduleRepr());
	}

	private TokenFeeScheduleUpdateTransactionBody op() {
		return TokenFeeScheduleUpdateTransactionBody.newBuilder()
				.setTokenId(target)
				.build();
	}

	private List<FcCustomFee> fcFees() {
		final var collector = new EntityId(1, 2 ,3);
		final var aDenom = new EntityId(2, 3 ,4);
		final var bDenom = new EntityId(3, 4 ,5);

		return List.of(
				fixedFee(1, null, collector),
				fixedFee(2, aDenom, collector),
				fixedFee(2, bDenom, collector),
				fractionalFee(1, 2, 1, 2, collector),
				fractionalFee(1, 3, 1, 2, collector),
				fractionalFee(1, 4, 1, 2, collector)
		);
	}
}
