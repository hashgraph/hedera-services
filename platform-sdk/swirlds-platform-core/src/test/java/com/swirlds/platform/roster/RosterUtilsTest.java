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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformStateAccessor;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class RosterUtilsTest {

    private final AddressBook addressBook = mock(AddressBook.class);
    private final Address address = mock(Address.class);
    private final NodeId nodeId = new NodeId(1);
    private final X509Certificate certificate = mock(X509Certificate.class);

    @Test
    void testHashOfEmptyRoster() {
        final Roster emptyRoster =
                Roster.newBuilder().rosters(new ArrayList<>()).build();
        assertNotNull(RosterUtils.hashOf(emptyRoster));
    }

    @Test
    void testHashOfRosterWithEntries() {
        final List<RosterEntry> entries = new ArrayList<>();
        entries.add(RosterEntry.newBuilder()
                .nodeId(1)
                .weight(1)
                .gossipCaCertificate(Bytes.EMPTY)
                .build());
        final Roster roster = Roster.newBuilder().rosters(entries).build();
        assertNotNull(RosterUtils.hashOf(roster));
    }

    @Test
    void testHashOfRosterNoSuchAlgorithmException() {
        try (final MockedStatic<MessageDigest> mocked = mockStatic(MessageDigest.class)) {
            mocked.when(() -> MessageDigest.getInstance(anyString())).thenThrow(new NoSuchAlgorithmException());
            final Roster roster = Roster.newBuilder().rosters(new ArrayList<>()).build();
            assertThrows(IllegalStateException.class, () -> RosterUtils.hashOf(roster));
        }
    }

    @Test
    @DisplayName("Test construct initial roster with software upgrade")
    void testConstructInitialRosterWithSoftwareUpgrade() {
        final SoftwareVersion version = mock(SoftwareVersion.class);
        final ReservedSignedState initialState = mock(ReservedSignedState.class);
        final SignedState state = mock(SignedState.class);
        final MerkleRoot stateMerkleRoot = mock(MerkleRoot.class);
        final WritableRosterStore rosterStore = mock(WritableRosterStore.class);
        final Roster candidateRoster = mock(Roster.class);
        final PlatformStateAccessor platformState = mock(PlatformStateAccessor.class);

        when(initialState.get()).thenReturn(state);
        when(state.getState()).thenReturn(stateMerkleRoot);
        when(state.getState().getWritableRosterStore()).thenReturn(rosterStore);
        when(stateMerkleRoot.getReadablePlatformState()).thenReturn(platformState);
        when(state.getState().getWritablePlatformState()).thenReturn(platformState);
        when(rosterStore.getCandidateRoster()).thenReturn(candidateRoster);

        assertNotNull(RosterUtils.constructInitialRoster(version, initialState, addressBook));
    }

    @Test
    @DisplayName("Test construct initial roster without software upgrade")
    void testConstructInitialRosterWithoutSoftwareUpgrade() {
        final SoftwareVersion version = mock(SoftwareVersion.class);
        final ReservedSignedState initialState = mock(ReservedSignedState.class);
        final SignedState state = mock(SignedState.class);
        final MerkleRoot stateMerkleRoot = mock(MerkleRoot.class);
        final WritableRosterStore rosterStore = mock(WritableRosterStore.class);
        final PlatformStateAccessor platformState = mock(PlatformStateAccessor.class);

        when(initialState.get()).thenReturn(state);
        when(state.getState()).thenReturn(stateMerkleRoot);
        when(stateMerkleRoot.getWritableRosterStore()).thenReturn(rosterStore);
        when(state.getState().getWritableRosterStore()).thenReturn(rosterStore);
        when(stateMerkleRoot.getReadablePlatformState()).thenReturn(platformState);
        when(state.getState().getWritablePlatformState()).thenReturn(platformState);
        when(rosterStore.getCandidateRoster()).thenReturn(null);

        assertNotNull(RosterUtils.constructInitialRoster(version, initialState, addressBook));
    }

    @Test
    @DisplayName(
            "Test construct initial roster without software upgrade but with an active roster present in the state")
    void testConstructInitialRosterWithoutSoftwareUpgradeWithActiveRoster() {
        final SoftwareVersion version = mock(SoftwareVersion.class);
        final ReservedSignedState initialState = mock(ReservedSignedState.class);
        final SignedState state = mock(SignedState.class);
        final MerkleRoot stateMerkleRoot = mock(MerkleRoot.class);
        final WritableRosterStore rosterStore = mock(WritableRosterStore.class);
        final Roster activeRoster = mock(Roster.class);
        final PlatformStateAccessor platformState = mock(PlatformStateAccessor.class);

        when(initialState.get()).thenReturn(state);
        when(state.getState()).thenReturn(stateMerkleRoot);
        when(stateMerkleRoot.getWritableRosterStore()).thenReturn(rosterStore);
        when(rosterStore.getActiveRoster()).thenReturn(activeRoster);
        when(stateMerkleRoot.getWritableRosterStore()).thenReturn(rosterStore);
        when(state.getState().getWritableRosterStore()).thenReturn(rosterStore);
        when(stateMerkleRoot.getReadablePlatformState()).thenReturn(platformState);
        when(state.getState().getWritablePlatformState()).thenReturn(platformState);

        assertSame(activeRoster, RosterUtils.constructInitialRoster(version, initialState, addressBook));
    }

    @Test
    void testCreateRoster() {
        when(addressBook.getSize()).thenReturn(1);
        when(addressBook.getNodeId(0)).thenReturn(nodeId);
        when(addressBook.getAddress(nodeId)).thenReturn(address);

        assertNotNull(RosterUtils.createRoster(addressBook));
    }

    @Test
    void testCreateRosterWithNullAddressBook() {
        assertThrows(NullPointerException.class, () -> RosterUtils.createRoster(null));
    }

    @Test
    void testToRosterEntry() throws CertificateEncodingException {
        when(address.getSigCert()).thenReturn(certificate);
        when(certificate.getEncoded()).thenReturn(new byte[] {1, 2, 3});
        when(address.getHostnameInternal()).thenReturn("internalhostname");
        when(address.getPortInternal()).thenReturn(8080);
        when(address.getHostnameExternal()).thenReturn("externalhostname");
        when(address.getPortExternal()).thenReturn(9090);

        assertNotNull(RosterUtils.toRosterEntry(address, nodeId));
    }

    @Test
    void testToRosterEntryWithCertificateEncodingException() throws CertificateEncodingException {
        when(address.getSigCert()).thenReturn(certificate);
        when(certificate.getEncoded()).thenThrow(new CertificateEncodingException());

        final RosterEntry entry = RosterUtils.toRosterEntry(address, nodeId);
        assertEquals(Bytes.EMPTY, entry.gossipCaCertificate());
    }
}
