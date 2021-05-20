package com.hedera.services.store;

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EntityStoreTest {
	@Mock
	private OptionValidator validator;
	@Mock
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	@Mock
	private FCMap<MerkleEntityId, MerkleToken> tokens;
	@Mock
	private TransactionRecordService transactionRecordService;

	private EntityStore subject;

	@BeforeEach
	void setUp() {
		/* Account setup */
		miscMerkleAccount = MerkleAccountFactory.newAccount().balance(balance).expirationTime(expiry).get();
		autoRenewMerkleAccount = MerkleAccountFactory.newAccount().balance(balance).expirationTime(expiry).get();
		treasuryMerkleAccount = new MerkleAccount();

		miscAccount.setExpiry(expiry);
		miscAccount.setBalance(balance);
		autoRenewAccount.setExpiry(expiry);
		autoRenewAccount.setBalance(balance);

		/* Token setup */
		merkleToken = new MerkleToken(
				expiry, tokenSupply, 0,
				symbol, name,
				false, true,
				new EntityId(0, 0, treasuryAccountNum));
		merkleToken.setAutoRenewAccount(new EntityId(0, 0, autoRenewAccountNum));

		token.setTreasury(treasuryAccount);
		token.setAutoRenewAccount(autoRenewAccount);
		token.setTotalSupply(tokenSupply);

		subject = new EntityStore(validator, transactionRecordService, () -> tokens, () -> accounts);
	}

	/* --- Token loading --- */
	@Test
	void loadsExpectedToken() {
		givenAccount(autoRenewMerkleId, autoRenewMerkleAccount);
		givenAccount(treasuryMerkleId, treasuryMerkleAccount);
		givenToken(merkleTokenId, merkleToken);

		// when:
		final var actualToken = subject.loadToken(tokenId);

		// then:
		assertEquals(actualToken, token);
	}

	@Test
	void failsLoadingTokenWithDetachedAutoRenewAccount() throws NegativeAccountBalanceException {
		givenAccount(autoRenewMerkleId, autoRenewMerkleAccount);
		givenToken(merkleTokenId, merkleToken);
		given(validator.isAfterConsensusSecond(expiry)).willReturn(true);
		autoRenewMerkleAccount.setBalance(0L);

		assertTokenLoadFailsWith(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
	}

	@Test
	void failsLoadingMissingToken() {
		assertTokenLoadFailsWith(INVALID_TOKEN_ID);
	}

	@Test
	void failsLoadingDeletedToken() {
		givenToken(merkleTokenId, merkleToken);
		merkleToken.setDeleted(true);

		assertTokenLoadFailsWith(TOKEN_WAS_DELETED);
	}

	/* --- Token saving --- */
	@Test
	void savesTokenAsExpected() {
		// setup:
		final var expectedReplacementToken = new MerkleToken(
						expiry, tokenSupply * 2, 0,
						symbol, name,
						false, true,
						new EntityId(0, 0, autoRenewAccountNum));
		expectedReplacementToken.setAutoRenewAccount(new EntityId(0, 0, treasuryAccountNum));

		givenToken(merkleTokenId, merkleToken);
		givenModifiableToken(merkleTokenId, merkleToken);
		givenAccount(autoRenewMerkleId, autoRenewMerkleAccount);
		givenAccount(treasuryMerkleId, treasuryMerkleAccount);

		// when:
		final var modelToken = subject.loadToken(tokenId);
		// and:
		modelToken.setTotalSupply(tokenSupply * 2);
		modelToken.setAutoRenewAccount(treasuryAccount);
		modelToken.setTreasury(autoRenewAccount);
		// and:
		subject.saveToken(modelToken);

		// then:
		verify(tokens).replace(merkleTokenId, expectedReplacementToken);
		// and:
		verify(transactionRecordService).includeChangesToToken(modelToken);
	}

	/* --- Account loading --- */
	@Test
	void failsLoadingMissingAccount() {
		assertMiscAccountLoadFailsWith(INVALID_ACCOUNT_ID);
	}

	@Test
	void failsLoadingDeleted() {
		givenAccount(miscMerkleId, miscMerkleAccount);
		miscMerkleAccount.setDeleted(true);

		assertMiscAccountLoadFailsWith(ACCOUNT_DELETED);
	}

	@Test
	void failsLoadingDetached() throws NegativeAccountBalanceException {
		givenAccount(miscMerkleId, miscMerkleAccount);
		given(validator.isAfterConsensusSecond(expiry)).willReturn(true);
		miscMerkleAccount.setBalance(0L);

		assertMiscAccountLoadFailsWith(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
	}

	@Test
	void canLoadExpiredWithNonzeroBalance() {
		givenAccount(miscMerkleId, miscMerkleAccount);

		// when:
		final var actualAccount = subject.loadAccount(miscId);

		// then:
		assertEquals(actualAccount, miscAccount);
	}

	private void givenAccount(MerkleEntityId anId, MerkleAccount anAccount) {
		given(accounts.get(anId)).willReturn(anAccount);
	}

	private void givenToken(MerkleEntityId anId, MerkleToken aToken) {
		given(tokens.get(anId)).willReturn(aToken);
	}

	private void givenModifiableToken(MerkleEntityId anId, MerkleToken aToken) {
		given(tokens.getForModify(anId)).willReturn(aToken);
	}

	private void assertMiscAccountLoadFailsWith(ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, () -> subject.loadAccount(miscId));
		assertEquals(status, ex.getResponseCode());
	}

	private void assertTokenLoadFailsWith(ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, () -> subject.loadToken(tokenId));
		assertEquals(status, ex.getResponseCode());
	}

	private final long expiry = 1_234_567L;
	private final long balance = 1_000L;
	private final long miscAccountNum = 1_234L;
	private final long treasuryAccountNum = 2_234L;
	private final long autoRenewAccountNum = 3_234L;
	private final MerkleEntityId miscMerkleId = new MerkleEntityId(0, 0, miscAccountNum);
	private final MerkleEntityId treasuryMerkleId = new MerkleEntityId(0, 0, treasuryAccountNum);
	private final MerkleEntityId autoRenewMerkleId = new MerkleEntityId(0, 0, autoRenewAccountNum);
	private final Id miscId = new Id(0, 0, miscAccountNum);
	private final Id treasuryId = new Id(0, 0, treasuryAccountNum);
	private final Id autoRenewId = new Id(0, 0, autoRenewAccountNum);
	private final Account miscAccount = new Account(miscId);
	private final Account treasuryAccount = new Account(treasuryId);
	private final Account autoRenewAccount = new Account(autoRenewId);

	private final long tokenNum = 4_234L;
	private final long tokenSupply = 777L;
	private final String name = "Testing123";
	private final String symbol = "T123";
	private final MerkleEntityId merkleTokenId = new MerkleEntityId(0, 0, tokenNum);
	private final Id tokenId = new Id(0, 0, tokenNum);
	private final Token token = new Token(tokenId);

	private MerkleToken merkleToken;
	private MerkleAccount miscMerkleAccount;
	private MerkleAccount autoRenewMerkleAccount;
	private MerkleAccount treasuryMerkleAccount;
}