package com.hedera.services.sigs.metadata.lookups;

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.sigs.order.KeyOrderingFailure;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DefaultAccountLookupTest {
	@Mock
	private AliasManager aliasManager;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	
	private DefaultAccountLookup subject;

	@BeforeEach
	void setUp() {
		subject = new DefaultAccountLookup(aliasManager, () -> accounts);
	}

	@Test
	void usesAliasWhenAppropriate() {
		final var targetAccount = new MerkleAccount();
		targetAccount.setAccountKey(EMPTY_KEY);

		final var extantId = EntityNum.fromLong(1_234L);
		final var explicitId = AccountID.newBuilder().setAccountNum(1_234L).build();
		final var matchedAlias = AccountID.newBuilder().setAlias(ByteString.copyFromUtf8("abcd")).build();
		final var unmatchedAlias = AccountID.newBuilder().setAlias(ByteString.copyFromUtf8("dcba")).build();

		given(accounts.get(extantId)).willReturn(targetAccount);
		given(aliasManager.lookupByAlias(matchedAlias.getAlias())).willReturn(extantId);
		given(aliasManager.lookupByAlias(unmatchedAlias.getAlias())).willReturn(MISSING_NUM);

		final var explicitResult = subject.aliasableSafeLookup(explicitId);
		final var matchedResult = subject.aliasableSafeLookup(matchedAlias);
		final var unmatchedResult = subject.aliasableSafeLookup(unmatchedAlias);

		assertTrue(explicitResult.succeeded());
		Assertions.assertSame(EMPTY_KEY, explicitResult.metadata().getKey());

		assertTrue(matchedResult.succeeded());
		Assertions.assertSame(EMPTY_KEY, matchedResult.metadata().getKey());

		assertFalse(unmatchedResult.succeeded());
		assertEquals(KeyOrderingFailure.MISSING_ACCOUNT, unmatchedResult.failureIfAny());
	}
}