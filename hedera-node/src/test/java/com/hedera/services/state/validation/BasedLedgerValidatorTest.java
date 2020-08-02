package com.hedera.services.state.validation;

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.exception.NegativeAccountBalanceException;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class BasedLedgerValidatorTest {
	private long shard = 1;
	private long realm = 2;

	FCMap<MerkleEntityId, MerkleAccount> accounts = new FCMap<>();

	HederaNumbers hederaNums;
	PropertySource properties;

	BasedLedgerValidator subject;

	@BeforeEach
	private void setup() {
		hederaNums = mock(HederaNumbers.class);
		given(hederaNums.realm()).willReturn(realm);
		given(hederaNums.shard()).willReturn(shard);

		properties = mock(PropertySource.class);
		given(properties.getLongProperty("ledger.maxAccountNum")).willReturn(5L);
		given(properties.getLongProperty("ledger.totalHbarFloat")).willReturn(100L);

		subject = new BasedLedgerValidator(hederaNums, properties);
	}

	@Test
	public void recognizesRightFloat() throws NegativeAccountBalanceException {
		// given:
		accounts.put(new MerkleEntityId(shard, realm, 1L), expectedWith(50L));
		accounts.put(new MerkleEntityId(shard, realm, 2L), expectedWith(50L));

		// expect:
		assertTrue(subject.hasExpectedTotalBalance(accounts));
	}

	@Test
	public void recognizesWrongFloat() throws NegativeAccountBalanceException {
		// given:
		accounts.put(new MerkleEntityId(shard, realm, 1L), expectedWith(50L));
		accounts.put(new MerkleEntityId(shard, realm, 2L), expectedWith(51L));

		// expect:
		assertFalse(subject.hasExpectedTotalBalance(accounts));
	}

	@Test
	public void doesntThrowWithValidIds() throws NegativeAccountBalanceException {
		// given:
		accounts.put(new MerkleEntityId(shard, realm, 3L), expectedWith(100L));

		// expect:
		assertDoesNotThrow(() -> subject.assertIdsAreValid(accounts));
	}

	@Test
	public void throwsOnIdWithInvalidShard() throws NegativeAccountBalanceException {
		// given:
		accounts.put(new MerkleEntityId(shard - 1, realm, 3L), expectedWith(100L));

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.assertIdsAreValid(accounts));
	}

	@Test
	public void throwsOnIdWithNumTooSmall() throws NegativeAccountBalanceException {
		// given:
		accounts.put(new MerkleEntityId(shard, realm, 0L), expectedWith(100L));

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.assertIdsAreValid(accounts));
	}

	@Test
	public void throwsOnIdWithNumTooLarge() throws NegativeAccountBalanceException {
		// given:
		accounts.put(new MerkleEntityId(shard, realm, 6L), expectedWith(100L));

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.assertIdsAreValid(accounts));
	}

	@Test
	public void throwsOnIdWithInvalidRealm() throws NegativeAccountBalanceException {
		// given:
		accounts.put(new MerkleEntityId(shard, realm - 1, 3L), expectedWith(100L));

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.assertIdsAreValid(accounts));
	}

	private MerkleAccount expectedWith(long balance) throws NegativeAccountBalanceException {
		MerkleAccount hAccount = new HederaAccountCustomizer()
				.fundsSentRecordThreshold(123)
				.fundsReceivedRecordThreshold(123)
				.isReceiverSigRequired(false)
				.proxy(MISSING_ENTITY_ID)
				.isDeleted(false)
				.expiry(1_234_567L)
				.memo("")
				.isSmartContract(false)
				.customizing(new MerkleAccount());
		hAccount.setBalance(balance);
		return hAccount;
	}
}