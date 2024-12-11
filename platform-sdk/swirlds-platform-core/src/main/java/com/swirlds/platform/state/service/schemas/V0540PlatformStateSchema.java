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

package com.swirlds.platform.state.service.schemas;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.platform.state.ConsensusSnapshot;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.utility.Pair;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.config.BasicConfig;
import com.swirlds.platform.crypto.SerializableX509Certificate;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.state.service.WritablePlatformStateStore;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Defines the {@link PlatformState} singleton and initializes it at genesis.
 */
public class V0540PlatformStateSchema extends Schema {
    private static final Function<Configuration, SoftwareVersion> UNAVAILABLE_VERSION_FN = config -> {
        throw new IllegalStateException("No version information available");
    };

    public static final String PLATFORM_STATE_KEY = "PLATFORM_STATE";
    /**
     * A platform state to be used as the non-null platform state under any circumstance a genesis state
     * is encountered before initializing the States API.
     */
    public static final PlatformState UNINITIALIZED_PLATFORM_STATE = new PlatformState(
            SemanticVersion.DEFAULT, 0, ConsensusSnapshot.DEFAULT, null, null, Bytes.EMPTY, 0L, 0L, null, null, null);

    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(54).patch(0).build();

    private final Function<Configuration, SoftwareVersion> versionFn;

    public V0540PlatformStateSchema() {
        this(UNAVAILABLE_VERSION_FN);
    }

    public V0540PlatformStateSchema(@NonNull final Function<Configuration, SoftwareVersion> versionFn) {
        super(VERSION);
        this.versionFn = requireNonNull(versionFn);
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.singleton(PLATFORM_STATE_KEY, PlatformState.PROTOBUF));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        if (ctx.isGenesis()) {
            // The WritablePlatformStateStore constructor needs a non-null singleton state here
            ctx.newStates().getSingleton(PLATFORM_STATE_KEY).put(UNINITIALIZED_PLATFORM_STATE);
            final var genesisStateSpec = genesisStateSpec(ctx);
            final var platformStateStore = new WritablePlatformStateStore(ctx.newStates());
            if (ctx.appConfig().getConfigData(AddressBookConfig.class).useRosterLifecycle()) {
                // When using the roster lifecycle at genesis, platform code will never
                // use the legacy previous/current AddressBook fields, so omit them
                platformStateStore.bulkUpdate(genesisStateSpec);
            } else {
                // Before roster lifecycle is enabled, we must populate the legacy
                // AddressBook fields from the genesis network info
                platformStateStore.bulkUpdate(genesisStateSpec.andThen(v -> {
                    v.setPreviousAddressBook(null);
                    final var networkInfo = requireNonNull(ctx.genesisNetworkInfo());
                    v.setAddressBook(new AddressBook(networkInfo.addressBook().stream()
                            .map(info -> {
                                final var intPair =
                                        asPair(info.gossipEndpoints().getLast());
                                final var extPair =
                                        asPair(info.gossipEndpoints().getFirst());
                                return new Address(
                                        NodeId.of(info.nodeId()),
                                        "" + info.nodeId(),
                                        "node" + (info.nodeId() + 1),
                                        info.weight(),
                                        intPair.left(),
                                        intPair.right(),
                                        extPair.left(),
                                        extPair.right(),
                                        new SerializableX509Certificate(info.sigCert()),
                                        null,
                                        "0.0." + info.accountId().accountNumOrThrow());
                            })
                            .toList()));
                }));
            }
        }
    }

    private Pair<String, Integer> asPair(@NonNull final ServiceEndpoint endpoint) {
        if (endpoint.ipAddressV4().length() == 4) {
            return Pair.of(dotDelimitedOctets(endpoint.ipAddressV4().toByteArray()), endpoint.port());
        } else {
            return Pair.of(endpoint.domainName(), endpoint.port());
        }
    }

    private String dotDelimitedOctets(@NonNull final byte[] bytes) {
        return (0xff & bytes[0]) + "." + (0xff & bytes[1]) + "." + (0xff & bytes[2]) + "." + (0xff & bytes[3]);
    }

    private Consumer<PlatformStateModifier> genesisStateSpec(@NonNull final MigrationContext ctx) {
        return v -> {
            v.setCreationSoftwareVersion(versionFn.apply(ctx.appConfig()));
            v.setRound(0);
            v.setLegacyRunningEventHash(null);
            v.setConsensusTimestamp(Instant.EPOCH);
            final var basicConfig = ctx.platformConfig().getConfigData(BasicConfig.class);
            final long genesisFreezeTime = basicConfig.genesisFreezeTime();
            if (genesisFreezeTime > 0) {
                v.setFreezeTime(Instant.ofEpochSecond(genesisFreezeTime));
            }
        };
    }
}
