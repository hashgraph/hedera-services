package com.hedera.test.forensics;

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

import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.SolidityFnResult;
import com.hedera.test.forensics.domain.PojoFs;
import com.hedera.test.forensics.domain.PojoLedger;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.merkle.utility.MerkleLong;
import com.swirlds.fcmap.FCMap;
import com.swirlds.fcmap.internal.FCMInternalNode;
import com.swirlds.fcmap.internal.FCMLeaf;
import com.swirlds.fcmap.internal.FCMTree;
import com.swirlds.fcqueue.FCQueue;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

@Disabled
public class FcmToJsonUtil {
	final List<String> accountsLocs = List.of(new String[] {
			"/Users/tinkerm/Dev/iss/stable/node00-logs/data/saved/com.hedera.services" + ".ServicesMain/0/accounts-round38056100.fcm",
//			"/Users/tinkerm/Dev/iss/stable/node00-logs/data/saved/com.hedera.services" +
//					".ServicesMain/0/accounts-round38056101.fcm"
	});
	final List<String> storageLocs = List.of(
			"/Users/tinkerm/Dev/hgn2/services-hedera/hedera-node/forensics/iss-demo/0/storage-round12.fcm",
			"/Users/tinkerm/Dev/hgn2/services-hedera/hedera-node/forensics/iss-demo/1/storage-round12.fcm",
			"/Users/tinkerm/Dev/hgn2/services-hedera/hedera-node/forensics/iss-demo/2/storage-round12.fcm"
	);
	final List<String> topicsLocs = List.of(
			"/Users/tinkerm/Dev/hgn2/services-hedera/hedera-node/forensics/iss-demo/0/topics-round12.fcm",
			"/Users/tinkerm/Dev/hgn2/services-hedera/hedera-node/forensics/iss-demo/1/topics-round12.fcm",
			"/Users/tinkerm/Dev/hgn2/services-hedera/hedera-node/forensics/iss-demo/2/topics-round12.fcm"
	);

	@Test
	void convertStorageToJson() throws Exception {
		String[] round60Locs = {
//				"/Users/tinkerm/Dev/hgn3/hedera-services/hedera-node/n0-storage-round60.fcm",
				"/Users/tinkerm/Dev/hgn3/hedera-services/hedera-node/n1-storage-round60.fcm",
//				"/Users/tinkerm/Dev/hgn3/hedera-services/hedera-node/n2-storage-round60.fcm",
//				"/Users/tinkerm/Dev/hgn3/hedera-services/hedera-node/n3-storage-round60.fcm",
		};

		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCMInternalNode.class, FCMInternalNode::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleLong.class, MerkleLong::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCMap.class, FCMap::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCMTree.class, FCMTree::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCMLeaf.class, FCMLeaf::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleBlobMeta.class, MerkleBlobMeta::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleOptionalBlob.class, MerkleOptionalBlob::new));

		for (String dumpLoc : round60Locs) {
			System.out.println("Reading " + dumpLoc);
			PojoFs.fromDisk(dumpLoc).asJsonTo(jsonSuffixed(dumpLoc));
		}
	}

	@Test
	public void convertAccountsToJson() throws Exception {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCMInternalNode.class, FCMInternalNode::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleLong.class, MerkleLong::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCQueue.class, FCQueue::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCMap.class, FCMap::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCMTree.class, FCMTree::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCMLeaf.class, FCMLeaf::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleEntityId.class, MerkleEntityId::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(EntityId.class, EntityId::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleAccountState.class, MerkleAccountState::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(ExpirableTxnRecord.class, ExpirableTxnRecord::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(TxnReceipt.class, TxnReceipt::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(TxnId.class, TxnId::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(CurrencyAdjustments.class, CurrencyAdjustments::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(SolidityFnResult.class, SolidityFnResult::new));

		for (String dumpLoc : accountsLocs) {
			PojoLedger.fromDisk(dumpLoc).asJsonTo(jsonSuffixed(dumpLoc));
		}
	}

	public static String jsonSuffixed(String path) {
		int n = path.length();
		return path.substring(0, n - 4) + ".json";
	}
}
