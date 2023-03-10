/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state.merkle;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StateLifecyclesTest extends ResponsibleVMapUser {
    //    private final String signedStateDir = "src/test/resources/signedState/";
    //
    //    @Mock
    //    private AddressBook addressBook;
    //
    //    @Test
    //    void noOpConstructorExists() {
    //        assertDoesNotThrow(() -> new MerkleHederaState());
    //    }
    //
    //    @Test
    //    void testGenesisState() {
    //        ClassLoaderHelper.loadClassPathDependencies();
    //
    //        final var currentVersion =
    //                SemanticVersions.SEMANTIC_VERSIONS.deployedSoftwareVersion().getServices();
    //        final var migration = Hedera.registerServiceSchemasForMigration(currentVersion);
    //
    //        final var merkleState = tracked(new MerkleHederaState(migration, (e, m, p) -> {}, (r, s, ds, m) -> {}));
    //
    //        final var platform = createMockPlatformWithCrypto();
    //        final var addressBook = createPretendBookFrom(platform, true);
    //        given(platform.getAddressBook()).willReturn(addressBook);
    //        final var recordsRunningHashLeaf = new RecordsRunningHashLeaf();
    //        recordsRunningHashLeaf.setRunningHash(new RunningHash(EMPTY_HASH));
    //        merkleState.setChild(StateChildIndices.RECORD_STREAM_RUNNING_HASH, recordsRunningHashLeaf);
    //        final var app = createApp(platform);
    //
    //        APPS.save(platform.getSelfId().getId(), app);
    //
    //        assertDoesNotThrow(() -> merkleState.init(platform, new DualStateImpl(), InitTrigger.GENESIS, null));
    //    }
    //
    //    @Test
    //    void testLoadingMHState() {
    //        ClassLoaderHelper.loadClassPathDependencies();
    //        final AtomicReference<SignedState> ref = new AtomicReference<>();
    //        assertThrows(
    //                com.swirlds.common.io.exceptions.ClassNotFoundException.class,
    //                () -> ref.set(loadSignedState(signedStateDir + "MHS/SignedState.swh")));
    //
    //        // TODO - continue as below after fixing ClassNotFoundException, which
    //        // should satisfy Sonar's coverage requirements for MerkleHederaState
    //        /*
    //        final var mockPlatform = createMockPlatformWithCrypto();
    //        given(mockPlatform.getAddressBook()).willReturn(addressBook);
    //        final var mhs = (MerkleHederaState) ref.get().getSwirldState();
    //        tracked(mhs).init(mockPlatform, new DualStateImpl(), RESTART, forHapiAndHedera("0.30.0", "0.30.5"));
    //        */
    //    }
    //
    //    private Platform createMockPlatformWithCrypto() {
    //        final var platform = mock(Platform.class);
    //        when(platform.getSelfId()).thenReturn(new NodeId(false, 0));
    //        when(platform.getCryptography())
    //                .thenReturn(new CryptoEngine(getStaticThreadManager(), CryptoConfigUtils.MINIMAL_CRYPTO_CONFIG));
    //        assertNotNull(platform.getCryptography());
    //        return platform;
    //    }
    //
    //    private AddressBook createPretendBookFrom(final Platform platform, final boolean withKeyDetails) {
    //        final var pubKey = mock(PublicKey.class);
    //        given(pubKey.getAlgorithm()).willReturn("EC");
    //        if (withKeyDetails) {
    //            given(pubKey.getEncoded()).willReturn(Longs.toByteArray(Long.MAX_VALUE));
    //        }
    //        final var nodeId = platform.getSelfId().getId();
    //        final var address = new Address(
    //                nodeId,
    //                "",
    //                "",
    //                1L,
    //                false,
    //                null,
    //                -1,
    //                Ints.toByteArray(123456789),
    //                -1,
    //                null,
    //                -1,
    //                null,
    //                -1,
    //                new SerializablePublicKey(pubKey),
    //                null,
    //                new SerializablePublicKey(pubKey),
    //                "");
    //        return new AddressBook(List.of(address));
    //    }
    //
    //    private static HederaApp createApp(final Platform platform) {
    //        return DaggerHederaApp.builder()
    //                .initialHash(new Hash())
    //                .platform(platform)
    //                .crypto(CryptographyHolder.get())
    //                .consoleCreator((ignore, visible) -> null)
    //                .selfId(platform.getSelfId().getId())
    //                .staticAccountMemo("memo")
    //                .maxSignedTxnSize(MAX_SIGNED_TXN_SIZE)
    //                .bootstrapProps(new BootstrapProperties())
    //                .build();
    //    }
    //
    //    private static SignedState loadSignedState(final String path) throws IOException {
    //        final var signedPair = SignedStateFileReader.readStateFile(Paths.get(path));
    //        // Because it's possible we are loading old data, we cannot check equivalence of the hash.
    //        Assertions.assertNotNull(signedPair.signedState());
    //        return signedPair.signedState();
    //    }
}
