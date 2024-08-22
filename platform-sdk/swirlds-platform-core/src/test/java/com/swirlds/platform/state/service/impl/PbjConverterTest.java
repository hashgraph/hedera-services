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

package com.swirlds.platform.state.service.impl;

import static com.swirlds.common.test.fixtures.RandomUtils.nextInt;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.common.test.fixtures.RandomUtils.randomInstant;
import static com.swirlds.common.test.fixtures.RandomUtils.randomString;
import static com.swirlds.platform.state.service.impl.PbjConverter.toPbjTimestamp;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.internal.CryptoUtils;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.crypto.SerializableX509Certificate;
import com.swirlds.platform.state.MinimumJudgeInfo;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

@SuppressWarnings("removal")
class PbjConverterTest {

    public static final NodeId NODE_ID_1 = new NodeId(1);
    public static final NodeId NODE_ID_2 = new NodeId(2);
    private final Random random = new Random();

    @Test
    void testToPbjPlatformState() {
        final PlatformState platformState = new PlatformState();
        platformState.setCreationSoftwareVersion(randomSoftwareVersion());
        platformState.setRoundsNonAncient(nextInt());
        platformState.setLastFrozenTime(randomInstant(random));
        platformState.setLegacyRunningEventHash(randomHash());
        platformState.setLowestJudgeGenerationBeforeBirthRoundMode(nextInt());
        platformState.setLastRoundBeforeBirthRoundMode(nextInt());
        platformState.setFirstVersionInBirthRoundMode(randomSoftwareVersion());
        platformState.setSnapshot(randomSnapshot());
        platformState.setAddressBook(randomAddressBook());

        final com.hedera.hapi.platform.state.PlatformState pbjPlatformState =
                PbjConverter.toPbjPlatformState(platformState);

        assertEquals(
                platformState.getCreationSoftwareVersion().getPbjSemanticVersion(),
                pbjPlatformState.creationSoftwareVersion());
        assertEquals(platformState.getRoundsNonAncient(), pbjPlatformState.roundsNonAncient());
        assertEquals(
                platformState.getLastFrozenTime().getEpochSecond(),
                pbjPlatformState.lastFrozenTime().seconds());
        assertEquals(platformState.getLegacyRunningEventHash().getBytes(), pbjPlatformState.legacyRunningEventHash());
        assertEquals(
                platformState.getLowestJudgeGenerationBeforeBirthRoundMode(),
                pbjPlatformState.lowestJudgeGenerationBeforeBirthRoundMode());
        assertEquals(
                platformState.getFirstVersionInBirthRoundMode().getPbjSemanticVersion(),
                pbjPlatformState.firstVersionInBirthRoundMode());

        assertSnapshot(platformState.getSnapshot(), pbjPlatformState.consensusSnapshot());
        assertAddressBook(platformState.getAddressBook(), pbjPlatformState.addressBook());
    }

    @Test
    void testToPbjConsensusSnapshot() {
        final ConsensusSnapshot snapshot = randomSnapshot();
        final com.hedera.hapi.platform.state.ConsensusSnapshot pbjSnapshot =
                PbjConverter.toPbjConsensusSnapshot(snapshot);
        assertSnapshot(snapshot, pbjSnapshot);
    }

    @Test
    void testToPbjConsensusSnapshot_null() {
        assertNull(PbjConverter.toPbjConsensusSnapshot(null));
    }

    @Test
    void testToPbjTimestamp_null() {
        assertNull(toPbjTimestamp(null));
    }

    @Test
    void testToPbjTimestamp() {
        final Instant instant = randomInstant(random);
        final Timestamp pbjTimestamp = toPbjTimestamp(instant);
        assertEquals(instant.getEpochSecond(), pbjTimestamp.seconds());
    }

    @Test
    void testFromPbjTimestamp_null() {
        assertNull(PbjConverter.fromPbjTimestamp(null));
    }

    @Test
    void testFromPbjTimestamp() {
        final Instant instant = randomInstant(random);
        final Timestamp pbjTimestamp = toPbjTimestamp(instant);
        assertEquals(instant, PbjConverter.fromPbjTimestamp(pbjTimestamp));
    }

    @Test
    void testFromPbjAddressBook_null() {
        assertNull(PbjConverter.fromPbjAddressBook(null));
    }

    @Test
    void testFromPbjAddressBook() {
        final com.hedera.hapi.platform.state.AddressBook pbjAddressBook = randomPbjAddressBook();
        final AddressBook addressBook = PbjConverter.fromPbjAddressBook(pbjAddressBook);
        assertAddressBook(addressBook, pbjAddressBook);
    }

    @Test
    void testFRomConsensusSnapshot_null() {
        assertNull(PbjConverter.fromPbjConsensusSnapshot(null));
    }

    @Test
    void testFromPbjConsensusSnapshot() {
        final com.hedera.hapi.platform.state.ConsensusSnapshot pbjSnapshot = randomPbjSnapshot();
        final ConsensusSnapshot snapshot = PbjConverter.fromPbjConsensusSnapshot(pbjSnapshot);
        assertSnapshot(snapshot, pbjSnapshot);
    }

    private com.hedera.hapi.platform.state.ConsensusSnapshot randomPbjSnapshot() {
        Instant instant = randomInstant(random);
        return new com.hedera.hapi.platform.state.ConsensusSnapshot(
                nextInt(),
                asList(randomHash().getBytes(), randomHash().getBytes()),
                asList(
                        new com.hedera.hapi.platform.state.MinimumJudgeInfo(nextInt(), nextInt()),
                        new com.hedera.hapi.platform.state.MinimumJudgeInfo(nextInt(), nextInt())),
                nextInt(),
                new Timestamp(instant.getEpochSecond(), instant.getNano()));
    }

