/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.address;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.swirlds.platform.network.Network;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * {@link AddressBook AddressBook} utility methods.
 */
public final class AddressBookNetworkUtils {

    private AddressBookNetworkUtils() {}

    /**
     * Check if the address is local to the machine.
     *
     * @param rosterEntry the address to check
     * @return true if the address is local to the machine, false otherwise
     * @throws IllegalStateException if the locality of the address cannot be determined.
     */
    public static boolean isLocal(@NonNull final RosterEntry rosterEntry) {
        Objects.requireNonNull(rosterEntry, "The address must not be null.");
        return IntStream.range(0, rosterEntry.gossipEndpoint().size()).anyMatch(i -> {
            try {
                return Network.isOwn(InetAddress.getByName(RosterUtils.fetchHostname(rosterEntry, i)));
            } catch (final UnknownHostException e) {
                throw new IllegalStateException(
                        "Not able to determine locality of address [%s] for node [%d]"
                                .formatted(RosterUtils.fetchHostname(rosterEntry, i), rosterEntry.nodeId()),
                        e);
            }
        });
    }

    /**
     * Check if the address is local to the machine.
     *
     * @param address the address to check
     * @return true if the address is local to the machine, false otherwise
     * @throws IllegalStateException if the locality of the address cannot be determined.
     */
    public static boolean isLocal(@NonNull final Address address) {
        Objects.requireNonNull(address, "The address must not be null.");
        try {
            return Network.isOwn(InetAddress.getByName(address.getHostnameInternal()));
        } catch (final UnknownHostException e) {
            throw new IllegalStateException(
                    "Not able to determine locality of address [%s] for node [%s]"
                            .formatted(address.getHostnameInternal(), address.getNodeId()),
                    e);
        }
    }

    /**
     * Get the number of addresses currently in the address book that are running on this computer. When the browser is
     * run with a config.txt file, it can launch multiple copies of the app simultaneously, each with its own TCP/IP
     * port. This method returns how many there are.
     *
     * @param roster the address book to check
     * @return the number of local addresses
     */
    public static int getLocalAddressCount(@NonNull final Roster roster) {
        Objects.requireNonNull(roster, "The roster must not be null.");
        int count = 0;
        for (final RosterEntry rosterEntry : roster.rosterEntries()) {
            if (isLocal(rosterEntry)) {
                count++;
            }
        }
        return count;
    }
}
