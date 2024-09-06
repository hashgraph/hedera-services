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

package com.swirlds.platform.state.address;

import static com.swirlds.platform.util.BootstrapUtils.detectSoftwareUpgrade;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformStateAccessor;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Utility class for address book operations.
 */
public class AddressBookUtils {
    /**
     * Initializes the address book from the configuration and platform saved state.
     * @param selfId the node ID of the current node
     * @param version the software version of the current node
     * @param initialState the initial state of the platform
     * @param bootstrapAddressBook the bootstrap address book
     * @param platformContext the platform context
     * @return the initialized address book
     */
    public static @NonNull AddressBook initializeAddressBook(
            final NodeId selfId,
            final SoftwareVersion version,
            final ReservedSignedState initialState,
            final AddressBook bootstrapAddressBook,
            final PlatformContext platformContext) {
        final boolean softwareUpgrade = detectSoftwareUpgrade(version, initialState.get());
        // Initialize the address book from the configuration and platform saved state.
        final AddressBookInitializer addressBookInitializer = new AddressBookInitializer(
                selfId, version, softwareUpgrade, initialState.get(), bootstrapAddressBook.copy(), platformContext);

        if (addressBookInitializer.hasAddressBookChanged()) {
            final MerkleRoot state = initialState.get().getState();
            // Update the address book with the current address book read from config.txt.
            // Eventually we will not do this, and only transactions will be capable of
            // modifying the address book.
            final PlatformStateAccessor platformState = state.getPlatformState();
            platformState.bulkUpdate(v -> {
                v.setAddressBook(addressBookInitializer.getCurrentAddressBook().copy());
                v.setPreviousAddressBook(
                        addressBookInitializer.getPreviousAddressBook() == null
                                ? null
                                : addressBookInitializer
                                        .getPreviousAddressBook()
                                        .copy());
            });
        }

        // At this point the initial state must have the current address book set.  If not, something is wrong.
        final AddressBook addressBook =
                initialState.get().getState().getPlatformState().getAddressBook();
        if (addressBook == null) {
            throw new IllegalStateException("The current address book of the initial state is null.");
        }
        return addressBook;
    }
}
