package com.hedera.services.store.models;

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountTest {
	private Id subjectId = new Id(0, 0, 12345);
	private CopyOnWriteIds assocTokens = new CopyOnWriteIds(new long[] { 666, 0, 0, 777, 0, 0 });

	private Account subject;

	@BeforeEach
	void setUp() {
		subject = new Account(subjectId);
		subject.setAssociatedTokens(assocTokens);
	}

	@Test
	void toStringAsExpected() {
		// given:
		final var desired = "Account{id=Id{shard=0, realm=0, num=12345}, expiry=0, balance=0, deleted=false, " +
				"tokens=[0.0.666, 0.0.777]}";

		// expect:
		assertEquals(desired, subject.toString());
	}

	@Test
	void failsOnAssociatingWithAlreadyRelatedToken() {
		// setup:
		final var alreadyAssocToken = new Token(new Id(0, 0, 666));

		// expect:
		assertFailsWith(
				() -> subject.associateWith(List.of(alreadyAssocToken), 100),
				TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);
	}

	@Test
	void cantAssociateWithMoreThanMax() {
		// setup:
		final var firstNewToken = new Token(new Id(0, 0, 888));
		final var secondNewToken = new Token(new Id(0, 0, 999));

		// when:
		assertFailsWith(
				() -> subject.associateWith(List.of(firstNewToken, secondNewToken), 3),
				TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);
	}

	@Test
	void canAssociateWithNewToken() {
		// setup:
		final var firstNewToken = new Token(new Id(0, 0, 888));
		final var secondNewToken = new Token(new Id(0, 0, 999));
		final var expectedFinalTokens = "[0.0.666, 0.0.777, 0.0.888, 0.0.999]";

		// when:
		subject.associateWith(List.of(firstNewToken, secondNewToken), 10);

		// expect:
		assertEquals(expectedFinalTokens, assocTokens.toReadableIdList());
	}

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}
}