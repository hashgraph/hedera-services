package com.hedera.test.forensics;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.SolidityFnResult;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.merkle.io.MerkleDataInputStream;
import com.swirlds.common.merkle.utility.MerkleLong;
import com.swirlds.fcmap.FCMap;
import com.swirlds.fcmap.internal.FCMInternalNode;
import com.swirlds.fcmap.internal.FCMLeaf;
import com.swirlds.fcmap.internal.FCMTree;
import com.swirlds.fcqueue.FCQueue;

import java.nio.file.Files;
import java.nio.file.Path;

public class AccountsReader {
	public static FCMap<MerkleEntityId, MerkleAccount> from(String loc) throws Exception {
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

		try (MerkleDataInputStream in = new MerkleDataInputStream(Files.newInputStream(Path.of(loc)), false)) {
			FCMap<MerkleEntityId, MerkleAccount> fcm = in.readMerkleTree(Integer.MAX_VALUE);
			return fcm;
		}
	}
}
