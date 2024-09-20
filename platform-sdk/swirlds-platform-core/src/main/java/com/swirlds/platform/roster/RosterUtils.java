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

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.util.PbjRecordHasher;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A utility class to help use Roster and RosterEntry instances.
 */
public final class RosterUtils {
    private static final PbjRecordHasher PBJ_RECORD_HASHER = new PbjRecordHasher();
    private static final Pattern IPV4_ADDRESS_PATTERN =
            Pattern.compile("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$");

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
     * Creates a new roster from the given address book.
     *
     * @param addressBook the address book
     * @return a new roster
     */
    @NonNull
    public static Roster createRoster(@NonNull final AddressBook addressBook) {
        Objects.requireNonNull(addressBook, "The addressBook must not be null.");
        final List<RosterEntry> rosterEntries = new ArrayList<>(addressBook.getSize());
        addressBook.iterator().forEachRemaining(address -> rosterEntries.add(createRosterEntry(address)));
        return Roster.newBuilder().rosterEntries(rosterEntries).build();
    }

    /**
     * Given a host and port, creates a {@link ServiceEndpoint} object with either an IP address or domain name
     * depending on the given host.
     *
     * @param host the host
     * @param port the port
     * @return the {@link ServiceEndpoint} object
     */
    @NonNull
    private static ServiceEndpoint endpointFor(@NonNull final String host, final int port) {
        final var builder = ServiceEndpoint.newBuilder().port(port);
        if (IPV4_ADDRESS_PATTERN.matcher(host).matches()) {
            final var octets = host.split("[.]");
            builder.ipAddressV4(Bytes.wrap(new byte[] {
                (byte) Integer.parseInt(octets[0]),
                (byte) Integer.parseInt(octets[1]),
                (byte) Integer.parseInt(octets[2]),
                (byte) Integer.parseInt(octets[3])
            }));
        } else {
            builder.domainName(host);
        }
        return builder.build();
    }

    /**
     * Converts an address to a roster entry.
     *
     * @param address the address to convert
     * @return the roster entry
     */
    @NonNull
    private static RosterEntry createRosterEntry(@NonNull final Address address) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(address.getNodeId());
        final var signingCertificate = address.getSigCert();
        final Bytes signingCertificateBytes;
        try {
            signingCertificateBytes =
                    signingCertificate == null ? Bytes.EMPTY : Bytes.wrap(signingCertificate.getEncoded());
        } catch (final CertificateEncodingException e) {
            throw new InvalidAddressBookException(e);
        }

        final List<ServiceEndpoint> serviceEndpoints = new ArrayList<>(2);
        if (address.getHostnameInternal() != null) {
            serviceEndpoints.add(endpointFor(address.getHostnameInternal(), address.getPortInternal()));
        }
        if (address.getHostnameExternal() != null) {
            serviceEndpoints.add(endpointFor(address.getHostnameExternal(), address.getPortExternal()));
        }

        return RosterEntry.newBuilder()
                .nodeId(address.getNodeId().id())
                .weight(address.getWeight())
                .gossipCaCertificate(signingCertificateBytes)
                .gossipEndpoint(serviceEndpoints)
                .build();
    }
}
