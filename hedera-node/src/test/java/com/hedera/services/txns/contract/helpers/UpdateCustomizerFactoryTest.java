package com.hedera.services.txns.contract.helpers;

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.junit.jupiter.api.Test;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ADMIN_KT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCustomizerFactoryTest {
	private long curExpiry = 1_234_567L;
	private long newExpiry = 2 * curExpiry;
	private ContractID target = IdUtils.asContract("0.0.1234");
	private Key targetKey = Key.newBuilder().setContractID(target).build();
	private Duration newAutoRenew = Duration.newBuilder().setSeconds(654_321L).build();
	private AccountID newProxy = IdUtils.asAccount("0.0.4321");
	private String newMemo = "The commonness of thoughts and images";
	private Key newAdminKey = TxnHandlingScenario.TOKEN_ADMIN_KT.asKey();


	private UpdateCustomizerFactory subject = new UpdateCustomizerFactory();

	@Test
	void makesExpectedChanges() {
		// given:
		var mutableContract = MerkleAccountFactory.newContract()
				.accountKeys(MISC_ADMIN_KT.asJKeyUnchecked())
				.get();
		// and:
		var op = ContractUpdateTransactionBody.newBuilder()
				.setContractID(target)
				.setAdminKey(newAdminKey)
				.setAutoRenewPeriod(newAutoRenew)
				.setProxyAccountID(newProxy)
				.setMemoWrapper(StringValue.newBuilder().setValue(newMemo))
				.setExpirationTime(Timestamp.newBuilder().setSeconds(newExpiry))
				.build();

		// when:
		var result = subject.customizerFor(mutableContract, op);
		// and when:
		mutableContract = result.getLeft().get().customizing(mutableContract);

		// then:
		assertEquals(newAdminKey, MiscUtils.asKeyUnchecked(mutableContract.getKey()));
		assertEquals(newAutoRenew.getSeconds(), mutableContract.getAutoRenewSecs());
		assertEquals(newExpiry, mutableContract.getExpiry());
		assertEquals(newMemo, mutableContract.getMemo());
		assertEquals(newProxy, mutableContract.getProxy().toGrpcAccountId());
	}

	@Test
	void permitsMakingImmutableWithSentinel() {
		// given:
		var mutableContract = MerkleAccountFactory.newContract()
				.accountKeys(MISC_ADMIN_KT.asJKeyUnchecked())
				.get();
		// and:
		var op = ContractUpdateTransactionBody.newBuilder()
				.setContractID(target)
				.setAdminKey(ImmutableKeyUtils.IMMUTABILITY_SENTINEL_KEY)
				.build();

		// when:
		var result = subject.customizerFor(mutableContract, op);
		// and when:
		mutableContract = result.getLeft().get().customizing(mutableContract);

		// then:
		assertEquals(targetKey, MiscUtils.asKeyUnchecked(mutableContract.getKey()));
	}

	@Test
	void disallowsInvalidKey() {
		// given:
		var mutableContract = MerkleAccountFactory.newContract()
				.accountKeys(MISC_ADMIN_KT.asJKeyUnchecked())
				.get();
		// and:
		var op = ContractUpdateTransactionBody.newBuilder()
				.setAdminKey(Key.newBuilder().setEd25519(ByteString.copyFrom("1".getBytes())))
				.build();

		// when:
		var result = subject.customizerFor(mutableContract, op);

		// then:
		assertTrue(result.getLeft().isEmpty());
		assertEquals(INVALID_ADMIN_KEY, result.getRight());
	}

	@Test
	void disallowsExplicitContractKey() {
		// given:
		var mutableContract = MerkleAccountFactory.newContract()
				.accountKeys(MISC_ADMIN_KT.asJKeyUnchecked())
				.get();
		// and:
		var op = ContractUpdateTransactionBody.newBuilder()
				.setAdminKey(Key.newBuilder().setContractID(target))
				.build();

		// when:
		var result = subject.customizerFor(mutableContract, op);

		// then:
		assertTrue(result.getLeft().isEmpty());
		assertEquals(INVALID_ADMIN_KEY, result.getRight());
	}

	@Test
	void refusesToShortenLifetime() {
		// setup:
		long then = curExpiry;

		// given:
		var mutableContract = MerkleAccountFactory.newContract()
				.accountKeys(MISC_ADMIN_KT.asJKeyUnchecked())
				.expirationTime(then)
				.get();
		// and:
		var op = ContractUpdateTransactionBody.newBuilder()
				.setExpirationTime(Timestamp.newBuilder().setSeconds(then - 1).build())
				.build();

		// when:
		var result = subject.customizerFor(mutableContract, op);

		// then:
		assertTrue(result.getLeft().isEmpty());
		assertEquals(EXPIRATION_REDUCTION_NOT_ALLOWED, result.getRight());
	}

	@Test
	void refusesToCustomizeNonExpiryChangeToImmutableContract() {
		// given:
		var immutableContract = MerkleAccountFactory.newContract()
				.accountKeys(new JContractIDKey(0, 0, 2))
				.get();
		// and:
		var op = ContractUpdateTransactionBody.newBuilder()
						.setProxyAccountID(IdUtils.asAccount("0.0.1234"))
						.build();

		// when:
		var result = subject.customizerFor(immutableContract, op);

		// then:
		assertTrue(result.getLeft().isEmpty());
		assertEquals(MODIFYING_IMMUTABLE_CONTRACT, result.getRight());
	}

	@Test
	void accountMutabilityUnderstood() {
		// given:
		var contractWithMissingKey = MerkleAccountFactory.newContract()
				.get();
		var contractWithNotionalKey = MerkleAccountFactory.newContract()
				.accountKeys(new JContractIDKey(0, 0, 2))
				.get();
		var contractWithEd25519Key = MerkleAccountFactory.newContract()
				.accountKeys(TxnHandlingScenario.FIRST_TOKEN_SENDER_KT.asJKeyUnchecked())
				.get();

		// expect:
		assertFalse(subject.isMutable(contractWithMissingKey));
		assertFalse(subject.isMutable(contractWithNotionalKey));
		assertTrue(subject.isMutable(contractWithEd25519Key));
	}

	@Test
	void understandsNonExpiryEffects() {
		// expect:
		assertFalse(subject.onlyAffectsExpiry(ContractUpdateTransactionBody.newBuilder()
				.setAdminKey(ImmutableKeyUtils.IMMUTABILITY_SENTINEL_KEY)
				.build()));
		assertFalse(subject.onlyAffectsExpiry(ContractUpdateTransactionBody.newBuilder()
				.setMemoWrapper(StringValue.newBuilder().setValue("You're not from these parts, are you?"))
				.build()));
		assertFalse(subject.onlyAffectsExpiry(ContractUpdateTransactionBody.newBuilder()
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(123L))
				.build()));
		assertFalse(subject.onlyAffectsExpiry(ContractUpdateTransactionBody.newBuilder()
				.setFileID(IdUtils.asFile("0.0.4321"))
				.build()));
		assertFalse(subject.onlyAffectsExpiry(ContractUpdateTransactionBody.newBuilder()
				.setProxyAccountID(IdUtils.asAccount("0.0.1234"))
				.build()));
		assertTrue(subject.onlyAffectsExpiry(ContractUpdateTransactionBody.newBuilder()
				.setExpirationTime(Timestamp.newBuilder().setSeconds(1_234_567L))
				.build()));
	}

	@Test
	void understandsMemoImpact() {
		// expect:
		assertFalse(subject.affectsMemo(ContractUpdateTransactionBody.getDefaultInstance()));
		assertTrue(subject.affectsMemo(ContractUpdateTransactionBody.newBuilder()
				.setMemo("Hi!").build()));
		assertTrue(subject.affectsMemo(ContractUpdateTransactionBody.newBuilder()
				.setMemoWrapper(StringValue.newBuilder().setValue("Interesting to see you here!")).build()));
	}
}