/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context;

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleScheduledTransactions;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.migration.UniqueTokenMapAdapter;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.time.Instant;
import java.util.Map;

public interface StateChildren {
    Instant signedAt();

    MerkleMap<EntityNum, MerkleAccount> accounts();

    MerkleMap<EntityNum, MerkleTopic> topics();

    MerkleMap<EntityNum, MerkleToken> tokens();

    MerkleScheduledTransactions schedules();

    VirtualMap<VirtualBlobKey, VirtualBlobValue> storage();

    VirtualMap<ContractKey, IterableContractValue> contractStorage();

    MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations();

    MerkleNetworkContext networkCtx();

    AddressBook addressBook();

    MerkleSpecialFiles specialFiles();

    UniqueTokenMapAdapter uniqueTokens();

    MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo();

    RecordsRunningHashLeaf runningHashLeaf();

    Map<ByteString, EntityNum> aliases();
}
