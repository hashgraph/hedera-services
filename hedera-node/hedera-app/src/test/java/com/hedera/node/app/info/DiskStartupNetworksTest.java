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

package com.hedera.node.app.info;

import static com.hedera.node.app.info.DiskStartupNetworks.ARCHIVE;
import static com.hedera.node.app.info.DiskStartupNetworks.GENESIS_NETWORK_JSON;
import static com.hedera.node.app.info.DiskStartupNetworks.OVERRIDE_NETWORK_JSON;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.Network;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiskStartupNetworksTest {
    private static final long NODE_ID = 123L;
    private static final long ROUND_NO = 666L;
    private static final Bytes EXPECTED_LEDGER_ID = Bytes.fromBase64("Lw==");

    private static Network networkWithTssKeys;
    private static Network networkWithoutTssKeys;

    @BeforeAll
    static void setupAll() throws IOException, ParseException {
        try (final var fin = DiskStartupNetworks.class.getClassLoader().getResourceAsStream("bootstrap/network.json")) {
            networkWithTssKeys = Network.JSON.parse(new ReadableStreamingData(requireNonNull(fin)));
            networkWithoutTssKeys = networkWithTssKeys
                    .copyBuilder()
                    .tssMessages(emptyList())
                    .ledgerId(Bytes.EMPTY)
                    .build();
        }
    }

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private StartupNetworks.TssDirectoryFactory tssDirectoryFactory;

    @Mock
    private TssParticipantDirectory directory;

    @Mock
    private TssBaseService tssBaseService;

    @TempDir
    Path tempDir;

    private DiskStartupNetworks subject;

    @BeforeEach
    void setUp() {
        final var config = HederaTestConfigBuilder.create()
                .withValue("networkAdmin.upgradeSysFilesLoc", tempDir.toString())
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(config, 123L));
        subject = new DiskStartupNetworks(NODE_ID, configProvider, tssBaseService, tssDirectoryFactory);
    }

    @Test
    void throwsOnMissingGenesisNetwork() {
        assertThatThrownBy(() -> subject.genesisNetworkOrThrow()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void throwsOnMissingMigrationNetwork() {
        assertThatThrownBy(() -> subject.migrationNetworkOrThrow()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findsAvailableGenesisNetwork() throws IOException {
        putJsonAt(GENESIS_NETWORK_JSON, WithTssKeys.NO);
        final var network = subject.genesisNetworkOrThrow();
        assertThat(network).isEqualTo(networkWithoutTssKeys);
    }

    @Test
    void findsAvailableMigrationNetwork() throws IOException {
        givenValidTssMessages();
        putJsonAt(OVERRIDE_NETWORK_JSON, WithTssKeys.YES);
        final var network = subject.migrationNetworkOrThrow();
        assertThat(network).isEqualTo(networkWithTssKeys);
    }

    @Test
    void archivesGenesisNetworks() throws IOException {
        putJsonAt(GENESIS_NETWORK_JSON, WithTssKeys.NO);
        final var genesisJson = tempDir.resolve(GENESIS_NETWORK_JSON);

        assertThat(Files.exists(genesisJson)).isTrue();

        subject.archiveJsonFiles();

        assertThat(Files.exists(genesisJson)).isFalse();
        final var archivedGenesisJson = tempDir.resolve(ARCHIVE + File.separator + GENESIS_NETWORK_JSON);
        assertThat(Files.exists(archivedGenesisJson)).isTrue();
    }

    @Test
    void archivesUnscopedOverrideNetwork() throws IOException {
        putJsonAt(OVERRIDE_NETWORK_JSON, WithTssKeys.YES);
        final var overrideJson = tempDir.resolve(OVERRIDE_NETWORK_JSON);

        assertThat(Files.exists(overrideJson)).isTrue();

        subject.archiveJsonFiles();

        assertThat(Files.exists(overrideJson)).isFalse();
        final var archivedGenesisJson = tempDir.resolve(ARCHIVE + File.separator + OVERRIDE_NETWORK_JSON);
        assertThat(Files.exists(archivedGenesisJson)).isTrue();
    }

    @Test
    void archivesScopedOverrideNetwork() throws IOException {
        Files.createDirectory(tempDir.resolve("" + ROUND_NO));
        putJsonAt(ROUND_NO + File.separator + OVERRIDE_NETWORK_JSON, WithTssKeys.YES);
        final var overrideJson = tempDir.resolve(ROUND_NO + File.separator + OVERRIDE_NETWORK_JSON);

        assertThat(Files.exists(overrideJson)).isTrue();

        subject.archiveJsonFiles();

        assertThat(Files.exists(overrideJson)).isFalse();
        final var archivedGenesisJson =
                tempDir.resolve(ARCHIVE + File.separator + ROUND_NO + File.separator + OVERRIDE_NETWORK_JSON);
        assertThat(Files.exists(archivedGenesisJson)).isTrue();
    }

    @Test
    void overrideNetworkOnlyStillAvailableAtSameRound() throws IOException {
        putJsonAt(OVERRIDE_NETWORK_JSON, WithTssKeys.YES);
        givenValidTssMessages();

        final var maybeOverrideNetwork = subject.overrideNetworkFor(ROUND_NO);
        assertThat(maybeOverrideNetwork).isPresent();
        final var overrideNetwork = maybeOverrideNetwork.orElseThrow();
        assertThat(overrideNetwork).isEqualTo(networkWithTssKeys);

        subject.setOverrideRound(ROUND_NO);
        final var unscopedOverrideJson = tempDir.resolve(OVERRIDE_NETWORK_JSON);
        assertThat(Files.exists(unscopedOverrideJson)).isFalse();
        final var scopedOverrideJson = tempDir.resolve(+ROUND_NO + File.separator + OVERRIDE_NETWORK_JSON);
        assertThat(Files.exists(scopedOverrideJson)).isTrue();

        final var maybeRepeatedOverrideNetwork = subject.overrideNetworkFor(ROUND_NO);
        assertThat(maybeRepeatedOverrideNetwork).isPresent();
        final var repeatedOverrideNetwork = maybeRepeatedOverrideNetwork.orElseThrow();
        assertThat(repeatedOverrideNetwork).isEqualTo(networkWithTssKeys);

        final var maybeOverrideNetworkAfterRound = subject.overrideNetworkFor(ROUND_NO + 1);
        assertThat(maybeOverrideNetworkAfterRound).isEmpty();
    }

    private enum WithTssKeys {
        YES,
        NO
    }

    private void givenValidTssMessages() {
        given(tssDirectoryFactory.create(any(), anyLong(), anyInt())).willReturn(directory);
        given(tssBaseService.ledgerIdFrom(eq(directory), any())).willReturn(EXPECTED_LEDGER_ID);
    }

    private void putJsonAt(@NonNull final String fileName, @NonNull final WithTssKeys withTssKeys) throws IOException {
        final var loc = tempDir.resolve(fileName);
        try (final var fout = Files.newOutputStream(loc)) {
            Network.JSON.write(
                    withTssKeys == WithTssKeys.YES ? networkWithTssKeys : networkWithoutTssKeys,
                    new WritableStreamingData(fout));
        }
    }
}
