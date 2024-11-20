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
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.state.PlatformStateAccessor;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.util.PbjRecordHasher;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
     * Formats a "node name" for a given node id, e.g. "node1" for nodeId == 0. This name can be used for logging
     * purposes, or to support code that uses strings to identify nodes.
     *
     * @param nodeId a node id
     * @return a "node name"
     */
    @NonNull
    public static String formatNodeName(final long nodeId) {
        return "node" + (nodeId + 1);
    }

    /**
     * Fetch the gossip certificate from a given RosterEntry.  If it cannot be parsed successfully, return null.
     *
     * @param entry a RosterEntry
     * @return a gossip certificate
     */
    public static X509Certificate fetchGossipCaCertificate(@NonNull final RosterEntry entry) {
        try {
            return CryptoStatic.decodeCertificate(entry.gossipCaCertificate().toByteArray());
        } catch (final CryptographyException e) {
            return null;
        }
    }

    /**
     * Fetch a hostname (or a string with an IPv4 address) of a ServiceEndpoint at a given index in a given
     * RosterEntry.
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
     * Fetch a port number of a ServiceEndpoint at a given index in a given RosterEntry.
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
     * @return {@code Map<Long, RosterEntry>}
     */
    @Nullable
    public static Map<Long, RosterEntry> toMap(@Nullable final Roster roster) {
        if (roster == null) {
            return null;
        }
        return roster.rosterEntries().stream().collect(Collectors.toMap(RosterEntry::nodeId, Function.identity()));
    }

    /**
     * Build a map from a long nodeId to an index of the node in the roster entries list. If code needs to perform this
     * lookup only once, then use the getIndex() instead.
     *
     * @param roster a roster
     * @return {@code Map<Long, Integer>}
     */
    public static Map<Long, Integer> toIndicesMap(@NonNull final Roster roster) {
        return IntStream.range(0, roster.rosterEntries().size())
                .boxed()
                .collect(Collectors.toMap(i -> roster.rosterEntries().get(i).nodeId(), Function.identity()));
    }

    /**
     * Return an index of a RosterEntry with a given node id. If code needs to perform this operation often, then use
     * the toIndicesMap() instead.
     *
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
     * Returns a RosterEntry with a given nodeId by simply iterating all entries, w/o building a temporary map.
     * <p>
     * Useful for one-off look-ups. If code needs to look up multiple entries by NodeId, then the code should use the
     * RosterUtils.toMap() method and keep the map instance for the look-ups.
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
     * Build an instance of RosterHistory from the current/previous AddressBook found in the PlatformState.
     *
     * @param readablePlatformState
     * @return a RosterHistory
     * @deprecated To be removed once AddressBook to Roster refactoring is complete.
     */
    @Deprecated(forRemoval = true)
    @NonNull
    public static RosterHistory buildRosterHistory(final PlatformStateAccessor readablePlatformState) {
        if (readablePlatformState.getAddressBook() == null) {
            throw new IllegalStateException("Address book is null");
        }

        final List<RoundRosterPair> roundRosterPairList = new ArrayList<>();
        final Map<Bytes, Roster> rosterMap = new HashMap<>();

        final Roster currentRoster = RosterRetriever.buildRoster(readablePlatformState.getAddressBook());
        final Bytes currentHash = RosterUtils.hash(currentRoster).getBytes();
        roundRosterPairList.add(new RoundRosterPair(readablePlatformState.getRound(), currentHash));
        rosterMap.put(currentHash, currentRoster);

        if (readablePlatformState.getPreviousAddressBook() != null) {
            final Roster previousRoster = RosterRetriever.buildRoster(readablePlatformState.getPreviousAddressBook());
            final Bytes previousHash = RosterUtils.hash(previousRoster).getBytes();
            roundRosterPairList.add(new RoundRosterPair(0, previousHash));
            rosterMap.put(previousHash, previousRoster);
        }

        return new RosterHistory(roundRosterPairList, rosterMap);
    }

    /**
     * Creates the Roster History to be used by Platform.
     *
     * @param rosterStore the roster store containing the active rosters.
     * @return the roster history if roster store contains active rosters, otherwise IllegalStateException is thrown.
     */
    @NonNull
    public static RosterHistory createRosterHistory(@NonNull final ReadableRosterStore rosterStore) {
        final var roundRosterPairs = rosterStore.getRosterHistory();
        // If there exists active rosters in the roster state.
        if (roundRosterPairs != null) {
            final var rosterMap = roundRosterPairs.stream()
                    .collect(Collectors.toMap(
                            RoundRosterPair::activeRosterHash, pair -> rosterStore.get(pair.activeRosterHash())));

            return new RosterHistory(roundRosterPairs, rosterMap);
        } else {
            // If there is no roster state content, this is a fatal error: The migration did not happen on software
            // upgrade.
            throw new IllegalStateException("No active rosters found in the roster state");
        }
    }

    /**
     * Build an Address object out of a given RosterEntry object.
     *
     * @param entry a RosterEntry
     * @return an Address
     * @deprecated To be removed once AddressBook to Roster refactoring is complete.
     */
    @Deprecated(forRemoval = true)
    @NonNull
    public static Address buildAddress(@NonNull final RosterEntry entry) {
        Address address = new Address();

        address = address.copySetNodeId(NodeId.of(entry.nodeId()));
        address = address.copySetWeight(entry.weight());

        X509Certificate sigCert;
        try {
            sigCert = CryptoStatic.decodeCertificate(entry.gossipCaCertificate().toByteArray());
        } catch (final CryptographyException e) {
            // Malformed or missing gossip certificates are nullified.
            // https://github.com/hashgraph/hedera-services/issues/16648
            sigCert = null;
        }
        address = address.copySetSigCert(sigCert);

        if (entry.gossipEndpoint().size() > 0) {
            address = address.copySetHostnameExternal(RosterUtils.fetchHostname(entry, 0));
            address = address.copySetPortExternal(RosterUtils.fetchPort(entry, 0));

            if (entry.gossipEndpoint().size() > 1) {
                address = address.copySetHostnameInternal(RosterUtils.fetchHostname(entry, 1));
                address = address.copySetPortInternal(RosterUtils.fetchPort(entry, 1));
            } else {
                // There's code in the app implementation that relies on both the external and internal endpoints at
                // once.
                // That code used to fetch the AddressBook from the Platform for some reason.
                // Since Platform only knows about the Roster now, we have to support both the endpoints
                // in this reverse conversion here.
                // Ideally, the app code should manage its AddressBook on its own and should never fetch it from
                // Platform directly.
                address = address.copySetHostnameInternal(RosterUtils.fetchHostname(entry, 0));
                address = address.copySetPortInternal(RosterUtils.fetchPort(entry, 0));
            }
        }

        final String name = RosterUtils.formatNodeName(entry.nodeId());
        address = address.copySetSelfName(name).copySetNickname(name);

        return address;
    }

    /**
     * Build an AddressBook object out of a given Roster object.
     *
     * @param roster a Roster
     * @return an AddressBook
     * @deprecated To be removed once AddressBook to Roster refactoring is complete.
     */
    @Deprecated(forRemoval = true)
    @NonNull
    public static AddressBook buildAddressBook(@NonNull final Roster roster) {
        AddressBook addressBook = new AddressBook();

        for (final RosterEntry entry : roster.rosterEntries()) {
            addressBook = addressBook.add(buildAddress(entry));
        }

        return addressBook;
    }
}
