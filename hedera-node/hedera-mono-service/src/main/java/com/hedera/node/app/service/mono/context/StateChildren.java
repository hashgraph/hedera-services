/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.node.app.service.mono.context;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.merkle.MerkleSpecialFiles;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.RecordsStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.common.system.address.AddressBook;
import java.time.Instant;
import java.util.Map;

public interface StateChildren {
    Instant signedAt();

    AccountStorageAdapter accounts();

    MerkleMapLike<EntityNum, MerkleTopic> topics();

    MerkleMapLike<EntityNum, MerkleToken> tokens();

    MerkleScheduledTransactions schedules();

    VirtualMapLike<VirtualBlobKey, VirtualBlobValue> storage();

    VirtualMapLike<ContractKey, IterableContractValue> contractStorage();

    TokenRelStorageAdapter tokenAssociations();

    MerkleNetworkContext networkCtx();

    AddressBook addressBook();

    MerkleSpecialFiles specialFiles();

    UniqueTokenMapAdapter uniqueTokens();

    RecordsStorageAdapter payerRecords();

    MerkleMapLike<EntityNum, MerkleStakingInfo> stakingInfo();

    RecordsRunningHashLeaf runningHashLeaf();

    Map<ByteString, EntityNum> aliases();
}
