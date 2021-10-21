package com.hedera.services.store.contracts;

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MutableEntityAccountAccessTest {
	@Mock
	private HederaLedger ledger;
	@Mock
	private VirtualMap<ContractKey, ContractValue> storage;
	@Mock
	private VirtualMap<VirtualBlobKey, VirtualBlobValue> bytecode;

	private MutableEntityAccess subject;

	@BeforeEach
	void setUp() {

	}

	@Test
	void testsSpawn(){

	}
}