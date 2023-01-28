package com.hedera.node.app.service.mono.context;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
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
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;

import java.time.Instant;
import java.util.Map;

public interface StateChildrenProvider {
    AccountStorageAdapter accounts();
    MerkleMapLike<EntityNum, MerkleTopic> topics();
    VirtualMap<VirtualBlobKey, VirtualBlobValue> storage();
    VirtualMap<ContractKey, IterableContractValue> contractStorage();
    MerkleMapLike<EntityNum, MerkleToken> tokens();
    TokenRelStorageAdapter tokenAssociations();
    MerkleScheduledTransactions scheduleTxs();
    MerkleNetworkContext networkCtx();
    AddressBook addressBook();
    MerkleSpecialFiles specialFiles();
    UniqueTokenMapAdapter uniqueTokens();
    RecordsStorageAdapter payerRecords();
    RecordsRunningHashLeaf runningHashLeaf();
    Map<ByteString, EntityNum> aliases();
    MerkleMapLike<EntityNum, MerkleStakingInfo> stakingInfo();

    int getStateVersion();
    boolean isInitialized();
    Instant getTimeOfLastHandledTxn();
}
