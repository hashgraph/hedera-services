/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system.address;

import static com.swirlds.platform.system.address.AddressBookValidator.hasNonZeroWeight;
import static com.swirlds.platform.system.address.AddressBookValidator.isGenesisAddressBookValid;
import static com.swirlds.platform.system.address.AddressBookValidator.isNonEmpty;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AddressBookValidator Tests")
class AddressBookValidatorTests {

    @Test
    @DisplayName("hasNonZeroWeight Test")
    void hasNonZeroWeightTest() {
        final Randotron randotron = Randotron.create();
        final AddressBook emptyAddressBook =
                RandomAddressBookBuilder.create(randotron).withSize(0).build();
        final AddressBook zeroWeightAddressBook = RandomAddressBookBuilder.create(randotron)
                .withSize(10)
                .withMaximumWeight(0)
                .build();
        final AddressBook validAddressBook =
                RandomAddressBookBuilder.create(randotron).withSize(10).build();

        assertFalse(hasNonZeroWeight(emptyAddressBook), "should fail validation");
        assertFalse(isGenesisAddressBookValid(emptyAddressBook), "should fail validation");
        assertFalse(hasNonZeroWeight(zeroWeightAddressBook), "should fail validation");
        assertFalse(isGenesisAddressBookValid(zeroWeightAddressBook), "should fail validation");

        assertTrue(hasNonZeroWeight(validAddressBook), "should pass validation");
        assertTrue(isGenesisAddressBookValid(validAddressBook), "should pass validation");
    }

    @Test
    @DisplayName("isNonEmpty Test")
    void isNonEmptyTest() {
        final Randotron randotron = Randotron.create();
        final AddressBook emptyAddressBook =
                RandomAddressBookBuilder.create(randotron).withSize(0).build();
        final AddressBook validAddressBook =
                RandomAddressBookBuilder.create(randotron).withSize(10).build();

        assertFalse(isNonEmpty(emptyAddressBook), "should fail validation");
        assertFalse(isGenesisAddressBookValid(emptyAddressBook), "should fail validation");

        assertTrue(isNonEmpty(validAddressBook), "should pass validation");
        assertTrue(isGenesisAddressBookValid(validAddressBook), "should pass validation");
    }

    /**
     * Remove a number of addresses from an address book.
     *
     * @param randotron   the random number generator to use
     * @param addressBook the address book to remove from
     * @param count       the number of addresses to remove, removes all addresses if count exceeds address book size
     * @return the input address book
     */
    public static AddressBook removeFromAddressBook(
            @NonNull final Randotron randotron, @NonNull final AddressBook addressBook, final int count) {
        Objects.requireNonNull(addressBook, "AddressBook must not be null");
        final List<NodeId> nodeIds = new ArrayList<>(addressBook.getSize());
        addressBook.forEach((final Address address) -> nodeIds.add(address.getNodeId()));
        Collections.shuffle(nodeIds, randotron);
        for (int i = 0; i < count && i < nodeIds.size(); i++) {
            addressBook.remove(nodeIds.get(i));
        }
        return addressBook;
    }
}
