package com.hedera.services.state.expiry.renewal;

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountGCTest {
	@Mock
	private AliasManager aliasManager;
	@Mock
	private SigImpactHistorian sigImpactHistorian;
	@Mock
	private BackingStore<AccountID, MerkleAccount> backingAccounts;

	private AccountGC subject;

	@BeforeEach
	void setUp() {
		subject = new AccountGC(aliasManager, sigImpactHistorian, backingAccounts);
	}

	@Test
	void removalWithNoTokensWorks() {
		given(aliasManager.forgetAlias(accountNoTokens.getAlias())).willReturn(true);
		final var expectedReturns = new TreasuryReturns(Collections.emptyList(), Collections.emptyList(), true);

		final var actualReturns = subject.expireBestEffort(num, accountNoTokens);

		assertEquals(expectedReturns, actualReturns);
		assertRemovalStepsTaken(num, accountNoTokens);
	}



	private void assertRemovalStepsTaken(final EntityNum num, final MerkleAccount account) {
		verify(aliasManager).forgetAlias(account.getAlias());
		verify(backingAccounts).remove(num.toGrpcAccountId());
		verify(sigImpactHistorian).markEntityChanged(num.longValue());
	}

	private final long expiredNum = 2L;
	private final long deletedTokenNum = 1234L;
	private final long survivedTokenNum = 4321L;
	private final long missingTokenNum = 5678L;
	private final EntityId expiredTreasuryId = new EntityId(0, 0, expiredNum);
	private final EntityId treasuryId = new EntityId(0, 0, 666L);
	private final MerkleToken deletedToken = new MerkleToken(
			Long.MAX_VALUE, 1L, 0,
			"GONE", "Long lost dream",
			true, true, expiredTreasuryId);
	private final MerkleToken longLivedToken = new MerkleToken(
			Long.MAX_VALUE, 1L, 0,
			"HERE", "Dreams never die",
			true, true, treasuryId);
	private final EntityNum num = EntityNum.fromLong(expiredNum);
	private final AccountID grpcId = num.toGrpcAccountId();
	private final EntityNum deletedTokenId = EntityNum.fromLong(deletedTokenNum);
	private final EntityNum survivedTokenId = EntityNum.fromLong(survivedTokenNum);
	private final EntityNum missingTokenId = EntityNum.fromLong(missingTokenNum);
	private final TokenID deletedTokenGrpcId = deletedTokenId.toGrpcTokenId();
	private final TokenID survivedTokenGrpcId = survivedTokenId.toGrpcTokenId();
	private final TokenID missingTokenGrpcId = missingTokenId.toGrpcTokenId();
	private final ByteString anAlias = ByteString.copyFromUtf8("bbbb");
	private final MerkleAccount accountNoTokens = MerkleAccountFactory.newAccount()
			.balance(0)
			.alias(anAlias)
			.get();
	private final MerkleAccount accountWithTokens = MerkleAccountFactory.newAccount()
			.balance(0)
			.alias(anAlias)
			.get();
	{
		deletedToken.setDeleted(true);
		final var associations = new MerkleAccountTokens();
		associations.associateAll(Set.of(deletedTokenGrpcId, survivedTokenGrpcId, missingTokenGrpcId));
		accountWithTokens.setTokens(associations);
	}
}