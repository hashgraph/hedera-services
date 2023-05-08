/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.network.Network;
import java.net.InetAddress;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * {@link AddressBook AddressBook} utility methods.
 */
public final class AddressBookUtils {

    /** For logging info, warn, and error. */
    private static final Logger logger = LogManager.getLogger(AddressBookUtils.class);

    private AddressBookUtils() {}

    /**
     * Get the number of addresses currently in the address book that are running on this computer. When the browser is
     * run with a config.txt file, it can launch multiple copies of the app simultaneously, each with its own TCP/IP
     * port. This method returns how many there are.
     *
     * @param addressBook the address book to check
     * @return the number of local addresses
     */
    public static int getOwnHostCount(final AddressBook addressBook) {
        int count = 0;
        for (final Address address : addressBook) {
            try {
                if (Network.isOwn(InetAddress.getByAddress(address.getAddressInternalIpv4()))) {
                    count++;
                }
            } catch (final Exception e) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Error while checking if address {} for node {} is local",
                        address.getAddressInternalIpv4(),
                        address.getId(),
                        e);
                throw new IllegalStateException("Error while checking if ip of address was local", e);
            }
        }
        return count;
    }
}
