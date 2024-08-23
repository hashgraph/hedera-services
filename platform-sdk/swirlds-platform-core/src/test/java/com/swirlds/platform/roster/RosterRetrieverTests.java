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
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.State;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
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

    private static final byte[] CERTIFICATE_BYTES_1 = new byte[] {1, 2, 3};
    private static final byte[] CERTIFICATE_BYTES_2 = new byte[] {4, 5, 6};
    private static final byte[] CERTIFICATE_BYTES_3 = new byte[] {7, 8, 9};

    private static final X509Certificate CERTIFICATE_1 = mock(X509Certificate.class);
    private static final X509Certificate CERTIFICATE_2 = mock(X509Certificate.class);
    private static final X509Certificate CERTIFICATE_3 = mock(X509Certificate.class);

    private static final AddressBook ADDRESS_BOOK = new AddressBook(List.of(
            new Address()
                    .copySetNodeId(new NodeId(1L))
                    .copySetWeight(1L)
                    .copySetSigCert(CERTIFICATE_1)
                    .copySetHostnameExternal("external1.com")
                    .copySetPortExternal(111)
                    .copySetHostnameInternal("192.168.0.1")
                    .copySetPortInternal(222),
            new Address()
                    .copySetNodeId(new NodeId(2L))
                    .copySetWeight(111L)
                    .copySetSigCert(CERTIFICATE_2)
                    .copySetHostnameInternal("10.0.55.66")
                    .copySetPortInternal(222),
            new Address()
                    .copySetNodeId(new NodeId(3L))
                    .copySetWeight(3L)
                    .copySetSigCert(CERTIFICATE_3)
                    .copySetHostnameExternal("external3.com")
                    .copySetPortExternal(111)));

    @Mock(extraInterfaces = {MerkleRoot.class})
    private State state;

    @Mock
    private PlatformState platformState;

    @Mock
    private ReadableStates readableStates;

    @Mock
    private ReadableSingletonState<RosterState> readableRosterState;

    @Mock
    private RosterState rosterState;

    @Mock
    private ReadableKVState<ProtoBytes, Roster> rosterMap;

    static {
        try {
            lenient().doReturn(CERTIFICATE_BYTES_1).when(CERTIFICATE_1).getTBSCertificate();
            lenient().doReturn(CERTIFICATE_BYTES_2).when(CERTIFICATE_2).getTBSCertificate();
            lenient().doReturn(CERTIFICATE_BYTES_3).when(CERTIFICATE_3).getTBSCertificate();
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    public void setup() {
        // Mock all the happy cases at once.  Use lenient() so that Mockito allows unused stubbing.
        lenient().doReturn(platformState).when((MerkleRoot) state).getPlatformState();
        lenient().doReturn(666L).when(platformState).getRound();
        lenient().doReturn(readableStates).when(state).getReadableStates("RosterServiceImpl");
        lenient().doReturn(readableRosterState).when(readableStates).getSingleton("ROSTER_STATE");
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
        lenient().doReturn(rosterMap).when(readableStates).get("ROSTER");
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
        lenient().doReturn(ADDRESS_BOOK).when(platformState).getAddressBook();
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
    void testGetActiveRosterHash(final long round, final Bytes activeRosterHash) {
        doReturn(round).when(platformState).getRound();
        assertEquals(activeRosterHash, RosterRetriever.getActiveRosterHash(state));
    }

    @Test
    void testRetrieve() {
        assertEquals(ROSTER_666, RosterRetriever.retrieve(state));
    }

    private static Stream<Arguments> provideArgumentsForRetrieveParametrized() {
        return Stream.of(
                // Arguments.of(554L, null),
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
        doReturn(round).when(platformState).getRound();
        assertEquals(roster, RosterRetriever.retrieve(state));
    }

    @Test
    void testRetrieveAddressBook() {
        Roster expected = Roster.newBuilder()
                .rosters(List.of(
                        RosterEntry.newBuilder()
                                .nodeId(1L)
                                .weight(1L)
                                .gossipCaCertificate(Bytes.wrap(CERTIFICATE_BYTES_1))
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
                                .gossipCaCertificate(Bytes.wrap(CERTIFICATE_BYTES_2))
                                .gossipEndpoint(List.of(ServiceEndpoint.newBuilder()
                                        .ipAddressV4(Bytes.wrap(new byte[] {10, 0, 55, 66}))
                                        .port(222)
                                        .build()))
                                .build(),
                        RosterEntry.newBuilder()
                                .nodeId(3L)
                                .weight(3L)
                                .gossipCaCertificate(Bytes.wrap(CERTIFICATE_BYTES_3))
                                .gossipEndpoint(List.of(ServiceEndpoint.newBuilder()
                                        .domainName("external3.com")
                                        .port(111)
                                        .build()))
                                .build()))
                .build();

        // First try a very old round for which there's not a roster
        doReturn(554L).when(platformState).getRound();
        assertEquals(expected, RosterRetriever.retrieve(state));

        // Then try a newer round, but remove the roster from the RosterMap
        doReturn(666L).when(platformState).getRound();
        doReturn(null)
                .when(rosterMap)
                .get(eq(ProtoBytes.newBuilder().value(HASH_666).build()));
        assertEquals(expected, RosterRetriever.retrieve(state));
    }
}
