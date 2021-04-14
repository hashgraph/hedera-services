package com.hedera.services.txns.contract.helpers;

import com.google.protobuf.StringValue;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCustomizerFactoryTest {
	UpdateCustomizerFactory subject = new UpdateCustomizerFactory();

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