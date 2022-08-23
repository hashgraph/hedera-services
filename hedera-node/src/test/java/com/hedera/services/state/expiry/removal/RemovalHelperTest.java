package com.hedera.services.state.expiry.removal;

import com.google.protobuf.ByteString;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.expiry.EntityProcessResult;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.state.expiry.classification.EntityLookup;
import com.hedera.services.state.expiry.renewal.RenewalRecordsHelper;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RemovalHelperTest {
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock private AliasManager aliasManager;
	private final MockGlobalDynamicProps properties = new MockGlobalDynamicProps();
	@Mock private ContractGC contractGC;
	@Mock private AccountGC accountGC;
	@Mock private RenewalRecordsHelper recordsHelper;

	private EntityLookup lookup;
	private ClassificationWork classifier;
	private RemovalHelper subject;

	@BeforeEach
	void setUp() {
		lookup = new EntityLookup(() -> accounts);
		classifier = new ClassificationWork(properties, lookup);
		subject = new RemovalHelper(classifier, properties, contractGC,accountGC, recordsHelper);
	}

	@Test
	void doesNothingWhenDisabled(){
		properties.disableAutoRenew();
		var result = subject.tryToRemoveAccount(EntityNum.fromLong(nonExpiredAccountNum));
		assertEquals(EntityProcessResult.NOTHING_TO_DO, result);

		properties.disableContractAutoRenew();
		result = subject.tryToRemoveContract(EntityNum.fromLong(nonExpiredAccountNum));
		assertEquals(EntityProcessResult.NOTHING_TO_DO, result);
	}

	@Test
	void removesAccountAsExpected(){
		properties.enableAutoRenew();

		final var expectedReturns =
				new TreasuryReturns(Collections.emptyList(), Collections.emptyList(), true);

		var result = subject.tryToRemoveAccount(EntityNum.fromLong(nonExpiredAccountNum));
		assertEquals(EntityProcessResult.DONE, result);
	}

	private final long now = 1_234_567L;
	private final long requestedRenewalPeriod = 3601L;
	private final long nonZeroBalance = 2L;

	private final MerkleAccount mockAccount =
			MerkleAccountFactory.newAccount()
					.autoRenewPeriod(requestedRenewalPeriod)
					.balance(nonZeroBalance)
					.expirationTime(now - 1)
					.get();
	private final MerkleAccount mockContract =
			MerkleAccountFactory.newContract()
					.autoRenewPeriod(requestedRenewalPeriod)
					.balance(nonZeroBalance)
					.expirationTime(now - 1)
					.get();
	private final long nonExpiredAccountNum = 1002L;

	private final MerkleAccount expiredDeletedAccount =
			MerkleAccountFactory.newAccount()
					.balance(0)
					.deleted(true)
					.alias(ByteString.copyFromUtf8("cccc"))
					.expirationTime(now - 1)
					.get();
	private final MerkleAccount expiredDeletedContract =
			MerkleAccountFactory.newAccount()
					.isSmartContract(true)
					.balance(0)
					.deleted(true)
					.alias(ByteString.copyFromUtf8("cccc"))
					.expirationTime(now - 1)
					.get();

	private final MerkleAccount autoRenewMerkleAccountZeroBalance =
			MerkleAccountFactory.newAccount()
					.balance(0)
					.expirationTime(now + 1)
					.alias(ByteString.copyFromUtf8("aaaa"))
					.get();
}