    private com.hedera.hapi.platform.state.AddressBook randomPbjAddressBook() {
        return new com.hedera.hapi.platform.state.AddressBook(
                nextInt(),
                new com.hedera.hapi.platform.state.NodeId(NODE_ID_2.id() + 1),
                asList(randomPbjAddress(NODE_ID_1), randomPbjAddress(NODE_ID_2)));
    }

    private void assertAddressBook(AddressBook addressBook, com.hedera.hapi.platform.state.AddressBook pbjAddressBook) {
        assertEquals(addressBook.getRound(), pbjAddressBook.round());
        assertEquals(addressBook.getSize(), pbjAddressBook.addresses().size());
        assertAddress(
                addressBook.getAddress(NODE_ID_1), pbjAddressBook.addresses().get(0));
        assertAddress(
                addressBook.getAddress(NODE_ID_2), pbjAddressBook.addresses().get(1));
    }

    private void assertAddress(Address address, com.hedera.hapi.platform.state.Address pbjAddress) {
        assertEquals(address.getNodeId().id(), pbjAddress.id().id());
        assertEquals(address.getNickname(), pbjAddress.nickname());
        assertEquals(address.getSelfName(), pbjAddress.selfName());
        assertEquals(address.getWeight(), pbjAddress.weight());
        assertEquals(address.getHostnameInternal(), pbjAddress.hostnameInternal());
        assertEquals(address.getPortInternal(), pbjAddress.portInternal());
        assertEquals(address.getHostnameExternal(), pbjAddress.hostnameExternal());
        assertEquals(address.getPortExternal(), pbjAddress.portExternal());
        try {
            assertArrayEquals(
                    address.getAgreeCert().getEncoded(),
                    pbjAddress.agreementCertificate().toByteArray());
            assertArrayEquals(
                    address.getSigCert().getEncoded(),
                    pbjAddress.signingCertificate().toByteArray());
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private void assertSnapshot(
            ConsensusSnapshot snapshot, com.hedera.hapi.platform.state.ConsensusSnapshot pbjSnapshot) {
        assertEquals(snapshot.round(), pbjSnapshot.round());
        assertEquals(snapshot.judgeHashes().size(), pbjSnapshot.judgeHashes().size());
        assertEquals(
                snapshot.judgeHashes().get(0).getBytes(),
                pbjSnapshot.judgeHashes().get(0));
        assertEquals(
                snapshot.judgeHashes().get(1).getBytes(),
                pbjSnapshot.judgeHashes().get(1));
        assertJudgeInfos(snapshot.getMinimumJudgeInfoList(), pbjSnapshot.minimumJudgeInfoList());
        assertEquals(snapshot.nextConsensusNumber(), pbjSnapshot.nextConsensusNumber());
        assertEquals(
                snapshot.consensusTimestamp().getEpochSecond(),
                pbjSnapshot.consensusTimestamp().seconds());
    }

    private void assertJudgeInfos(
            List<MinimumJudgeInfo> expected, List<com.hedera.hapi.platform.state.MinimumJudgeInfo> actual) {
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(
                    expected.get(i).minimumJudgeAncientThreshold(),
                    actual.get(i).minimumJudgeAncientThreshold());
            assertEquals(expected.get(i).round(), actual.get(i).round());
        }
    }

    private ConsensusSnapshot randomSnapshot() {
        return new ConsensusSnapshot(
                nextInt(),
                asList(randomHash(), randomHash()),
                asList(new MinimumJudgeInfo(nextInt(), nextInt()), new MinimumJudgeInfo(nextInt(), nextInt())),
                nextInt(),
                randomInstant(random));
    }

    private AddressBook randomAddressBook() {
        final AddressBook addresses = new AddressBook();
        addresses.setRound(nextInt());
        addresses.add(randomAddress(NODE_ID_1));
        addresses.add(randomAddress(NODE_ID_2));

        return addresses;
    }

    private Address randomAddress(NodeId nodeId) {
        return new Address(
                nodeId,
                randomString(random, 10),
                randomString(random, 10),
                nextInt(),
                randomString(random, 10),
                nextInt(),
                randomString(random, 10),
                nextInt(),
                randomX509Certificate(),
                randomX509Certificate(),
                randomString(random, 10));
    }

    private com.hedera.hapi.platform.state.Address randomPbjAddress(NodeId nodeId) {
        return new com.hedera.hapi.platform.state.Address(
                new com.hedera.hapi.platform.state.NodeId(nodeId.id()),
                randomString(random, 10),
                randomString(random, 10),
                nextInt(),
                randomString(random, 10),
                nextInt(),
                randomString(random, 10),
                nextInt(),
                randomEncodedCertificate(),
                randomEncodedCertificate(),
                randomString(random, 10));
    }

    private Bytes randomEncodedCertificate() {
        try {
            return Bytes.wrap(randomX509Certificate().getCertificate().getEncoded());
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private SoftwareVersion randomSoftwareVersion() {
        return new BasicSoftwareVersion(nextInt(1, 100));
    }

    private SerializableX509Certificate randomX509Certificate() {
        try {
            final SecureRandom secureRandom = CryptoUtils.getDetRandom();

            final KeyPairGenerator rsaKeyGen = KeyPairGenerator.getInstance("RSA");
            rsaKeyGen.initialize(3072, secureRandom);
            final KeyPair rsaKeyPair1 = rsaKeyGen.generateKeyPair();

            final String name = "CN=Bob";
            return new SerializableX509Certificate(
                    CryptoStatic.generateCertificate(name, rsaKeyPair1, name, rsaKeyPair1, secureRandom));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
