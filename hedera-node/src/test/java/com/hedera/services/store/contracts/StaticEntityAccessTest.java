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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaticEntityAccessTest {
	@Mock
	private StateView stateView;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private VirtualMap<ContractKey, ContractValue> storage;
	@Mock
	private VirtualMap<VirtualBlobKey, VirtualBlobValue> blobs;

	private StaticEntityAccess subject;

	private AccountID id = IdUtils.asAccount("0.0.1234");
	MerkleAccount someAccount = new HederaAccountCustomizer()
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
		given(accounts.get(EntityNum.fromAccountId(id))).willReturn(someAccount);

		subject = new StaticEntityAccess(stateView);
	}

	// need a lot of test coverage. Adding test to resolve sonar issues
	@Test
	void testBalance() {
		final var balance = subject.getBalance(id);
		assertEquals(someAccount.getBalance(), balance);
	}
}