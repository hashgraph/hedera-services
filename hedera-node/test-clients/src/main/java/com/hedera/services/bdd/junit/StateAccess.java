/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit;

// import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
// import static com.swirlds.platform.builder.PlatformBuildConstants.DEFAULT_SETTINGS_FILE_NAME;
// import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.getMetricsProvider;
// import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.setupGlobalMetrics;
// import static com.swirlds.platform.state.signed.SignedStateFileReader.readStateFile;

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ACCOUNTS_KEY;
import static com.hedera.node.app.statedumpers.accounts.AccountDumpUtils.gatherAccounts;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
// import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.service.mono.statedumpers.accounts.BBMHederaAccount;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.swirlds.base.time.Time;
import com.swirlds.common.config.StateCommonConfig;
// import com.swirlds.common.context.DefaultPlatformContext;
// import com.swirlds.common.context.PlatformContext;
// import com.swirlds.common.crypto.Cryptography;
// import com.swirlds.common.crypto.CryptographyFactory;
// import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyFactory;
// import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
// import com.swirlds.common.merkle.crypto.MerkleCryptography;
// import com.swirlds.common.merkle.crypto.MerkleCryptographyFactory;
// import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.config.DefaultConfiguration;
import com.swirlds.platform.state.signed.DeserializedSignedState;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SavedStateInfo;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFilePath;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import com.swirlds.platform.util.BootstrapUtils;
import com.swirlds.virtualmap.VirtualMap;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

public class StateAccess {

    private static final String mainDirectory = "com.hedera.services.ServicesMain";

    private static final String SIGNED_STATE_FILE_PATH = "build/hapi-test/node%d/data/saved";

    public static List<BBMHederaAccount> readSignedStateAccountsFromNode(int nodeId) throws IOException {
        NodeId nodeId1 = new NodeId(nodeId);

        SignedStateFilePath state = new SignedStateFilePath(
                new StateCommonConfig(Paths.get(String.format(SIGNED_STATE_FILE_PATH, nodeId))));

        String swirldsName = findSwirldsDirectory(FileUtils.getAbsolutePath(
                Paths.get(String.format(SIGNED_STATE_FILE_PATH, nodeId) + "/" + mainDirectory + "/" + nodeId)));

        final List<SavedStateInfo> savedStates =
                state.getSavedStateFiles(mainDirectory, new NodeId(nodeId), swirldsName);

        PlatformContext platformContext;
        try {
            platformContext = buildPlatformContext(nodeId1);
        } catch (IOException e) {
            System.out.println("Error building platform context");
            return new ArrayList<>();
        }

        List<BBMHederaAccount> dumpableAccounts = new ArrayList<>();
        for (SavedStateInfo savedStateInfo : savedStates) {
            Path stateFilePath = savedStateInfo.stateFile();
            try {
                DeserializedSignedState deserializedSignedState =
                        SignedStateFileReader.readStateFile(platformContext, stateFilePath);
                ReservedSignedState reservedSignedState = deserializedSignedState.reservedSignedState();
                SignedState signedState = reservedSignedState.get();
                MerkleHederaState hederaState = (MerkleHederaState) signedState.getSwirldState();
                VirtualMap<OnDiskKey<AccountID>, OnDiskValue<Account>> accountsMap =
                        hederaState.getChild(hederaState.findNodeIndex(TokenService.NAME, ACCOUNTS_KEY));
                dumpableAccounts.addAll(Arrays.asList(gatherAccounts(accountsMap)));
            } catch (IOException e) {
                System.out.println("Error reading state file");
            }
        }

        return dumpableAccounts;
    }

    private static String findSwirldsDirectory(Path directory) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    return path.getFileName().toString();
                }
            }
        }
        return null;
    }

    public static Set<BBMHederaAccount> readAccountsFromNodesState(int nodeCount) throws IOException {
        Set<BBMHederaAccount> accounts = new HashSet<>();
        for (int i = 0; i < nodeCount; i++) {
            accounts.addAll(readSignedStateAccountsFromNode(i));
        }
        return accounts;
    }

    private static PlatformContext buildPlatformContext(NodeId nodeId) throws IOException {
        final Configuration configuration = DefaultConfiguration.buildBasicConfiguration(ConfigurationBuilder.create());
        BootstrapUtils.setupConstructableRegistry();

        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        DefaultMetrics defaultMetrics = new DefaultMetrics(
                nodeId,
                new MetricKeyRegistry(),
                Executors.newSingleThreadScheduledExecutor(),
                new DefaultMetricsFactory(metricsConfig),
                metricsConfig);

        final Cryptography cryptography = CryptographyFactory.create(configuration);

        return new DefaultPlatformContext(configuration, defaultMetrics, cryptography, Time.getCurrent());
    }
}
