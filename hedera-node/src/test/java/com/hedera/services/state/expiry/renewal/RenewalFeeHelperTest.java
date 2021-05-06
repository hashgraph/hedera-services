package com.hedera.services.state.expiry.renewal;

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class RenewalFeeHelperTest {
	private final long startBalance = 1_234L;
	private final MerkleEntityId fundingKey = new MerkleEntityId(0, 0, 98);

	private final MerkleAccount fundingAccountPreCycle = MerkleAccountFactory.newAccount()
			.balance(startBalance)
			.get();
	private final MerkleAccount fundingAccountPostCycle = MerkleAccountFactory.newAccount()
			.balance(startBalance + 55L)
			.get();

	@Mock
	private FCMap<MerkleEntityId, MerkleAccount> accounts;

	private GlobalDynamicProperties properties = new MockGlobalDynamicProps();

	private RenewalFeeHelper subject;

	@BeforeEach
	void setUp() {
		subject = new RenewalFeeHelper(properties, () -> accounts);
	}

	@Test
	void abortsIfNoFeesToCharge() {
		// when:
		subject.endChargingCycle();

		// then:
		verifyNoInteractions(accounts);
	}

	@Test
	void chargesAsExpectedInCycle() {
		long expected = 0;
		setupFundingAccount();

		// given:
		subject.beginChargingCycle();
		// and:
		for (long i = 1; i <= 10; i++) {
			subject.recordCharged(i);
			expected += i;
			assertEquals(expected, subject.getTotalFeesCharged());
		}
		// and:
		subject.endChargingCycle();
		assertEquals(0L, subject.getTotalFeesCharged());

		// then:
		verify(accounts).getForModify(fundingKey);
		verify(accounts).replace(fundingKey, fundingAccountPostCycle);
	}

	private void setupFundingAccount() {
		given(accounts.getForModify(fundingKey)).willReturn(fundingAccountPreCycle);
	}
}