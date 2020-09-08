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
import com.hedera.services.sigs.factories.PlatformSigFactory;
import com.hedera.services.sigs.metadata.lookups.SafeLookupResult;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.KeyOrderingFailure;
import com.hedera.services.sigs.order.SigStatusOrderResultFactory;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytesProvider;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.txns.PlatformTxnFactory;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.legacy.crypto.SignatureStatusCode;
import com.hedera.services.legacy.exception.KeySignatureCountMismatchException;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.VerificationStatus;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.state.merkle.MerkleEntityId.fromTokenId;
import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.services.sigs.HederaToPlatformSigOps.*;
import static com.hedera.test.factories.txns.SystemDeleteFactory.*;
import static com.hedera.services.sigs.Rationalization.IN_HANDLE_SUMMARY_FACTORY;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.test.factories.sigs.SigWrappers.asValid;
import static com.hedera.test.factories.sigs.SyncVerifiers.ALWAYS_VALID;

@RunWith(JUnitPlatform.class)
class DelegatingSigMetadataLookupTest {
	JKey adminKey;
	JKey freezeKey;
	String symbol = "NotAnHbar";
	int divisibility = 2;
	long tokenFloat = 1_000_000;
	boolean freezeDefault = true;
	EntityId treasury = new EntityId(1,2, 3);
	TokenID target = IdUtils.asToken("1.2.666");

	FCMap<MerkleEntityId, MerkleToken> tokens;
	MerkleToken token;

	Function<TokenID, SafeLookupResult<TokenSigningMetadata>> subject;

	@BeforeEach
	public void setup() {
		adminKey = new JEd25519Key("not-a-real-admin-key".getBytes());
		freezeKey = new JEd25519Key("not-a-real-freeze-key".getBytes());

		token = new MerkleToken(tokenFloat, divisibility, adminKey, symbol, freezeDefault, treasury);

		tokens = mock(FCMap.class);

		subject = DelegatingSigMetadataLookup.DEFAULT_TOKEN_LOOKUP_FACTORY.apply(() -> tokens);
	}

	@Test
	public void returnsExpectedFailIfMissing() {
		given(tokens.get(fromTokenId(target))).willReturn(null);

		// when:
		var result = subject.apply(target);

		// then:
		assertEquals(KeyOrderingFailure.MISSING_TOKEN, result.failureIfAny());
	}

	@Test
	public void returnsExpectedMetaIfPresent() {
		// setup:
		token.setFreezeKey(freezeKey);
		var expected = TokenSigningMetadata.from(token);

		given(tokens.get(fromTokenId(target))).willReturn(token);

		// when:
		var result = subject.apply(target);

		// then:
		assertEquals(KeyOrderingFailure.NONE, result.failureIfAny());
		// and:
		assertEquals(expected.adminKey(), result.metadata().adminKey());
		assertEquals(expected.optionalFreezeKey(), result.metadata().optionalFreezeKey());
	}
}