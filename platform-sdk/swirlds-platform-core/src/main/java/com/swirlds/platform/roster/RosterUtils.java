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
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.util.PbjRecordHasher;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A utility class to help use Rooster and RosterEntry instances.
 */
public final class RosterUtils {
    private static final PbjRecordHasher PBJ_RECORD_HASHER = new PbjRecordHasher();

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
     * Fetch a hostname (or a string with an IPv4 address) of the first ServiceEndpoint
     * in a given RosterEntry.
     *
     * @param entry a RosterEntry
     * @return a string with a hostname or ip address
     */
    public static String fetchHostname(@NonNull final RosterEntry entry) {
        return fetchHostname(entry, 0);
    }

    /**
     * Fetch a hostname (or a string with an IPv4 address) of a ServiceEndpoint
     * at a given index in a given RosterEntry.
     *
     * @param entry a RosterEntry
     * @param index an index of the ServiceEndpoint
     * @return a string with a hostname or ip address
     */
    public static String fetchHostname(@NonNull final RosterEntry entry, final int index) {
        final ServiceEndpoint serviceEndpoint = entry.gossipEndpoint().get(index);
        final Bytes ipAddressV4 = serviceEndpoint.ipAddressV4();
        final long length = ipAddressV4.length();
        if (length == 0) {
            return serviceEndpoint.domainName();
        }
        if (length == 4) {
            return "%d.%d.%d.%d"
                    .formatted(
                            // Java expands a byte into an int, and the "sign bit" of the byte gets extended,
                            // making it possibly a negative integer for values > 0x7F. So we AND 0xFF
                            // to get rid of the extended "sign bits" to keep this an actual, positive byte.
                            ipAddressV4.getByte(0) & 0xFF,
                            ipAddressV4.getByte(1) & 0xFF,
                            ipAddressV4.getByte(2) & 0xFF,
                            ipAddressV4.getByte(3) & 0xFF);
        }
        throw new IllegalArgumentException("Invalid IP address: " + ipAddressV4 + " in RosterEntry: " + entry);
    }

    /**
     * Fetch a port number of the first ServiceEndpoint
     * in a given RosterEntry.
     *
     * @param entry a RosterEntry
     * @return a port number
     */
    public static int fetchPort(@NonNull final RosterEntry entry) {
        return fetchPort(entry, 0);
    }

    /**
     * Fetch a port number of a ServiceEndpoint
     * at a given index in a given RosterEntry.
     *
     * @param entry a RosterEntry
     * @param index an index of the ServiceEndpoint
     * @return a port number
     */
    public static int fetchPort(@NonNull final RosterEntry entry, final int index) {
        final ServiceEndpoint serviceEndpoint = entry.gossipEndpoint().get(index);
        return serviceEndpoint.port();
    }

    /**
     * Fetch the gossip certificate from a given RosterEntry.
     *
     * @param entry a RosterEntry
     * @return a gossip certificate
     */
    public static X509Certificate fetchGossipCaCertificate(@NonNull final RosterEntry entry) {
        return CryptoStatic.generateCertificate(entry.gossipCaCertificate().toByteArray());
    }

    /**
     * Create a Hash object for a given Roster instance.
     *
     * @param roster a roster
     * @return its Hash
     */
    @NonNull
    public static Hash hash(@NonNull final Roster roster) {
        return PBJ_RECORD_HASHER.hash(roster, Roster.PROTOBUF);
    }

    /**
     * Build a map from a long nodeId to a RosterEntry for a given Roster.
     *
     * @param roster a roster
     * @return Map&lt;Long, RosterEntry&gt;
     */
    public static Map<Long, RosterEntry> toMap(@NonNull final Roster roster) {
        return roster.rosterEntries().stream().collect(Collectors.toMap(RosterEntry::nodeId, Function.identity()));
    }

    /**
     * Build a map from a long nodeId to an index of the node in the roster entries list.
     * If code needs to perform this lookup only once, then use the getIndex() instead.
     *
     * @param roster a roster
     * @return Map&lt;Long, Integer&gt;
     */
    public static Map<Long, Integer> toIndicesMap(@NonNull final Roster roster) {
        return IntStream.range(0, roster.rosterEntries().size())
                .boxed()
                .collect(Collectors.toMap(i -> roster.rosterEntries().get(i).nodeId(), Function.identity()));
    }

    /**
     * Return an index of a RosterEntry with a given node id.
     * If code needs to perform this operation often, then use the toIndicesMap() instead.
     * @param roster a Roster
     * @param nodeId a node id
     * @return an index, or -1 if not found
     */
    public static int getIndex(@NonNull final Roster roster, final long nodeId) {
        for (int i = 0; i < roster.rosterEntries().size(); i++) {
            if (roster.rosterEntries().get(i).nodeId() == nodeId) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Compute the total weight of a Roster which is a sum of weights of all the RosterEntries.
     *
     * @param roster a roster
     * @return the total weight
     */
    public static long computeTotalWeight(@NonNull final Roster roster) {
        return roster.rosterEntries().stream().mapToLong(RosterEntry::weight).sum();
    }

    /**
     * Returns a RosterEntry with a given nodeId by simply iterating all entries,
     * w/o building a temporary map.
     *
     * Useful for one-off look-ups. If code needs to look up multiple entries by NodeId,
     * then the code should use the RosterUtils.toMap() method and keep the map instance
     * for the look-ups.
     *
     * @param roster a roster
     * @param nodeId a node id
     * @return a RosterEntry
     * @throws RosterEntryNotFoundException if RosterEntry is not found in Roster
     */
    public static RosterEntry getRosterEntry(@NonNull final Roster roster, final long nodeId) {
        for (final RosterEntry entry : roster.rosterEntries()) {
            if (entry.nodeId() == nodeId) {
                return entry;
            }
        }
        throw new RosterEntryNotFoundException("No RosterEntry with nodeId: " + nodeId + " in Roster: " + roster);
    }

    /**
     * Count the number of RosterEntries with non-zero weight.
     * @param roster a roster
     * @return the number of RosterEntries with non-zero weight
     */
    public static int getNumberWithWeight(@NonNull final Roster roster) {
        return (int) roster.rosterEntries().stream()
                .map(RosterEntry::weight)
                .filter(w -> w != 0)
                .count();
    }
}
