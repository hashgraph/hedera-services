package com.hedera.services.sigs.metadata.lookups;

import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BackedAccountLookupTest {
	private final AccountID id = IdUtils.asAccount("1.2.3");
	private final MerkleAccount account = MerkleAccountFactory.newAccount()
			.receiverSigRequired(true)
			.accountKeys(TxnHandlingScenario.MISC_ADMIN_KT.asJKeyUnchecked())
			.get();

	@Mock
	private BackingStore<AccountID, MerkleAccount> accounts;

	private BackedAccountLookup subject;

	@BeforeEach
	void setUp() {
		subject = new BackedAccountLookup(accounts);
	}

	@Test
	void usesUnsafeRefForPureLookup() {
		given(accounts.contains(id)).willReturn(true);
		given(accounts.getUnsafeRef(id)).willReturn(account);

		// when:
		final var result = subject.pureSafeLookup(id);

		// then:
		assertTrue(result.metadata().isReceiverSigRequired());
		assertSame(account.getKey(), result.metadata().getKey());
	}

	@Test
	void usesRefForImpureLookup() {
		given(accounts.contains(id)).willReturn(true);
		given(accounts.getRef(id)).willReturn(account);

		// when:
		final var result = subject.safeLookup(id);

		// then:
		assertTrue(result.metadata().isReceiverSigRequired());
		assertSame(account.getKey(), result.metadata().getKey());
	}
}