package com.hedera.services.ledger.interceptors;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.NftNumPair;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.utils.NftNumPair.MISSING_NFT_NUM_PAIR;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UniqueTokensLinkManagerTest {
	private MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
	private MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens = new MerkleMap<>();

	private UniqueTokensLinkManager subject;

	@BeforeEach
	void setUp() {
		subject = new UniqueTokensLinkManager(() -> accounts, () -> uniqueTokens);
	}

	@Test
	void updatesOldOwnersHeadAsExpected() {
		setUpEntities();
		setUpMaps();

		assertEquals(tokenNum, accounts.get(oldOwner).getHeadNftId());
		assertEquals(serialNum1, accounts.get(oldOwner).getHeadNftSerialNum());

		subject.updateLinks(oldOwner, newOwner, nftKey1, false, false);

		assertEquals(MISSING_NFT_NUM_PAIR, uniqueTokens.get(nftKey2).getPrev());
		assertEquals(MISSING_NFT_NUM_PAIR, uniqueTokens.get(nftKey1).getNext());
		assertEquals(tokenNum, accounts.get(newOwner).getHeadNftId());
		assertEquals(serialNum1, accounts.get(newOwner).getHeadNftSerialNum());
		assertEquals(tokenNum, accounts.get(oldOwner).getHeadNftId());
		assertEquals(serialNum2, accounts.get(oldOwner).getHeadNftSerialNum());
	}

	@Test
	void updatesLinksAsExpected() {
		setUpEntities();
		setUpMaps();

		assertEquals(tokenNum, accounts.get(oldOwner).getHeadNftId());
		assertEquals(serialNum1, accounts.get(oldOwner).getHeadNftSerialNum());

		subject.updateLinks(oldOwner, newOwner, nftKey2, false, false);

		assertEquals(nftNumPair1, uniqueTokens.get(nftKey3).getPrev());
		assertEquals(nftNumPair3, uniqueTokens.get(nftKey1).getNext());
		assertEquals(tokenNum, accounts.get(newOwner).getHeadNftId());
		assertEquals(serialNum2, accounts.get(newOwner).getHeadNftSerialNum());
		assertEquals(tokenNum, accounts.get(oldOwner).getHeadNftId());
		assertEquals(serialNum1, accounts.get(oldOwner).getHeadNftSerialNum());
	}

	@Test
	void fromTreasuryDoesntUpdateTreasuryAccountLinks() {
		newOwnerAccount.setHeadNftId(tokenNum);
		newOwnerAccount.setHeadNftSerialNum(2L);
		nft1.setNext(MISSING_NFT_NUM_PAIR);
		nft1.setPrev(MISSING_NFT_NUM_PAIR);
		nft2.setNext(MISSING_NFT_NUM_PAIR);
		nft2.setPrev(MISSING_NFT_NUM_PAIR);
		setUpMaps();

		assertDoesNotThrow(() -> subject.updateLinks(treasury, newOwner, nftKey1, true, false));

		assertEquals(tokenNum, accounts.get(newOwner).getHeadNftId());
		assertEquals(serialNum1, accounts.get(newOwner).getHeadNftSerialNum());
		assertEquals(nftNumPair1, uniqueTokens.get(nftKey2).getPrev());
		assertEquals(nftNumPair2, uniqueTokens.get(nftKey1).getNext());
	}

	@Test
	void toTreasuryDoesntUpdateTreasuryAccountLinks() {
		setUpEntities();
		setUpMaps();

		assertDoesNotThrow(() -> subject.updateLinks(oldOwner, treasury, nftKey2, false, true));

		assertEquals(nftNumPair1, uniqueTokens.get(nftKey3).getPrev());
		assertEquals(nftNumPair3, uniqueTokens.get(nftKey1).getNext());
		assertEquals(tokenNum, accounts.get(oldOwner).getHeadNftId());
		assertEquals(serialNum1, accounts.get(oldOwner).getHeadNftSerialNum());
	}

	void setUpEntities() {
		oldOwnerAccount.setHeadNftId(tokenNum);
		oldOwnerAccount.setHeadNftSerialNum(serialNum1);
		nft1.setPrev(MISSING_NFT_NUM_PAIR);
		nft1.setNext(nftNumPair2);
		nft2.setPrev(nftNumPair1);
		nft2.setNext(nftNumPair3);
		nft3.setPrev(nftNumPair2);
		nft3.setNext(MISSING_NFT_NUM_PAIR);
	}

	void setUpMaps() {
		accounts.put(oldOwner, oldOwnerAccount);
		accounts.put(newOwner, newOwnerAccount);
		uniqueTokens.put(nftKey1, nft1);
		uniqueTokens.put(nftKey2, nft2);
		uniqueTokens.put(nftKey3, nft3);
	}

	final long oldOwnerNum = 1234L;
	final long newOwnerNum = 1235L;
	final long treasuryNum = 1236L;
	final long tokenNum = 1237L;
	final long serialNum1 = 1L;
	final long serialNum2 = 2L;
	final long serialNum3 = 3L;
	final EntityNum oldOwner = EntityNum.fromLong(oldOwnerNum);
	final EntityNum newOwner = EntityNum.fromLong(newOwnerNum);
	final EntityNum treasury = EntityNum.fromLong(treasuryNum);
	final EntityNumPair nftKey1 = EntityNumPair.fromLongs(tokenNum, serialNum1);
	final EntityNumPair nftKey2 = EntityNumPair.fromLongs(tokenNum, serialNum2);
	final EntityNumPair nftKey3 = EntityNumPair.fromLongs(tokenNum, serialNum3);
	final NftNumPair nftNumPair1 = nftKey1.asNftNumPair();
	final NftNumPair nftNumPair2 = nftKey2.asNftNumPair();
	final NftNumPair nftNumPair3 = nftKey3.asNftNumPair();
	private MerkleAccount oldOwnerAccount = new MerkleAccount();
	private MerkleAccount newOwnerAccount = new MerkleAccount();
	private MerkleUniqueToken nft1 = new MerkleUniqueToken();
	private MerkleUniqueToken nft2 = new MerkleUniqueToken();
	private MerkleUniqueToken nft3 = new MerkleUniqueToken();
}
