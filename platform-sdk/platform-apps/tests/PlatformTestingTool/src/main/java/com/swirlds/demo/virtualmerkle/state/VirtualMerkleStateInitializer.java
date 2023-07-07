/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.virtualmerkle.state;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.system.Platform;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.demo.platform.PlatformTestingToolState;
import com.swirlds.demo.platform.UnsafeMutablePTTStateAccessor;
import com.swirlds.demo.virtualmerkle.config.VirtualMerkleConfig;
import com.swirlds.demo.virtualmerkle.map.account.AccountVirtualMapKey;
import com.swirlds.demo.virtualmerkle.map.account.AccountVirtualMapKeyBuilder;
import com.swirlds.demo.virtualmerkle.map.account.AccountVirtualMapKeySerializer;
import com.swirlds.demo.virtualmerkle.map.account.AccountVirtualMapKeySerializerMerkleDb;
import com.swirlds.demo.virtualmerkle.map.account.AccountVirtualMapValue;
import com.swirlds.demo.virtualmerkle.map.account.AccountVirtualMapValueBuilder;
import com.swirlds.demo.virtualmerkle.map.account.AccountVirtualMapValueSerializerMerkleDb;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapKey;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapKeyBuilder;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapKeySerializer;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapKeySerializerMerkleDb;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapValue;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapValueBuilder;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapValueSerializerMerkleDb;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.data.SmartContractMapKey;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.data.SmartContractMapKeyBuilder;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.data.SmartContractMapKeySerializer;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.data.SmartContractMapKeySerializerMerkleDb;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.data.SmartContractMapValue;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.data.SmartContractMapValueBuilder;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.data.SmartContractMapValueSerializerMerkleDb;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.VirtualHashRecordSerializer;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.logging.LogMarker;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

/**
 * This is a helper class to initialize the part of the state that corresponds to virtual map tests.
 */
public final class VirtualMerkleStateInitializer {

    private static final Logger logger = LogManager.getLogger(VirtualMerkleStateInitializer.class);
    private static final Marker LOGM_DEMO_INFO = LogMarker.DEMO_INFO.getMarker();

    private VirtualMerkleStateInitializer() {}

