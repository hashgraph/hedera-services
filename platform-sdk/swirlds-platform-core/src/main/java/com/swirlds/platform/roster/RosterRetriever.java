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
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.utility.Pair;
import com.swirlds.common.RosterStateId;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.ReadablePlatformStateStore;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.State;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableSingletonState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.cert.CertificateEncodingException;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class to help retrieve a Roster instance from the state.
 */
public final class RosterRetriever {
    private RosterRetriever() {}

    private static final String IP_ADDRESS_COMPONENT_REGEX = "(\\d{1,2}|(?:0|1)\\d{2}|2[0-4]\\d|25[0-5])";
    private static final Pattern IP_ADDRESS_PATTERN =
            Pattern.compile("^%N\\.%N\\.%N\\.%N$".replace("%N", IP_ADDRESS_COMPONENT_REGEX));

    /**
     * Retrieve the current active Roster from the state.
     * <p>
     * This method first checks the RosterState/RosterMap entities,
     * and if they contain an active roster, then returns it.
     * If the active roster is missing from RosterState,
     * then fall back to reading an AddressBook from the PlatformState
     * and converting it to a Roster.
     *
     * @return an active Roster for the round of the state, or a Roster that represents the current AddressBook in PlatformState
     */
    @NonNull
    public static Roster retrieve(@NonNull final State state) {
        final Bytes activeRosterHash = getActiveRosterHash(state);
        if (activeRosterHash != null) {
            final ReadableKVState<ProtoBytes, Roster> rosterMap =
                    state.getReadableStates(RosterStateId.SCHEMA_NAME).get(RosterStateId.ROSTER_KEY);
            final Roster roster = rosterMap.get(
                    ProtoBytes.newBuilder().value(activeRosterHash).build());
            if (roster != null) {
                return roster;
            }
        }

        final ReadablePlatformStateStore readablePlatformStateStore =
                new ReadablePlatformStateStore(state.getReadableStates(PlatformStateService.NAME));
        return buildRoster(readablePlatformStateStore.getAddressBook());
    }

    /**
     * Retrieve a hash of the active roster for the current round of the state,
     * or null if the roster is unknown for that round.
     * A roster may be unknown if the RosterState hasn't been populated yet,
     * or the current round of the state predates the implementation of the Roster.
     *
     * @param state a state
     * @return a Bytes object with the roster hash, or null
     */
    @Nullable
    public static Bytes getActiveRosterHash(@NonNull final State state) {
        final long round = getRound(state);
        final ReadableSingletonState<RosterState> rosterState =
                state.getReadableStates(RosterStateId.SCHEMA_NAME).getSingleton(RosterStateId.ROSTER_STATES_KEY);
        // replace with binary search when/if the list size becomes unreasonably large (100s of entries or more)
        final List<RoundRosterPair> roundRosterPairs = rosterState.get().roundRosterPairs();
        for (int i = 0; i < roundRosterPairs.size(); i++) {
            final RoundRosterPair roundRosterPair = roundRosterPairs.get(i);
            if (roundRosterPair.roundNumber() <= round) {
                return roundRosterPair.activeRosterHash();
            }
        }

        return null;
    }

    /**
     * Get the current round of the given state.
     *
     * @param state a state
     * @return the round of the state
     */
    public static long getRound(@NonNull final State state) {
        final ReadablePlatformStateStore readablePlatformStateStore =
                new ReadablePlatformStateStore(state.getReadableStates(PlatformStateService.NAME));
        return readablePlatformStateStore.getRound();
    }

    /**
     * Builds a Roster object out of a given AddressBook object.
     *
     * @param addressBook an AddressBook
     * @return a Roster
     */
    @NonNull
    public static Roster buildRoster(@NonNull final AddressBook addressBook) {
        return Roster.newBuilder()
                .rosters(addressBook.getNodeIdSet().stream()
                        .map(addressBook::getAddress)
                        .map(address -> {
                            try {
                                return RosterEntry.newBuilder()
                                        .nodeId(address.getNodeId().id())
                                        .weight(address.getWeight())
                                        .gossipCaCertificate(
                                                Bytes.wrap(address.getSigCert().getEncoded()))
                                        .gossipEndpoint(List.of(
                                                        Pair.of(
                                                                address.getHostnameExternal(),
                                                                address.getPortExternal()),
                                                        Pair.of(
                                                                address.getHostnameInternal(),
                                                                address.getPortInternal()))
                                                .stream()
                                                .filter(pair -> pair.left() != null
                                                        && !pair.left().isBlank()
                                                        && pair.right() != 0)
                                                .distinct()
                                                .map(pair -> {
                                                    final Matcher matcher = IP_ADDRESS_PATTERN.matcher(pair.left());

                                                    if (!matcher.matches()) {
                                                        return ServiceEndpoint.newBuilder()
                                                                .domainName(pair.left())
                                                                .port(pair.right())
                                                                .build();
                                                    }

                                                    try {
                                                        return ServiceEndpoint.newBuilder()
                                                                .ipAddressV4(Bytes.wrap(new byte[] {
                                                                    (byte) Integer.parseInt(matcher.group(1)),
                                                                    (byte) Integer.parseInt(matcher.group(2)),
                                                                    (byte) Integer.parseInt(matcher.group(3)),
                                                                    (byte) Integer.parseInt(matcher.group(4)),
                                                                }))
                                                                .port(pair.right())
                                                                .build();
                                                    } catch (NumberFormatException e) {
                                                        throw new InvalidAddressBookException(e);
                                                    }
                                                })
                                                .toList())
                                        .build();
                            } catch (CertificateEncodingException e) {
                                throw new InvalidAddressBookException(e);
                            }
                        })
                        .sorted(Comparator.comparing(RosterEntry::nodeId))
                        .toList())
                .build();
    }
}
