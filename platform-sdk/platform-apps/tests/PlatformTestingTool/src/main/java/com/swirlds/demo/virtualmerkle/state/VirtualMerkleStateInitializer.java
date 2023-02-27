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
import com.swirlds.common.system.PlatformWithDeprecatedMethods;
import com.swirlds.demo.platform.PlatformTestingToolState;
import com.swirlds.demo.virtualmerkle.config.VirtualMerkleConfig;
import com.swirlds.demo.virtualmerkle.map.account.AccountVirtualMapKey;
import com.swirlds.demo.virtualmerkle.map.account.AccountVirtualMapKeyBuilder;
import com.swirlds.demo.virtualmerkle.map.account.AccountVirtualMapKeySerializer;
import com.swirlds.demo.virtualmerkle.map.account.AccountVirtualMapValue;
import com.swirlds.demo.virtualmerkle.map.account.AccountVirtualMapValueBuilder;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapKey;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapKeyBuilder;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapKeySerializer;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapValue;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapValueBuilder;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.data.SmartContractMapKey;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.data.SmartContractMapKeyBuilder;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.data.SmartContractMapKeySerializer;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.data.SmartContractMapValue;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.data.SmartContractMapValueBuilder;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.VirtualInternalRecordSerializer;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.logging.LogMarker;
import com.swirlds.virtualmap.VirtualMap;
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
            Platform platform, final long nodeId, final VirtualMerkleConfig virtualMerkleConfig) {

        final Path pathToJasperDBStorageDir =
                Path.of(virtualMerkleConfig.getJasperDBStoragePath(), Long.toString(nodeId));
        final PlatformTestingToolState state = ((PlatformWithDeprecatedMethods) platform).getState();
        final long totalAccountCreations = virtualMerkleConfig.getTotalAccountCreations();
        if (state.getVirtualMap() == null && totalAccountCreations > 0) {
            logger.info(LOGM_DEMO_INFO, "Creating virtualmap for {} accounts.", totalAccountCreations);
            final AccountVirtualMapKeySerializer keySerializer = new AccountVirtualMapKeySerializer();
            final VirtualLeafRecordSerializer<AccountVirtualMapKey, AccountVirtualMapValue> leafRecordSerializer =
                    new VirtualLeafRecordSerializer<>(
                            (short) 1,
                            DigestType.SHA_384,
                            (short) 1,
                            keySerializer.getSerializedSize(),
                            new AccountVirtualMapKeyBuilder(),
                            (short) 1,
                            AccountVirtualMapValue.getSizeInBytes(),
                            new AccountVirtualMapValueBuilder(),
                            false);

            final JasperDbBuilder<AccountVirtualMapKey, AccountVirtualMapValue> jasperDbBuilder = new JasperDbBuilder<
                            AccountVirtualMapKey, AccountVirtualMapValue>()
                    .virtualLeafRecordSerializer(leafRecordSerializer)
                    .virtualInternalRecordSerializer(new VirtualInternalRecordSerializer())
                    .keySerializer(keySerializer)
                    .storageDir(pathToJasperDBStorageDir)
                    .maxNumOfKeys(totalAccountCreations)
                    .internalHashesRamToDiskThreshold(0)
                    .preferDiskBasedIndexes(false);

            final VirtualMap<AccountVirtualMapKey, AccountVirtualMapValue> virtualMap =
                    new VirtualMap<>("accounts", jasperDbBuilder);
            virtualMap.registerMetrics(platform.getContext().getMetrics());
            state.setVirtualMap(virtualMap);
        }

        final long maximumNumberOfKeyValuePairsCreation = virtualMerkleConfig.getMaximumNumberOfKeyValuePairsCreation();
        if (state.getVirtualMapForSmartContracts() == null && maximumNumberOfKeyValuePairsCreation > 0) {
            logger.info(
                    LOGM_DEMO_INFO,
                    "Creating virtualmap for max {} key value pairs.",
                    maximumNumberOfKeyValuePairsCreation);
            final SmartContractMapKeySerializer keySerializer = new SmartContractMapKeySerializer();
            final VirtualLeafRecordSerializer<SmartContractMapKey, SmartContractMapValue> leafRecordSerializer =
                    new VirtualLeafRecordSerializer<>(
                            (short) 1,
                            DigestType.SHA_384,
                            (short) 1,
                            keySerializer.getSerializedSize(),
                            new SmartContractMapKeyBuilder(),
                            (short) 1,
                            SmartContractMapValue.getSizeInBytes(),
                            new SmartContractMapValueBuilder(),
                            false);

            final JasperDbBuilder<SmartContractMapKey, SmartContractMapValue> jasperDbBuilder = new JasperDbBuilder<
                            SmartContractMapKey, SmartContractMapValue>()
                    .virtualLeafRecordSerializer(leafRecordSerializer)
                    .virtualInternalRecordSerializer(new VirtualInternalRecordSerializer())
                    .keySerializer(keySerializer)
                    .storageDir(pathToJasperDBStorageDir)
                    .maxNumOfKeys(maximumNumberOfKeyValuePairsCreation)
                    .internalHashesRamToDiskThreshold(0)
                    .preferDiskBasedIndexes(false);

            final VirtualMap<SmartContractMapKey, SmartContractMapValue> virtualMap =
                    new VirtualMap<>("smartContracts", jasperDbBuilder);
            virtualMap.registerMetrics(platform.getContext().getMetrics());
            state.setVirtualMapForSmartContracts(virtualMap);
        }

        final long totalSmartContractCreations = virtualMerkleConfig.getTotalSmartContractCreations();
        if (state.getVirtualMapForSmartContractsByteCode() == null && totalSmartContractCreations > 0) {
            logger.info(LOGM_DEMO_INFO, "Creating virtualmap for {} bytecodes.", totalSmartContractCreations);

            final SmartContractByteCodeMapKeySerializer keySerializer = new SmartContractByteCodeMapKeySerializer();
            final VirtualLeafRecordSerializer<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue>
                    leafRecordSerializer = new VirtualLeafRecordSerializer<>(
                            (short) 1,
                            DigestType.SHA_384,
                            (short) 1,
                            keySerializer.getSerializedSize(),
                            new SmartContractByteCodeMapKeyBuilder(),
                            (short) 1,
                            DataFileCommon.VARIABLE_DATA_SIZE,
                            new SmartContractByteCodeMapValueBuilder(),
                            false);

            final JasperDbBuilder<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> jasperDbBuilder =
                    new JasperDbBuilder<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue>()
                            .virtualLeafRecordSerializer(leafRecordSerializer)
                            .virtualInternalRecordSerializer(new VirtualInternalRecordSerializer())
                            .keySerializer(keySerializer)
                            .storageDir(pathToJasperDBStorageDir)
                            // since each smart contract has one bytecode, we can use the number of
                            // smart contracts to decide the max num of keys for the bytecode map.
                            .maxNumOfKeys(totalSmartContractCreations)
                            .internalHashesRamToDiskThreshold(0)
                            .preferDiskBasedIndexes(false);

            final VirtualMap<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> virtualMap =
                    new VirtualMap<>("smartContractByteCode", jasperDbBuilder);
            virtualMap.registerMetrics(platform.getContext().getMetrics());
            state.setVirtualMapForSmartContractsByteCode(virtualMap);
        }
    }
}
