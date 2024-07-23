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

import static com.hedera.node.app.service.addressbook.AddressBookHelper.NODES_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.BootstrapConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * General schema for the addressbook service
 * {@code V052AddressBookSchema} is used for migrating the address book on Version 0.52.0
 */
public class V053AddressBookSchema extends Schema {
    private static final Logger log = LogManager.getLogger(V053AddressBookSchema.class);
    private static final Pattern IPV4_ADDRESS_PATTERN =
            Pattern.compile("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$");

    private static final long MAX_NODES = 100L;
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(53).patch(0).build();
    public static final String ACCOUNTS_KEY = "ACCOUNTS";

    public V053AddressBookSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.onDisk(NODES_KEY, EntityNumber.PROTOBUF, Node.PROTOBUF, MAX_NODES));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        final WritableKVState<EntityNumber, Node> writableNodes =
                ctx.newStates().get(NODES_KEY);
        ReadableKVState<AccountID, Account> readableAccounts = null;
        try {
            readableAccounts = ctx.newStates().get(ACCOUNTS_KEY);
        } catch (IllegalArgumentException e) {
            log.info("AccountStore is not found, can be ignored.");
        }
        final var networkInfo = ctx.networkInfo();
        final var addressBook = networkInfo.addressBook();
        final var bootstrapConfig = ctx.configuration().getConfigData(BootstrapConfig.class);
        final var accountConfig = ctx.configuration().getConfigData(AccountsConfig.class);
        var adminKey = Key.DEFAULT;
        if (readableAccounts != null) {
            final var adminAccount = readableAccounts.get(AccountID.newBuilder()
                    .accountNum(accountConfig.addressBookAdmin())
                    .build());
            if (adminAccount != null) {
                adminKey = adminAccount.keyOrElse(Key.DEFAULT);
            }
        }
        log.info("Started migrating nodes from address book");

        Key finalAdminKey = adminKey == null || adminKey.equals(Key.DEFAULT)
                ? Key.newBuilder().ed25519(bootstrapConfig.genesisPublicKey()).build()
                : adminKey;
        addressBook.forEach(nodeInfo -> {
            final var node = Node.newBuilder()
                    .nodeId(nodeInfo.nodeId())
                    .accountId(nodeInfo.accountId())
                    .description(nodeInfo.selfName())
                    .gossipEndpoint(List.of(
                            endpointFor(nodeInfo.internalHostName(), nodeInfo.internalPort()),
                            endpointFor(nodeInfo.externalHostName(), nodeInfo.externalPort())))
                    .gossipCaCertificate(nodeInfo.sigCertBytes())
                    .weight(nodeInfo.stake())
                    .adminKey(finalAdminKey)
                    .build();
            writableNodes.put(
                    EntityNumber.newBuilder().number(nodeInfo.nodeId()).build(), node);
        });

        log.info("Migrated {} nodes from address book", addressBook.size());
    }

    /**
     * Given a host and port, creates a {@link ServiceEndpoint} object with either an IP address or domain name
     * depending on the given host.
     *
     * @param host the host
     * @param port the port
     * @return the {@link ServiceEndpoint} object
     */
    public static ServiceEndpoint endpointFor(@NonNull final String host, final int port) {
        final var builder = ServiceEndpoint.newBuilder().port(port);
        if (IPV4_ADDRESS_PATTERN.matcher(host).matches()) {
            final var octets = host.split("[.]");
            builder.ipAddressV4(Bytes.wrap(new byte[] {
                (byte) Integer.parseInt(octets[0]),
                (byte) Integer.parseInt(octets[1]),
                (byte) Integer.parseInt(octets[2]),
                (byte) Integer.parseInt(octets[3])
            }));
        } else {
            builder.domainName(host);
        }
        return builder.build();
    }
}
