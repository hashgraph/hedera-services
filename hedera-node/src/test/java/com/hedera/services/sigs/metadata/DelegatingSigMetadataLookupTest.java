package com.hedera.services.sigs.metadata;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.metadata.lookups.SafeLookupResult;
import com.hedera.services.sigs.order.KeyOrderingFailure;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.tokens.TokenStore;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class DelegatingSigMetadataLookupTest {
	JKey adminKey;
	JKey freezeKey;
	String symbol = "NotAnHbar";
	String tokenName = "TokenName";
	int divisibility = 2;
	long tokenFloat = 1_000_000;
	boolean freezeDefault = true;
	boolean kycDefault = true;
	EntityId treasury = new EntityId(1,2, 3);
	TokenRef ref = IdUtils.asIdRef("1.2.666");

	MerkleToken token;
	TokenStore tokenStore;

	Function<TokenRef, SafeLookupResult<TokenSigningMetadata>> subject;

	@BeforeEach
	public void setup() {
		adminKey = new JEd25519Key("not-a-real-admin-key".getBytes());
		freezeKey = new JEd25519Key("not-a-real-freeze-key".getBytes());

		token = new MerkleToken(Long.MAX_VALUE, tokenFloat, divisibility, symbol, tokenName,  freezeDefault, kycDefault, treasury);

		tokenStore = mock(TokenStore.class);

		subject = SigMetadataLookup.REF_LOOKUP_FACTORY.apply(tokenStore);
	}

	@Test
	public void returnsExpectedFailIfMissing() {
		given(tokenStore.resolve(ref)).willReturn(TokenStore.MISSING_TOKEN);

		// when:
		var result = subject.apply(ref);

		// then:
		assertEquals(KeyOrderingFailure.MISSING_TOKEN, result.failureIfAny());
	}

	@Test
	public void returnsExpectedMetaIfPresent() {
		// setup:
		token.setFreezeKey(freezeKey);
		var expected = TokenSigningMetadata.from(token);

		given(tokenStore.resolve(ref)).willReturn(ref.getTokenId());
		given(tokenStore.get(ref.getTokenId())).willReturn(token);

		// when:
		var result = subject.apply(ref);

		// then:
		assertEquals(KeyOrderingFailure.NONE, result.failureIfAny());
		// and:
		assertEquals(expected.adminKey(), result.metadata().adminKey());
		assertEquals(expected.optionalFreezeKey(), result.metadata().optionalFreezeKey());
	}
}