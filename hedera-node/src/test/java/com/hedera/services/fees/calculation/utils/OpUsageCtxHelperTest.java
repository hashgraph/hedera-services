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

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.state.submerkle.FixedFeeSpec;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.state.submerkle.FcCustomFee.fixedFee;
import static com.hedera.services.state.submerkle.FcCustomFee.fractionalFee;
import static com.hedera.services.state.submerkle.FcCustomFee.royaltyFee;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
class OpUsageCtxHelperTest {
	private final int newFileBytes = 1234;
	private final long now = 1_234_567L;
	private final long then = 1_234_567L + 7776000L;
	private final FileID targetFile = IdUtils.asFile("0.0.123456");
	private final JKeyList wacl = new JKeyList(List.of(
			new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8))));
	private final MerkleToken extant = new MerkleToken(now, 1, 2,
			"shLong.asPhlThree", "FOUR", false, true,
			EntityId.MISSING_ENTITY_ID);
	private final TokenID target = IdUtils.asToken("1.2.3");
	private final TokenOpsUsage tokenOpsUsage = new TokenOpsUsage();
	private final HFileMeta fileMeta = new HFileMeta(false, wacl, then);
	private final TransactionBody appendTxn = TransactionBody.newBuilder()
			.setTransactionID(TransactionID.newBuilder()
					.setTransactionValidStart(Timestamp.newBuilder()
							.setSeconds(now)))
			.setFileAppend(
					FileAppendTransactionBody.newBuilder()
							.setFileID(targetFile)
							.setContents(ByteString.copyFrom(new byte[newFileBytes])))
			.build();

	@Mock
	private MerkleMap<EntityNum, MerkleToken> tokens;
	@Mock
	private StateView workingView;

	private OpUsageCtxHelper subject;

	@BeforeEach
	void setUp() {
		subject = new OpUsageCtxHelper(workingView, () -> tokens);
	}

	@Test
	void returnsKnownExpiry() {
		given(workingView.attrOf(targetFile)).willReturn(Optional.of(fileMeta));

		// when:
		final var opMeta = subject.metaForFileAppend(appendTxn);

		// then:
		assertEquals(newFileBytes, opMeta.getBytesAdded());
		assertEquals(then - now, opMeta.getLifetime());
	}

	@Test
	void returnsZeroLifetimeForUnknownExpiry() {
		// when:
		final var opMeta = subject.metaForFileAppend(appendTxn);

		// then:
		assertEquals(newFileBytes, opMeta.getBytesAdded());
		assertEquals(0, opMeta.getLifetime());
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
		final var expBytes = tokenOpsUsage.bytesNeededToRepr(1, 2, 3, 1, 1, 1);

		given(tokens.get(EntityNum.fromTokenId(target))).willReturn(extant);

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
		final var collector = new EntityId(1, 2, 3);
		final var aDenom = new EntityId(2, 3, 4);
		final var bDenom = new EntityId(3, 4, 5);

		return List.of(
				fixedFee(1, null, collector),
				fixedFee(2, aDenom, collector),
				fixedFee(2, bDenom, collector),
				fractionalFee(
						1, 2, 1, 2, false, collector),
				fractionalFee(
						1, 3, 1, 2, false, collector),
				fractionalFee(
						1, 4, 1, 2, false, collector),
				royaltyFee(1, 10, null, collector),
				royaltyFee(1, 10,
						new FixedFeeSpec(1, null), collector),
				royaltyFee(1, 10,
						new FixedFeeSpec(1, aDenom), collector)
		);
	}
}
