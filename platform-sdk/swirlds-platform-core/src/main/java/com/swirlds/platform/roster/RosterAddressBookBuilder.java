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

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A utility to build Address and AddressBook objects out of RosterEntry and Roster objects.
 */
public class RosterAddressBookBuilder {

    /**
     * Build an Address object out of a given RosterEntry object.
     * @param entry a RosterEntry
     * @return an Address
     */
    @NonNull
    public static Address buildAddress(@NonNull final RosterEntry entry) {
        Address address = new Address();

        address = address.copySetNodeId(NodeId.of(entry.nodeId()));
        address = address.copySetWeight(entry.weight());
        address = address.copySetSigCert(
                CryptoStatic.decodeCertificate(entry.gossipCaCertificate().toByteArray()));

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
     * @param roster a Roster
     * @return an AddressBook
     */
    @NonNull
    public static AddressBook buildAddressBook(@NonNull final Roster roster) {
        AddressBook addressBook = new AddressBook();

        for (final RosterEntry entry : roster.rosterEntries()) {
            addressBook = addressBook.add(buildAddress(entry));
        }

        return addressBook;
    }
}
