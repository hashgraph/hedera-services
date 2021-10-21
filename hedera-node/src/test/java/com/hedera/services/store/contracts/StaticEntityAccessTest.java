package com.hedera.services.store.contracts;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;

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

	@BeforeEach
	void setUp() {
		given(stateView.storage()).willReturn(blobs);
		given(stateView.accounts()).willReturn(accounts);
		given(stateView.contractStorage()).willReturn(storage);

		subject = new StaticEntityAccess(stateView);
	}

	@Test
	void testsSpawn() {

	}
}