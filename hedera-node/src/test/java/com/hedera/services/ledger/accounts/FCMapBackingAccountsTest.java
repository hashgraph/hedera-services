package com.hedera.services.ledger.accounts;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class FCMapBackingAccountsTest {
	private final AccountID a = IdUtils.asAccount("0.0.2");
	private final MerkleEntityId aKey = MerkleEntityId.fromAccountId(a);
	private final AccountID b = IdUtils.asAccount("0.0.3");
	private final MerkleEntityId bKey = MerkleEntityId.fromAccountId(b);

	private final MerkleAccount aAccount = MerkleAccountFactory.newAccount().balance(Long.MAX_VALUE).get();

	@Mock
	private FCMap<MerkleEntityId, MerkleAccount> accounts;

	private FCMapBackingAccounts subject;

	@BeforeEach
	void setUp() {
		subject = new FCMapBackingAccounts(() -> accounts);
	}

	@Test
	void rebuildIsNoop() {
		// when:
		subject.rebuildFromSources();

		Mockito.verifyNoInteractions(accounts);
	}

	@Test
	void usesGetWhenFetchingUnsafeRef() {
		given(accounts.get(aKey)).willReturn(aAccount);

		// then:
		Assertions.assertSame(aAccount, subject.getUnsafeRef(a));
	}

	@Test
	void usesCachedG4mWhenFetchingRef() {
		given(accounts.getForModify(aKey)).willReturn(aAccount);

		// when:
		final var firstA = subject.getRef(a);
		final var secondA = subject.getRef(a);

		// then:
		Assertions.assertSame(firstA, secondA);
		Assertions.assertSame(secondA, aAccount);
		verify(accounts, times(1)).getForModify(aKey);
	}

	@Test
	void putsIfNotAlreadyContained() {
		// when:
		subject.put(a, aAccount);

		// then:
		verify(accounts).put(aKey, aAccount);
	}

	@Test
	void doesntPutIfAlreadyContained() {
		given(accounts.containsKey(aKey)).willReturn(true);

		// when:
		subject.put(a, aAccount);

		// then:
		verify(accounts, never()).put(aKey, aAccount);
	}

	@Test
	void delegatesContains() {
		given(accounts.containsKey(aKey)).willReturn(true);

		// then:
		Assertions.assertTrue(subject.contains(a));
	}

	@Test
	void delegatesRemove() {
		// when:
		subject.remove(a);

		// then:
		verify(accounts).remove(aKey);
	}

	@Test
	void createsKeySet() {
		given(accounts.keySet()).willReturn(Set.of(aKey, bKey));

		// when:
		var keys = subject.idSet();

		// then:
		Assertions.assertEquals(Set.of(a, b), keys);
	}
}