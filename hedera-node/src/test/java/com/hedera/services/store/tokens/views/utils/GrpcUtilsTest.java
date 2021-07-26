package com.hedera.services.store.tokens.views.utils;

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
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class GrpcUtilsTest {
	private final GrpcUtils subject = new GrpcUtils();

	@Test
	void createsExpectedForTreasuryUsingWildcard() {
		// given:
		final var treasuryNft = new MerkleUniqueToken(wildcard, treasuryMeta, creationTime);

		// when:
		final var actual = subject.reprOf(
				token.toGrpcTokenId(), treasurySerial, treasuryNft, treasury.toGrpcAccountId());

		// then:
		Assertions.assertEquals(expectedTreasury, actual);
	}

	private final long nonTreasurySerial = 1L;
	private final long treasurySerial = 2L;
	private final byte[] treasuryMeta = "As you wish...".getBytes(StandardCharsets.UTF_8);
	private final byte[] nonTreasuryMeta = "...caution to the wind, then.".getBytes(StandardCharsets.UTF_8);
	private final RichInstant creationTime = new RichInstant(1_234_567L, 890);
	private final EntityId token = new EntityId(6, 6, 6);
	private final EntityId wildcard = EntityId.MISSING_ENTITY_ID;
	private final EntityId nonTreasuryOwner = new EntityId(1, 2, 3);
	private final EntityId treasury = new EntityId(2, 3, 4);

	private final TokenNftInfo expectedTreasury = TokenNftInfo.newBuilder()
			.setNftID(NftID.newBuilder()
					.setTokenID(token.toGrpcTokenId())
					.setSerialNumber(treasurySerial))
			.setAccountID(treasury.toGrpcAccountId())
			.setMetadata(ByteString.copyFrom(treasuryMeta))
			.setCreationTime(creationTime.toGrpc())
			.build();
}