    /**
     * This method initialize all the data structures needed during the virtual merkle tests.
     *
     * @param platform
     * 		The platform where this method is being called.
     * @param nodeId
     * 		The id of the current node.
     * @param virtualMerkleConfig
     */
    public static void initStateChildren(
            final Platform platform,
            final long nodeId,
            final VirtualMerkleConfig virtualMerkleConfig,
            final boolean useMerkleDb) {

        try (final AutoCloseableWrapper<PlatformTestingToolState> wrapper =
                UnsafeMutablePTTStateAccessor.getInstance().getUnsafeMutableState(platform.getSelfId())) {

            final Path storageDir = Path.of(virtualMerkleConfig.getJasperDBStoragePath(), Long.toString(nodeId));
            final PlatformTestingToolState state = wrapper.get();
            logger.info(LOGM_DEMO_INFO, "State = {}, useMerkleDb = {}", state, useMerkleDb);

            final long totalAccounts = virtualMerkleConfig.getTotalAccountCreations();
            logger.info(LOGM_DEMO_INFO, "total accounts = {}", totalAccounts);
            if (state.getVirtualMap() == null && totalAccounts > 0) {
                logger.info(LOGM_DEMO_INFO, "Creating virtualmap for {} accounts.", totalAccounts);
                final VirtualMap<AccountVirtualMapKey, AccountVirtualMapValue> virtualMap =
                        createAccountsVM(useMerkleDb, storageDir, totalAccounts);
                logger.info(LOGM_DEMO_INFO, "accounts VM = {}, DS = {}", virtualMap, virtualMap.getDataSource());
                virtualMap.registerMetrics(platform.getContext().getMetrics());
                state.setVirtualMap(virtualMap);
            }

            final long maximumNumberOfKeyValuePairs = virtualMerkleConfig.getMaximumNumberOfKeyValuePairsCreation();
            logger.info(LOGM_DEMO_INFO, "max KV pairs = {}", maximumNumberOfKeyValuePairs);
            if (state.getVirtualMapForSmartContracts() == null && maximumNumberOfKeyValuePairs > 0) {
                logger.info(
                        LOGM_DEMO_INFO,
                        "Creating virtualmap for max {} key value pairs.",
                        maximumNumberOfKeyValuePairs);
                final VirtualMap<SmartContractMapKey, SmartContractMapValue> virtualMap =
                        createSmartContractsVM(useMerkleDb, storageDir, maximumNumberOfKeyValuePairs);
                logger.info(LOGM_DEMO_INFO, "SC VM = {}, DS = {}", virtualMap, virtualMap.getDataSource());
                virtualMap.registerMetrics(platform.getContext().getMetrics());
                state.setVirtualMapForSmartContracts(virtualMap);
            }

            final long totalSmartContracts = virtualMerkleConfig.getTotalSmartContractCreations();
            logger.info(LOGM_DEMO_INFO, "total SC = {}", totalSmartContracts);
            if (state.getVirtualMapForSmartContractsByteCode() == null && totalSmartContracts > 0) {
                logger.info(LOGM_DEMO_INFO, "Creating virtualmap for {} bytecodes.", totalSmartContracts);
                final VirtualMap<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> virtualMap =
                        createSmartContractByteCodeVM(useMerkleDb, storageDir, totalSmartContracts);
                logger.info(LOGM_DEMO_INFO, "SCBC VM = {}, DS = {}", virtualMap, virtualMap.getDataSource());
                virtualMap.registerMetrics(platform.getContext().getMetrics());
                state.setVirtualMapForSmartContractsByteCode(virtualMap);
            }
        }
    }

    private static VirtualMap<AccountVirtualMapKey, AccountVirtualMapValue> createAccountsVM(
            final boolean merkleDb, final Path storageDir, final long numOfKeys) {
        final VirtualDataSourceBuilder<AccountVirtualMapKey, AccountVirtualMapValue> dsBuilder;
        if (merkleDb) {
            final MerkleDbTableConfig<AccountVirtualMapKey, AccountVirtualMapValue> tableConfig =
                    new MerkleDbTableConfig<>(
                            (short) 1,
                            DigestType.SHA_384,
                            (short) 1,
                            new AccountVirtualMapKeySerializerMerkleDb(),
                            (short) 1,
                            new AccountVirtualMapValueSerializerMerkleDb());
            // Use null as storageDir, let MerkleDb manage it internally
            dsBuilder = new MerkleDbDataSourceBuilder<>(null, tableConfig);
        } else {
            final AccountVirtualMapKeySerializer keySerializer = new AccountVirtualMapKeySerializer();
            final VirtualLeafRecordSerializer<AccountVirtualMapKey, AccountVirtualMapValue> leafRecordSerializer =
                    new VirtualLeafRecordSerializer<>(
                            (short) 1,
                            keySerializer.getSerializedSize(),
                            new AccountVirtualMapKeyBuilder(),
                            (short) 1,
                            AccountVirtualMapValue.getSizeInBytes(),
                            new AccountVirtualMapValueBuilder(),
                            false);
            dsBuilder = new JasperDbBuilder<AccountVirtualMapKey, AccountVirtualMapValue>()
                    .virtualLeafRecordSerializer(leafRecordSerializer)
                    .virtualInternalRecordSerializer(new VirtualHashRecordSerializer())
                    .keySerializer(keySerializer)
                    .storageDir(storageDir)
                    .maxNumOfKeys(numOfKeys)
                    .hashesRamToDiskThreshold(0)
                    .preferDiskBasedIndexes(false);
        }
        return new VirtualMap<>("accounts", dsBuilder);
    }

