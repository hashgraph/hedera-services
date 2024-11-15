/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.addressbook.impl.schemas;

import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.NODES_KEY;
import static com.swirlds.platform.roster.RosterUtils.formatNodeName;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NodeAddress;
import com.hedera.hapi.node.base.NodeAddressBook;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * General schema for the addressbook service.
 * {@code V052AddressBookSchema} is used for migrating the address book on Version 0.52.0
 */
public class V056AddressBookSchema extends Schema {
    private static final Logger log = LogManager.getLogger(V056AddressBookSchema.class);

    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(56).patch(0).build();
    public static final String ACCOUNTS_KEY = "ACCOUNTS";
    public static final String FILES_KEY = "FILES";

    public V056AddressBookSchema() {
        super(VERSION);
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        final var isGenesis = ctx.previousVersion() == null;
        // Genesis use V053AddressBookSchema
        if (isGenesis) {
            return;
        }

        final var networkInfo = ctx.genesisNetworkInfo();
        if (networkInfo == null) {
            throw new IllegalStateException("Genesis network info is not found");
        }
        final WritableKVState<EntityNumber, Node> writableNodes =
                ctx.newStates().get(NODES_KEY);

        log.info("Started migrating nodes from address book");
        final var adminKey = getAccountAdminKey(ctx);
        final var nodeDetailMap = getNodeAddressMap(ctx);

        if (adminKey == null) {
            log.error("Failed to migrate nodes from address book because of {}", adminKey);
        } else {
            migratedKVState(nodeDetailMap, networkInfo, adminKey, writableNodes);
        }
    }

    @Nullable
    private Key getAccountAdminKey(@NonNull final MigrationContext ctx) {
        final var accountConfig = ctx.configuration().getConfigData(AccountsConfig.class);
        final ReadableKVState<AccountID, Account> readableAccounts =
                ctx.previousStates().get(ACCOUNTS_KEY);
        final var addressBookAdminAccount = readableAccounts.get(AccountID.newBuilder()
                .accountNum(accountConfig.addressBookAdmin())
                .build());
        if (addressBookAdminAccount == null) {
            log.error("addressBookAdminAccount not found in the previous state");
            return null;
        } else {
            return addressBookAdminAccount.keyOrThrow();
        }
    }

    @Nullable
    private Map<Long, NodeAddress> getNodeAddressMap(@NonNull final MigrationContext ctx) {
        Map<Long, NodeAddress> nodeDetailMap = null;

        final var fileConfig = ctx.configuration().getConfigData(FilesConfig.class);
        ReadableKVState<FileID, File> readableFiles = ctx.previousStates().get(FILES_KEY);

        final var nodeDetailFile = readableFiles.get(
                FileID.newBuilder().fileNum(fileConfig.nodeDetails()).build());
        if (nodeDetailFile == null) {
            log.warn("102 file not found in the previous state");
        } else {
            try {
                final var nodeDetails = NodeAddressBook.PROTOBUF
                        .parse(nodeDetailFile.contents())
                        .nodeAddress();
                nodeDetailMap = nodeDetails.stream().collect(toMap(NodeAddress::nodeId, Function.identity()));
            } catch (ParseException e) {
                log.error("Can not parse file 102 ", e);
            }
        }
        return nodeDetailMap;
    }

    private void migratedKVState(
            @Nullable Map<Long, NodeAddress> nodeDetailMap,
            @NonNull NetworkInfo networkInfo,
            @NonNull Key adminKey,
            @NonNull WritableKVState<EntityNumber, Node> writableNodes) {
        NodeAddress nodeDetail;
        final var addressBook = networkInfo.addressBook();
        for (final var nodeInfo : addressBook) {
            nodeDetail = nodeDetailMap == null ? null : nodeDetailMap.get(nodeInfo.nodeId());
            final var nodeBuilder = Node.newBuilder()
                    .nodeId(nodeInfo.nodeId())
                    .accountId(nodeInfo.accountId())
                    .description(formatNodeName(nodeInfo.nodeId()))
                    .gossipEndpoint(nodeInfo.gossipEndpoints())
                    .gossipCaCertificate(nodeInfo.sigCertBytes())
                    .weight(nodeInfo.stake())
                    .adminKey(adminKey)
                    .serviceEndpoint(nodeDetail != null ? nodeDetail.serviceEndpoint() : emptyList())
                    .grpcCertificateHash(nodeDetail != null ? nodeDetail.nodeCertHash() : Bytes.EMPTY);

            writableNodes.put(
                    EntityNumber.newBuilder().number(nodeInfo.nodeId()).build(), nodeBuilder.build());
        }
        log.info("Migrated {} nodes from address book", addressBook.size());
    }
}
