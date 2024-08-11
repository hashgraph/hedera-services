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

package com.hedera.node.app.workflows.standalone.impl;

import static com.hedera.node.app.util.FileUtilities.createFileID;
import static com.hedera.node.app.util.FileUtilities.getFileContent;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NodeAddressBook;
import com.hedera.node.app.info.NodeInfoImpl;
import com.hedera.node.app.info.SelfNodeInfoImpl;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.State;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import com.swirlds.state.spi.info.SelfNodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link NetworkInfo} implementation that gets network information from the {@link FilesConfig#nodeDetails()}
 * file in a {@link State}. This lets a standalone executor handle transactions that make staking elections.
 * <p>
 * If the executor is to handle such transactions as if on a live network with this state, the node details file
 * must be present in the state and reflect the network's active address book.
 * <p>
 * The {@link NetworkInfo#selfNodeInfo()} implementation, however, returns a {@link SelfNodeInfo} that is a complete
 * mock other than the software version, since the self-identity of the node executing a transaction clearly cannot
 * change <i>how</i> the transaction is executed.
 */
@Singleton
public class StateNetworkInfo implements NetworkInfo {
    private static final Logger log = LogManager.getLogger(StateNetworkInfo.class);

    private final Bytes ledgerId;
    private final ConfigProvider configProvider;
    private final SelfNodeInfo selfNodeInfo;

    @Nullable
    private List<NodeInfo> nodeInfos;

    @Inject
    public StateNetworkInfo(@NonNull final ConfigProvider configProvider) {
        this.configProvider = requireNonNull(configProvider);
        final var config = configProvider.getConfiguration();
        this.ledgerId = config.getConfigData(LedgerConfig.class).id();
        this.selfNodeInfo = mockSelfNodeInfo(config);
    }

    /**
     * Initializes this instance from {@link FilesConfig#nodeDetails()} file in the given state.
     * <p>
     * If the file is missing or cannot be parsed, no staking election will be treated as valid,
     * and staking transactions will not be executed as on any live network (which necessarily
     * has at least some nodes).
     * @param state the state to use
     */
    public void initFrom(@NonNull final State state) {
        requireNonNull(state);
        final var config = configProvider.getConfiguration();
        final var filesConfig = config.getConfigData(FilesConfig.class);
        final var nodeDetails = getFileContent(state, createFileID(filesConfig.nodeDetails(), config));
        try {
            final var nodeAddressBook = NodeAddressBook.PROTOBUF.parse(nodeDetails);
            nodeInfos = nodeAddressBook.nodeAddress().stream()
                    .<NodeInfo>map(address -> new NodeInfoImpl(
                            address.nodeId(),
                            address.nodeAccountIdOrThrow(),
                            address.stake(),
                            "",
                            -1,
                            "",
                            -1,
                            address.rsaPubKey(),
                            address.description(),
                            Bytes.EMPTY,
                            ""))
                    .toList();
        } catch (ParseException e) {
            log.warn("Failed to parse node details", e);
            nodeInfos = emptyList();
        }
    }

    @NonNull
    @Override
    public Bytes ledgerId() {
        return ledgerId;
    }

    @NonNull
    @Override
    public SelfNodeInfo selfNodeInfo() {
        return selfNodeInfo;
    }

    @NonNull
    @Override
    public List<NodeInfo> addressBook() {
        return nodeInfosOrThrow();
    }

    @Nullable
    @Override
    public NodeInfo nodeInfo(final long nodeId) {
        return nodeInfosOrThrow().stream()
                .filter(node -> node.nodeId() == nodeId)
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean containsNode(final long nodeId) {
        return nodeInfo(nodeId) != null;
    }

    private @NonNull List<NodeInfo> nodeInfosOrThrow() {
        return requireNonNull(nodeInfos, "Not initialized");
    }

    /**
     * Returns a {@link SelfNodeInfo} that is a complete mock other than the software version present in the
     * given configuration.
     * @param config the configuration to use
     * @return a mock self node info
     */
    private static SelfNodeInfo mockSelfNodeInfo(@NonNull final Configuration config) {
        final var versionConfig = config.getConfigData(VersionConfig.class);
        final var version = new HederaSoftwareVersion(
                versionConfig.hapiVersion(),
                versionConfig.servicesVersion(),
                config.getConfigData(HederaConfig.class).configVersion());
        return new SelfNodeInfoImpl(0, AccountID.DEFAULT, 0, "", -1, "", -1, "", "", Bytes.EMPTY, version, "");
    }
}
