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

import static com.swirlds.platform.system.address.AddressBookUtils.endpointFor;
import static com.swirlds.platform.util.BootstrapUtils.detectSoftwareUpgrade;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.util.PbjRecordHasher;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A utility class to help use Rooster and RosterEntry instances.
 */
public final class RosterUtils {

    private static final PbjRecordHasher PBJ_RECORD_HASHER = new PbjRecordHasher();

    /**
     * Prevents instantiation of this utility class.
     */
    private RosterUtils() {}

    /**
     * Formats a "node name" for a given node id, e.g. "node1" for nodeId == 0.
     * This name can be used for logging purposes, or to support code that
     * uses strings to identify nodes.
     *
     * @param nodeId a node id
     * @return a "node name"
     */
    @NonNull
    public static String formatNodeName(final long nodeId) {
        return "node" + (nodeId + 1);
    }

    /**
     * Create a Hash object for a given Roster instance.
     *
     * @param roster a roster
     * @return its Hash
     */
    @NonNull
    public static Hash hashOf(@NonNull final Roster roster) {
        return PBJ_RECORD_HASHER.hash(roster, Roster.PROTOBUF);
    }

    /**
     * Constructs the initial roster based on the given software version and initial state.
     * The initial roster is constructed by adopting the candidate roster if a software upgrade is detected.
     * Otherwise, the initial roster is created from the address book.
     *
     * @param version      the software version
     * @param initialState the initial state
     * @param addressBook  the address book
     * @return the initial roster
     */
    public static @NonNull Roster constructInitialRoster(
            @NonNull final SoftwareVersion version,
            @NonNull final ReservedSignedState initialState,
            @NonNull final AddressBook addressBook) {
        final boolean softwareUpgrade = detectSoftwareUpgrade(version, initialState.get());
        final MerkleRoot merkleRoot = initialState.get().getState();
        final WritableRosterStore rosterStore = merkleRoot.getWritableRosterStore();
        final Roster candidateRoster = rosterStore.getCandidateRoster();

        return constructRoster(
                rosterStore,
                softwareUpgrade,
                candidateRoster,
                addressBook,
                initialState.get().getRound());
    }

    /**
     * Creates a new roster from the bootstrap address book.
     * @param addressBook the address book
     * @return a new roster
     */
    @NonNull
    public static Roster createRoster(@NonNull final AddressBook addressBook) {
        Objects.requireNonNull(addressBook, "The AddressBook must not be null.");
        final List<RosterEntry> rosterEntries = new ArrayList<>(addressBook.getSize());
        for (int i = 0; i < addressBook.getSize(); i++) {
            final NodeId nodeId = addressBook.getNodeId(i);
            final Address address = addressBook.getAddress(nodeId);

            final RosterEntry rosterEntry = RosterUtils.toRosterEntry(address, nodeId);
            rosterEntries.add(rosterEntry);
        }
        return Roster.newBuilder().rosterEntries(rosterEntries).build();
    }

    /**
     * Converts an address to a roster entry.
     *
     * @param address the address to convert
     * @param nodeId  the node ID to use for the roster entry
     * @return the roster entry
     */
    static RosterEntry toRosterEntry(@NonNull final Address address, @NonNull final NodeId nodeId) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(nodeId);
        final var signingCertificate = address.getSigCert();
        Bytes signingCertificateBytes;
        try {
            signingCertificateBytes =
                    signingCertificate == null ? Bytes.EMPTY : Bytes.wrap(signingCertificate.getEncoded());
        } catch (final CertificateEncodingException e) {
            signingCertificateBytes = Bytes.EMPTY;
        }

        final List<ServiceEndpoint> serviceEndpoints = new ArrayList<>(2);
        if (address.getHostnameInternal() != null) {
            serviceEndpoints.add(endpointFor(address.getHostnameInternal(), address.getPortInternal()));
        }
        if (address.getHostnameExternal() != null) {
            serviceEndpoints.add(endpointFor(address.getHostnameExternal(), address.getPortExternal()));
        }

        return RosterEntry.newBuilder()
                .nodeId(nodeId.id())
                .weight(address.getWeight())
                .gossipCaCertificate(signingCertificateBytes)
                .gossipEndpoint(serviceEndpoints)
                .build();
    }

    /**
     * Constructs a roster based on the given software upgrade flag and candidate roster.
     *
     * @param rosterStore   the roster store
     * @param softwareUpgrade the software upgrade flag
     * @param candidateRoster the candidate roster
     * @param addressBook     the address book
     * @param latestConsensusRound the latest consensus round
     * @return the roster
     */
    @NonNull
    private static Roster constructRoster(
            final WritableRosterStore rosterStore,
            final boolean softwareUpgrade,
            final Roster candidateRoster,
            final AddressBook addressBook,
            final long latestConsensusRound) {
        if (!softwareUpgrade || candidateRoster == null) {
            final Roster previousActiveRoster = rosterStore.getActiveRoster();
            // not in software upgrade mode, return the previous active roster if available
            // otherwise, create a new roster from the address book
            return Objects.requireNonNullElseGet(previousActiveRoster, () -> createRoster(addressBook));
        }

        // software upgrade is detected, we adopt the candidate roster
        rosterStore.adoptCandidateRoster(latestConsensusRound + 1);
        return candidateRoster;
    }
}
