package com.hedera.services.store.tokens.unique;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.Test;

import static com.hedera.services.store.tokens.unique.ExceptionalTokenUniqueStore.NOOP_UNIQUE_TOKEN_STORE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExceptionalTokenUniqueStoreTest {
	@Test
	public void allButSetAreUse() {
		// expect:
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.nftExists(null));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.mint(null, 0));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.mint(null, null, null));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.listOfTokensServed(null));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.freeze(null, null));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.unfreeze(null, null));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.adjustBalance(null, null, 0));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.createProvisionally(null, null, 0));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.exists(null));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.get((NftID) null));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.get((TokenID) null));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.update(null, 0));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.isKnownTreasury(null));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.grantKyc(null, null));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.revokeKyc(null, null));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.apply(null, token -> {
		}));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.burn(null, 0L));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.wipe(null, null, 0L, false));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.associate(null, null));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.dissociate(null, null));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.associationExists(null, null));
		assertThrows(UnsupportedOperationException.class, () -> NOOP_UNIQUE_TOKEN_STORE.isTreasuryForToken(null, null));
		assertThrows(UnsupportedOperationException.class, NOOP_UNIQUE_TOKEN_STORE::commitCreation);
		assertThrows(UnsupportedOperationException.class, NOOP_UNIQUE_TOKEN_STORE::rollbackCreation);
		assertThrows(UnsupportedOperationException.class, NOOP_UNIQUE_TOKEN_STORE::isCreationPending);
		// and:
		assertDoesNotThrow(() -> NOOP_UNIQUE_TOKEN_STORE.setAccountsLedger(null));
		assertDoesNotThrow(() -> NOOP_UNIQUE_TOKEN_STORE.setHederaLedger(null));
	}
}
