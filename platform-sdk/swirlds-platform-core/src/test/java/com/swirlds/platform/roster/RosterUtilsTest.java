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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformStateAccessor;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RosterUtilsTest {

    private final AddressBook addressBook = mock(AddressBook.class);
    private final Address address = mock(Address.class);
    private final NodeId nodeId = new NodeId(1);
    private final X509Certificate certificate = mock(X509Certificate.class);

    @Test
    void testHashOf() {
        final Hash hash = RosterUtils.hashOf(Roster.DEFAULT);
        assertEquals(
                "38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b",
                hash.toString());

        final Hash anotherHash = RosterUtils.hashOf(
                Roster.DEFAULT.copyBuilder().rosterEntries(RosterEntry.DEFAULT).build());
        assertEquals(
                "5d693ce2c5d445194faee6054b4d8fe4a4adc1225cf0afc2ecd7866ea895a0093ea3037951b75ab7340b75699aa1db1d",
                anotherHash.toString());

        final Hash validRosterHash = RosterUtils.hashOf(RosterValidatorTests.buildValidRoster());
        assertEquals(
                "1b8414aa690d96ce79e972abfc58c7ca04052996f89c5e6789b25b9051ee85fccb7c8ed3fc6ebacef177adfdcbbb5709",
                validRosterHash.toString());
    }

    @Test
    @DisplayName("Test generate active roster with software upgrade")
    void testDetermineActiveRosterWithSoftwareUpgrade() {
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

        assertSame(candidateRoster, RosterUtils.determineActiveRoster(version, initialState, addressBook));
    }

    @Test
    @DisplayName("Test generate active roster without software upgrade")
    void testDetermineActiveRosterWithoutSoftwareUpgrade() {
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
        assertEquals(
                RosterUtils.determineActiveRoster(version, initialState, addressBook),
                RosterUtils.createRoster(addressBook));
    }

    @Test
    @DisplayName("Test generate active roster without software upgrade but with an active roster present in the state")
    void testDetermineActiveRosterWithoutSoftwareUpgrade2() {
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

        assertSame(activeRoster, RosterUtils.determineActiveRoster(version, initialState, addressBook));
    }

    @Test
    void testCreateRoster() {
        when(addressBook.getSize()).thenReturn(1);
        when(addressBook.getNodeId(0)).thenReturn(nodeId);
        when(addressBook.getAddress(nodeId)).thenReturn(address);

        final Roster roster = RosterUtils.createRoster(addressBook);
        assertNotNull(roster);
        assertNotNull(roster.rosterEntries());
        assertEquals(1, roster.rosterEntries().size());
    }

    @Test
    void testCreateRosterWithNullAddressBook() {
        assertThrows(NullPointerException.class, () -> RosterUtils.createRoster(null));
    }

    @Test
    void testToRosterEntryWithCertificateEncodingException() throws CertificateEncodingException {
        when(address.getSigCert()).thenReturn(certificate);
        when(certificate.getEncoded()).thenThrow(new CertificateEncodingException());
        when(addressBook.getSize()).thenReturn(1);
        when(addressBook.getNodeId(0)).thenReturn(nodeId);
        when(addressBook.getAddress(nodeId)).thenReturn(address);

        assertEquals(
                Bytes.EMPTY,
                RosterUtils.createRoster(addressBook).rosterEntries().getFirst().gossipCaCertificate());
    }
}
