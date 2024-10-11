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

package com.swirlds.platform.roster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.hapi.platform.state.Address;
import com.hedera.hapi.platform.state.AddressBook;
import com.hedera.hapi.platform.state.ConsensusSnapshot;
import com.hedera.hapi.platform.state.NodeId;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.internal.CryptoUtils;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.state.State;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RosterRetrieverTests {

    private static final Bytes HASH_555 = Bytes.wrap("555");
    private static final Bytes HASH_666 = Bytes.wrap("666");
    private static final Bytes HASH_777 = Bytes.wrap("777");

    private static final Roster ROSTER_555 = mock(Roster.class);
    private static final Roster ROSTER_666 = mock(Roster.class);
    private static final Roster ROSTER_777 = mock(Roster.class);

    private static final X509Certificate CERTIFICATE_1 = randomX509Certificate();
    private static final X509Certificate CERTIFICATE_2 = randomX509Certificate();
    private static final X509Certificate CERTIFICATE_3 = randomX509Certificate();

    private static final AddressBook ADDRESS_BOOK = AddressBook.newBuilder()
            .addresses(List.of(
                    Address.newBuilder()
                            .id(new NodeId(1L))
                            .weight(1L)
                            .signingCertificate(getCertBytes(CERTIFICATE_1))
                            // The agreementCertificate is unused, but required to prevent deserialization failure in
                            // States API.
                            .agreementCertificate(getCertBytes(CERTIFICATE_1))
                            .hostnameExternal("external1.com")
                            .portExternal(111)
                            .hostnameInternal("192.168.0.1")
                            .portInternal(222)
                            .build(),
                    Address.newBuilder()
                            .id(new NodeId(2L))
                            .weight(111L)
                            .signingCertificate(getCertBytes(CERTIFICATE_2))
                            // The agreementCertificate is unused, but required to prevent deserialization failure in
                            // States API.
                            .agreementCertificate(getCertBytes(CERTIFICATE_2))
                            .hostnameInternal("10.0.55.66")
                            .portInternal(222)
                            .build(),
                    Address.newBuilder()
                            .id(new NodeId(3L))
                            .weight(3L)
                            .signingCertificate(getCertBytes(CERTIFICATE_3))
                            // The agreementCertificate is unused, but required to prevent deserialization failure in
                            // States API.
                            .agreementCertificate(getCertBytes(CERTIFICATE_3))
                            .hostnameExternal("external3.com")
                            .portExternal(111)
                            .build()))
            .build();

    private static final Roster ROSTER_FROM_ADDRESS_BOOK = Roster.newBuilder()
            .rosterEntries(List.of(
                    RosterEntry.newBuilder()
                            .nodeId(1L)
                            .weight(1L)
                            .gossipCaCertificate(getCertBytes(CERTIFICATE_1))
                            .gossipEndpoint(List.of(
                                    ServiceEndpoint.newBuilder()
                                            .domainName("external1.com")
                                            .port(111)
                                            .build(),
                                    ServiceEndpoint.newBuilder()
                                            .ipAddressV4(Bytes.wrap(new byte[] {(byte) 192, (byte) 168, 0, 1}))
                                            .port(222)
                                            .build()))
                            .build(),
                    RosterEntry.newBuilder()
                            .nodeId(2L)
                            .weight(111L)
                            .gossipCaCertificate(getCertBytes(CERTIFICATE_2))
                            .gossipEndpoint(List.of(ServiceEndpoint.newBuilder()
                                    .ipAddressV4(Bytes.wrap(new byte[] {10, 0, 55, 66}))
                                    .port(222)
                                    .build()))
                            .build(),
                    RosterEntry.newBuilder()
                            .nodeId(3L)
                            .weight(3L)
                            .gossipCaCertificate(getCertBytes(CERTIFICATE_3))
                            .gossipEndpoint(List.of(ServiceEndpoint.newBuilder()
                                    .domainName("external3.com")
                                    .port(111)
                                    .build()))
                            .build()))
            .build();

    @Mock
    private State state;

    @Mock
    private ReadableStates platfromReadableStates;

    @Mock
    private ReadableSingletonState<PlatformState> readablePlatformState;

    @Mock
    private PlatformState platformState;

    @Mock
    private ConsensusSnapshot consensusSnapshot;

    @Mock
    private ReadableStates rosterReadableStates;

    @Mock
    private ReadableSingletonState<RosterState> readableRosterState;

    @Mock
    private RosterState rosterState;

    @Mock
    private ReadableKVState<ProtoBytes, Roster> rosterMap;

    @BeforeEach
    public void setup() {
        // Mock all the happy cases at once.  Use lenient() so that Mockito allows unused stubbing.
        lenient().doReturn(platfromReadableStates).when(state).getReadableStates("PlatformStateService");
        lenient().doReturn(readablePlatformState).when(platfromReadableStates).getSingleton("PLATFORM_STATE");
        lenient().doReturn(platformState).when(readablePlatformState).get();
        lenient().doReturn(ADDRESS_BOOK).when(platformState).addressBook();
        lenient().doReturn(consensusSnapshot).when(platformState).consensusSnapshot();
        lenient().doReturn(666L).when(consensusSnapshot).round();
        lenient().doReturn(rosterReadableStates).when(state).getReadableStates("RosterService");
        lenient().doReturn(readableRosterState).when(rosterReadableStates).getSingleton("ROSTER_STATE");
        lenient().doReturn(rosterState).when(readableRosterState).get();
        lenient()
                .doReturn(List.of(
                        RoundRosterPair.newBuilder()
                                .roundNumber(777L)
                                .activeRosterHash(HASH_777)
                                .build(),
                        RoundRosterPair.newBuilder()
                                .roundNumber(666L)
                                .activeRosterHash(HASH_666)
                                .build(),
                        RoundRosterPair.newBuilder()
                                .roundNumber(555L)
                                .activeRosterHash(HASH_555)
                                .build()))
                .when(rosterState)
                .roundRosterPairs();
        lenient().doReturn(rosterMap).when(rosterReadableStates).get("ROSTERS");
        lenient()
                .doReturn(ROSTER_555)
                .when(rosterMap)
                .get(eq(ProtoBytes.newBuilder().value(HASH_555).build()));
        lenient()
                .doReturn(ROSTER_666)
                .when(rosterMap)
                .get(eq(ProtoBytes.newBuilder().value(HASH_666).build()));
        lenient()
                .doReturn(ROSTER_777)
                .when(rosterMap)
                .get(eq(ProtoBytes.newBuilder().value(HASH_777).build()));
    }

    @Test
    void testGetRound() {
        assertEquals(666L, RosterRetriever.getRound(state));
    }

    private static Stream<Arguments> provideArgumentsForGetActiveRosterHash() {
        return Stream.of(
                Arguments.of(554L, null),
                Arguments.of(555L, HASH_555),
                Arguments.of(556L, HASH_555),
                Arguments.of(665L, HASH_555),
                Arguments.of(666L, HASH_666),
                Arguments.of(667L, HASH_666),
                Arguments.of(776L, HASH_666),
                Arguments.of(777L, HASH_777),
                Arguments.of(778L, HASH_777));
    }

    @ParameterizedTest
    @MethodSource("provideArgumentsForGetActiveRosterHash")
    void testGetActiveRosterHashForRound(final long round, final Bytes activeRosterHash) {
        assertEquals(activeRosterHash, RosterRetriever.getActiveRosterHash(state, round));
    }

    @Test
    void testRetrieve() {
        assertEquals(ROSTER_666, RosterRetriever.retrieve(state));
    }

    private static Stream<Arguments> provideArgumentsForRetrieveParametrized() {
        return Stream.of(
                Arguments.of(554L, ROSTER_FROM_ADDRESS_BOOK),
                Arguments.of(555L, ROSTER_555),
                Arguments.of(556L, ROSTER_555),
                Arguments.of(665L, ROSTER_555),
                Arguments.of(666L, ROSTER_666),
                Arguments.of(667L, ROSTER_666),
                Arguments.of(776L, ROSTER_666),
                Arguments.of(777L, ROSTER_777),
                Arguments.of(778L, ROSTER_777));
    }

    @ParameterizedTest
    @MethodSource("provideArgumentsForRetrieveParametrized")
    void testRetrieveParametrized(final long round, final Roster roster) {
        doReturn(round).when(consensusSnapshot).round();
        assertEquals(roster, RosterRetriever.retrieve(state));
    }

    private static Stream<Arguments> provideArgumentsForRetrieveForRound() {
        return Stream.of(
                Arguments.of(554L, null),
                Arguments.of(555L, ROSTER_555),
                Arguments.of(556L, ROSTER_555),
                Arguments.of(665L, ROSTER_555),
                Arguments.of(666L, ROSTER_666),
                Arguments.of(667L, ROSTER_666),
                Arguments.of(776L, ROSTER_666),
                Arguments.of(777L, ROSTER_777),
                Arguments.of(778L, ROSTER_777));
    }

    @ParameterizedTest
    @MethodSource("provideArgumentsForRetrieveForRound")
    void testRetrieveForRound(final long round, final Roster roster) {
        assertEquals(roster, RosterRetriever.retrieve(state, round));
    }

    @Test
    void testRetrieveAddressBook() {
        // First try a very old round for which there's not a roster
        doReturn(554L).when(consensusSnapshot).round();
        assertEquals(ROSTER_FROM_ADDRESS_BOOK, RosterRetriever.retrieve(state));

        // Then try a newer round, but remove the roster from the RosterMap
        doReturn(666L).when(consensusSnapshot).round();
        doReturn(null)
                .when(rosterMap)
                .get(eq(ProtoBytes.newBuilder().value(HASH_666).build()));
        assertEquals(ROSTER_FROM_ADDRESS_BOOK, RosterRetriever.retrieve(state));
    }

    public static X509Certificate randomX509Certificate() {
        try {
            final SecureRandom secureRandom = CryptoUtils.getDetRandom();

            final KeyPairGenerator rsaKeyGen = KeyPairGenerator.getInstance("RSA");
            rsaKeyGen.initialize(3072, secureRandom);
            final KeyPair rsaKeyPair1 = rsaKeyGen.generateKeyPair();

            final String name = "CN=Bob";
            return CryptoStatic.generateCertificate(name, rsaKeyPair1, name, rsaKeyPair1, secureRandom);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Bytes getCertBytes(X509Certificate certificate) {
        try {
            return Bytes.wrap(certificate.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
