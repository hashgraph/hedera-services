package com.hedera.services.store.contracts;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StaticEntityAccessTest {
	@Mock
	private StateView stateView;
	@Mock
	private HederaAccountCustomizer customizer;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private VirtualMap<ContractKey, ContractValue> storage;
	@Mock
	private VirtualMap<VirtualBlobKey, VirtualBlobValue> blobs;

	private StaticEntityAccess subject;

	private AccountID id = IdUtils.asAccount("0.0.1234");
	private UInt256 uint256Key = UInt256.ONE;
	private ContractKey contractKey = new ContractKey(id.getAccountNum(), uint256Key.toArray());
	private VirtualBlobKey blobKey = new VirtualBlobKey(VirtualBlobKey.Type.CONTRACT_BYTECODE,
			(int) id.getAccountNum());
	private ContractValue contractVal = new ContractValue(BigInteger.ONE);
	private VirtualBlobValue blobVal = new VirtualBlobValue("data".getBytes());


	private MerkleAccount someAccount = new HederaAccountCustomizer()
			.isReceiverSigRequired(false)
			.proxy(EntityId.MISSING_ENTITY_ID)
			.isDeleted(false)
			.expiry(1234L)
			.memo("")
			.isSmartContract(false)
			.autoRenewPeriod(1234L)
			.customizing(new MerkleAccount());

	@BeforeEach
	void setUp() {
		given(stateView.storage()).willReturn(blobs);
		given(stateView.accounts()).willReturn(accounts);
		given(stateView.contractStorage()).willReturn(storage);
		subject = new StaticEntityAccess(stateView);
	}

	@Test
	void mutatorsThrows() {
		assertThrows(UnsupportedOperationException.class, () -> subject.spawn(id, 0l, customizer));
		assertThrows(UnsupportedOperationException.class, () -> subject.customize(id, customizer));
		assertThrows(UnsupportedOperationException.class, () -> subject.adjustBalance(id, 10L));
		assertThrows(UnsupportedOperationException.class, () -> subject.put(id, uint256Key, uint256Key));
		assertThrows(UnsupportedOperationException.class, () -> subject.store(id, uint256Key.toBytes()));
	}

	@Test
	void nonMutatorsWork() {
		given(accounts.get(EntityNum.fromAccountId(id))).willReturn(someAccount);

		assertEquals(someAccount.getBalance(), subject.getBalance(id));
		assertEquals(someAccount.isDeleted(), subject.isDeleted(id));
		assertTrue(subject.isExtant(id));
		assertEquals(someAccount, subject.lookup(id));
	}

	@Test
	void getWorks() {
		given(storage.get(contractKey)).willReturn(contractVal);

		final var unit256Val = subject.get(id, uint256Key);

		final var expectedVal = UInt256.fromBytes(Bytes.wrap(contractVal.getValue()));
		assertEquals(expectedVal, unit256Val);
	}

	@Test
	void getForUnknownReturnsZero() {
		final var unit256Val = subject.get(id, UInt256.MAX_VALUE);

		assertEquals(UInt256.ZERO, unit256Val);
	}

	@Test
	void fetchWithValueWorks() {
		given(blobs.get(blobKey)).willReturn(blobVal);

		final var blobBytes = subject.fetch(id);

		final var expectedVal = Bytes.of(blobVal.getData());
		assertEquals(expectedVal, blobBytes);
	}

	@Test
	void fetchWithoutValueReturnsNull() {
		final var blobBytes = subject.fetch(id);
		assertEquals(Bytes.EMPTY, blobBytes);
	}

}