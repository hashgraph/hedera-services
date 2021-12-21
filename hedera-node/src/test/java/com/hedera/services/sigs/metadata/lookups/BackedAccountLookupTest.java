package com.hedera.services.sigs.metadata.lookups;

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
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.sigs.order.KeyOrderingFailure;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BackedAccountLookupTest {
	private final AccountID id = IdUtils.asAccount("0.0.3");
	private final MerkleAccount account = MerkleAccountFactory.newAccount()
			.receiverSigRequired(true)
			.accountKeys(TxnHandlingScenario.MISC_ADMIN_KT.asJKeyUnchecked())
			.get();

	@Mock
	private BackingStore<AccountID, MerkleAccount> accounts;
	@Mock
	private AliasManager aliasManager;

	private BackedAccountLookup subject;

	@BeforeEach
	void setUp() {
		subject = new BackedAccountLookup(accounts, aliasManager);
	}

	@Test
	void usesRefForImpureLookup() {
		given(accounts.contains(id)).willReturn(true);
		given(accounts.getImmutableRef(id)).willReturn(account);

		final var result = subject.safeLookup(id);

		assertTrue(result.metadata().receiverSigRequired());
		assertSame(account.getAccountKey(), result.metadata().key());
	}

	@Test
	void toStringAsExpected() {
		given(accounts.contains(id)).willReturn(true);
		given(accounts.getImmutableRef(id)).willReturn(account);

		final var result = subject.safeLookup(id);
		System.out.println(result.toString());

		assertTrue(result.toString().startsWith("SafeLookupResult{failure=NONE, metadata=AccountSigningMetadata["));
	}

	@Test
	void marksFailure() {
		given(accounts.contains(id)).willReturn(false);

		final var result = subject.safeLookup(id);

		assertFalse(result.succeeded());
	}

	@Test
	void usesAliasWhenAppropriate() {
		final var extantId = EntityNum.fromLong(1_234L);
		final var explicitId = AccountID.newBuilder().setAccountNum(1_234L).build();
		final var matchedAlias = AccountID.newBuilder().setAlias(ByteString.copyFromUtf8("abcd")).build();
		final var unmatchedAlias = AccountID.newBuilder().setAlias(ByteString.copyFromUtf8("dcba")).build();

		given(accounts.contains(explicitId)).willReturn(true);
		given(aliasManager.lookupIdBy(matchedAlias.getAlias())).willReturn(extantId);
		given(aliasManager.lookupIdBy(unmatchedAlias.getAlias())).willReturn(MISSING_NUM);
		given(accounts.getImmutableRef(explicitId)).willReturn(account);

		final var explicitResult = subject.aliasableSafeLookup(explicitId);
		final var matchedResult = subject.aliasableSafeLookup(matchedAlias);
		final var unmatchedResult = subject.aliasableSafeLookup(unmatchedAlias);

		assertTrue(explicitResult.succeeded());
		Assertions.assertSame(account.getAccountKey(), explicitResult.metadata().key());

		assertTrue(matchedResult.succeeded());
		Assertions.assertSame(account.getAccountKey(), matchedResult.metadata().key());

		assertFalse(unmatchedResult.succeeded());
		assertEquals(KeyOrderingFailure.MISSING_ACCOUNT, unmatchedResult.failureIfAny());
	}
}
