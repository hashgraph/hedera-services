package com.hedera.services.sigs.metadata.lookups;

import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultAccountLookupTest {
	@Mock
	private AliasManager aliasManager;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	
	private DefaultAccountLookup subject;

	@BeforeEach
	void setUp() {
		subject = new DefaultAccountLookup(aliasManager, () -> accounts);
	}

	@Test
	void usesAliasWhenAppropriate() {
		
	}
}