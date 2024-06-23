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

package com.hedera.services.bdd.junit.hedera;

import static com.hedera.hapi.util.HapiUtils.parseAccount;
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.ADDRESS_BOOK;
import static com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader.loadConfigFile;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static com.swirlds.platform.system.status.PlatformStatus.ACTIVE;
import static com.swirlds.platform.system.status.PlatformStatus.FREEZE_COMPLETE;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.StreamSupport.stream;

import com.hedera.node.app.Hedera;
import com.hedera.node.app.config.IsEmbeddedTest;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.fixtures.state.FakeServicesRegistry;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.services.bdd.junit.hedera.embedded.EmbeddedHedera;
import com.hedera.services.bdd.junit.hedera.embedded.EmbeddedNode;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.AbstractFakePlatform;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeServiceMigrator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.listeners.PlatformStatusChangeNotification;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation support for {@link EmbeddedHedera}.
 */
public abstract class AbstractEmbeddedHedera implements EmbeddedHedera {
    private static final int NANOS_IN_A_SECOND = 1_000_000_000;
    private static final long VALID_START_TIME_OFFSET_SECS = 42;

    protected static final int MAX_PLATFORM_TXN_SIZE = 1024 * 6;
    protected static final int MAX_QUERY_RESPONSE_SIZE = 1024 * 1024 * 2;
    protected static final TransactionResponse OK_RESPONSE = TransactionResponse.getDefaultInstance();
    protected static final PlatformStatusChangeNotification ACTIVE_NOTIFICATION =
            new PlatformStatusChangeNotification(ACTIVE);
    protected static final PlatformStatusChangeNotification FREEZE_COMPLETE_NOTIFICATION =
            new PlatformStatusChangeNotification(FREEZE_COMPLETE);

    protected final PlatformState platformState = new PlatformState();
    protected final Map<AccountID, NodeId> nodeIds;
    protected final Map<NodeId, com.hedera.hapi.node.base.AccountID> accountIds;
    protected final FakeHederaState state = new FakeHederaState();
    protected final AccountID defaultNodeAccountId;
    protected final AddressBook addressBook;
    protected final NodeId defaultNodeId;
    protected final AtomicInteger nextNano = new AtomicInteger(0);
    protected final Hedera hedera;
    protected final HederaSoftwareVersion version;

    protected AbstractEmbeddedHedera(@NonNull final EmbeddedNode node) {
        requireNonNull(node);
        addressBook = loadAddressBook(node.getExternalPath(ADDRESS_BOOK));
        nodeIds = stream(spliteratorUnknownSize(addressBook.iterator(), 0), false)
                .collect(toMap(AbstractEmbeddedHedera::accountIdOf, Address::getNodeId));
        accountIds = stream(spliteratorUnknownSize(addressBook.iterator(), 0), false)
                .collect(toMap(Address::getNodeId, address -> parseAccount(address.getMemo())));
        defaultNodeId = addressBook.getNodeId(0);
        defaultNodeAccountId = fromPbj(accountIds.get(defaultNodeId));
        hedera = new Hedera(
                ConstructableRegistry.getInstance(),
                FakeServicesRegistry.FACTORY,
                new FakeServiceMigrator(),
                IsEmbeddedTest.YES,
                this::now);
        version = (HederaSoftwareVersion) hedera.getSoftwareVersion();
    }

    @Override
    public void start() {
        hedera.onStateInitialized(state, fakePlatform(), platformState, GENESIS, null);
        hedera.init(fakePlatform(), defaultNodeId);
        fakePlatform().start();
        fakePlatform().notifyListeners(ACTIVE_NOTIFICATION);
    }

    @Override
    public void stop() {
        fakePlatform().notifyListeners(FREEZE_COMPLETE_NOTIFICATION);
    }

    @Override
    public FakeHederaState state() {
        return state;
    }

    @Override
    public Timestamp nextValidStart() {
        var candidateNano = nextNano.getAndIncrement();
        if (candidateNano >= NANOS_IN_A_SECOND) {
            candidateNano = 0;
            nextNano.set(1);
        }
        return Timestamp.newBuilder()
                .setSeconds(now().getEpochSecond() - VALID_START_TIME_OFFSET_SECS)
                .setNanos(candidateNano)
                .build();
    }

    /**
     * Returns the fake platform to start and stop.
     *
     * @return the fake platform
     */
    protected abstract AbstractFakePlatform fakePlatform();

    protected static boolean isFree(@NonNull final Query query) {
        return query.hasCryptogetAccountBalance() || query.hasTransactionGetReceipt();
    }

    protected static TransactionResponse parseTransactionResponse(@NonNull final BufferedData responseBuffer) {
        try {
            return TransactionResponse.parseFrom(AbstractEmbeddedHedera.usedBytesFrom(responseBuffer));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected static Response parseQueryResponse(@NonNull final BufferedData responseBuffer) {
        try {
            return Response.parseFrom(AbstractEmbeddedHedera.usedBytesFrom(responseBuffer));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] usedBytesFrom(@NonNull final BufferedData responseBuffer) {
        final byte[] bytes = new byte[Math.toIntExact(responseBuffer.position())];
        responseBuffer.resetPosition();
        responseBuffer.readBytes(bytes);
        return bytes;
    }

    private static AddressBook loadAddressBook(@NonNull final Path path) {
        requireNonNull(path);
        final var configFile = loadConfigFile(path.toAbsolutePath());
        final var randomAddressBook = RandomAddressBookBuilder.create(new Random())
                .withSize(1)
                .withRealKeysEnabled(true)
                .build();
        final var sigCert = requireNonNull(randomAddressBook.iterator().next().getSigCert());
        final var addressBook = configFile.getAddressBook();
        return new AddressBook(stream(spliteratorUnknownSize(addressBook.iterator(), 0), false)
                .map(address -> address.copySetSigCert(sigCert))
                .toList());
    }

    private static AccountID accountIdOf(@NonNull final Address address) {
        return fromPbj(parseAccount(address.getMemo()));
    }
}