    private static VirtualMap<SmartContractMapKey, SmartContractMapValue> createSmartContractsVM(
            final boolean useMerkleDb, final Path storageDir, final long numOfKeys) {
        final VirtualDataSourceBuilder<SmartContractMapKey, SmartContractMapValue> dsBuilder;
        if (useMerkleDb) {
            final MerkleDbTableConfig<SmartContractMapKey, SmartContractMapValue> tableConfig =
                    new MerkleDbTableConfig<>(
                            (short) 1,
                            DigestType.SHA_384,
                            (short) 1,
                            new SmartContractMapKeySerializerMerkleDb(),
                            (short) 1,
                            new SmartContractMapValueSerializerMerkleDb());
            // Use null as storageDir, let MerkleDb manage it internally
            dsBuilder = new MerkleDbDataSourceBuilder<>(null, tableConfig);
        } else {
            final SmartContractMapKeySerializer keySerializer = new SmartContractMapKeySerializer();
            final VirtualLeafRecordSerializer<SmartContractMapKey, SmartContractMapValue> leafRecordSerializer =
                    new VirtualLeafRecordSerializer<>(
                            (short) 1,
                            keySerializer.getSerializedSize(),
                            new SmartContractMapKeyBuilder(),
                            (short) 1,
                            SmartContractMapValue.getSizeInBytes(),
                            new SmartContractMapValueBuilder(),
                            false);
            dsBuilder = new JasperDbBuilder<SmartContractMapKey, SmartContractMapValue>()
                    .virtualLeafRecordSerializer(leafRecordSerializer)
                    .virtualInternalRecordSerializer(new VirtualHashRecordSerializer())
                    .keySerializer(keySerializer)
                    .storageDir(storageDir)
                    .maxNumOfKeys(numOfKeys)
                    .hashesRamToDiskThreshold(0)
                    .preferDiskBasedIndexes(false);
        }
        return new VirtualMap<>("smartContracts", dsBuilder);
    }

    private static VirtualMap<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> createSmartContractByteCodeVM(
            final boolean useMerkleDb, final Path storageDir, final long numOfKeys) {
        final VirtualDataSourceBuilder<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> dsBuilder;
        if (useMerkleDb) {
            final MerkleDbTableConfig<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> tableConfig =
                    new MerkleDbTableConfig<>(
                            (short) 1,
                            DigestType.SHA_384,
                            (short) 1,
                            new SmartContractByteCodeMapKeySerializerMerkleDb(),
                            (short) 1,
                            new SmartContractByteCodeMapValueSerializerMerkleDb());
            // Use null as storageDir, let MerkleDb manage it internally
            dsBuilder = new MerkleDbDataSourceBuilder<>(null, tableConfig);
        } else {
            final SmartContractByteCodeMapKeySerializer keySerializer = new SmartContractByteCodeMapKeySerializer();
            final VirtualLeafRecordSerializer<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue>
                    leafRecordSerializer = new VirtualLeafRecordSerializer<>(
                            (short) 1,
                            keySerializer.getSerializedSize(),
                            new SmartContractByteCodeMapKeyBuilder(),
                            (short) 1,
                            DataFileCommon.VARIABLE_DATA_SIZE,
                            new SmartContractByteCodeMapValueBuilder(),
                            false);
            dsBuilder = new JasperDbBuilder<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue>()
                    .virtualLeafRecordSerializer(leafRecordSerializer)
                    .virtualInternalRecordSerializer(new VirtualHashRecordSerializer())
                    .keySerializer(keySerializer)
                    .storageDir(storageDir)
                    // since each smart contract has one bytecode, we can use the number of
                    // smart contracts to decide the max num of keys for the bytecode map.
                    .maxNumOfKeys(numOfKeys)
                    .hashesRamToDiskThreshold(0)
                    .preferDiskBasedIndexes(false);
        }
        return new VirtualMap<>("smartContractByteCode", dsBuilder);
    }
}
