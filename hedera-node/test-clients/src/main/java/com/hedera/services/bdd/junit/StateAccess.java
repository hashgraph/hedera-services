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

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ACCOUNTS_KEY;
import static com.hedera.node.app.statedumpers.accounts.AccountDumpUtils.gatherAccounts;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.statedumpers.accounts.BBMHederaAccount;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.swirlds.base.time.Time;
import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyFactory;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The StateAccess class provides utility methods for accessing and processing the state of a Hedera network. It
 * includes methods for reading account information from signed state files, finding Swirlds directories, reading
 * account states from multiple nodes
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Read accounts from the state of a single node
 * List<BBMHederaAccount> accounts = StateAccess.readSignedStateAccountsFromNode(nodeId);
 *
 * // Read accounts from the state of multiple nodes
 * Set<BBMHederaAccount> allAccounts = StateAccess.readAccountsFromNodesState(nodeCount);
 * }</pre>
 *
 * <p>Note: This class assumes a specific directory structure and file naming convention for the signed state
 * files.</p>
 */
public class StateAccess {
    static final Logger log = LogManager.getLogger(StateAccess.class);

    private static final String MAIN_DIRECTORY = "com.hedera.services.ServicesMain";
    private static final String SIGNED_STATE_FILE_PATH = "build/hapi-test/node%d/data/saved";

    /**
     * Reads BBMHederaAccount objects from the state of a single node.
     *
     * @param nodeId The ID of the node from which to read the state.
     * @return A list of BBMHederaAccount objects read from the state of the specified node.
     */
    public static List<BBMHederaAccount> readSignedStateAccountsFromNode(int nodeId) {
        SignedStateFilePath state = new SignedStateFilePath(
                new StateCommonConfig(Paths.get(String.format(SIGNED_STATE_FILE_PATH, nodeId))));

        String swirldsName = findSwirldsDirectory(nodeId);

        final List<SavedStateInfo> savedStates =
                state.getSavedStateFiles(MAIN_DIRECTORY, new NodeId(nodeId), swirldsName);

        PlatformContext platformContext;
        try {
            platformContext = buildPlatformContext();
        } catch (IOException e) {
            log.info("Error building platform context when reading state");
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
                log.info("Error reading state file");
            }
        }

        return dumpableAccounts;
    }

    /**
     * Reads BBMHederaAccount objects from the state of multiple nodes.
     *
     * @param nodeCount The number of nodes whose state needs to be read.
     * @return A set of BBMHederaAccount objects collected from the state of all nodes.
     */
    public static Set<BBMHederaAccount> readAccountsFromNodesState(int nodeCount) {
        Set<BBMHederaAccount> accounts = new HashSet<>();
        for (int i = 0; i < nodeCount; i++) {
            accounts.addAll(readSignedStateAccountsFromNode(i));
        }
        return accounts;
    }

    /**
     * Finds the swirldsDir directory for the specified node ID within the signed state file path. This method
     * constructs the path to the swirlds directory using the provided node ID and the predefined signed state file
     * path. It then searches for the swirlds directory within the constructed path and returns its name if found. Check
     * also SignedStateFilePath.java class.
     *
     * <p>For example:
     * <pre>
     *     e.g. data/saved/com.swirlds.foobar/1/swirldsDir
     *          |--------| |----------------||--| |------| |--|
     *              |             |           |      |      |
     *              |         mainClassName   |      |    round
     *              |                         |  swirldName
     *           location where             selfId
     *           states are saved
     * </pre>
     * </p>
     *
     * @param nodeId The ID of the node for which to find the swirlds directory.
     * @return The name of the swirlds directory if found, or null if not found.
     */
    private static String findSwirldsDirectory(int nodeId) {
        Path directory = FileUtils.getAbsolutePath(
                Paths.get(String.format(SIGNED_STATE_FILE_PATH, nodeId) + "/" + MAIN_DIRECTORY + "/" + nodeId));

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    return path.getFileName().toString();
                }
            }
        } catch (IOException e) {
            log.error("Error finding Swirlds directory: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Builds the platform context configuration required for reading the state file. This method creates a
     * PlatformContext instance
     *
     * @return The PlatformContext instance
     * @throws IOException If an I/O error occurs during the configuration setup.
     */
    private static PlatformContext buildPlatformContext() throws IOException {
        final Configuration configuration = DefaultConfiguration.buildBasicConfiguration(ConfigurationBuilder.create());
        BootstrapUtils.setupConstructableRegistry();

        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        DefaultMetrics defaultMetrics = new DefaultMetrics(
                null,
                new MetricKeyRegistry(),
                Executors.newSingleThreadScheduledExecutor(),
                new DefaultMetricsFactory(metricsConfig),
                metricsConfig);

        // maybe change this since it uses deprecated class
        final Cryptography cryptography = CryptographyFactory.create(configuration);

        return new DefaultPlatformContext(configuration, defaultMetrics, cryptography, Time.getCurrent());
    }
}
