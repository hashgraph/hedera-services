/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test;

import static com.swirlds.common.system.address.AddressBookValidator.hasNonZeroWeight;
import static com.swirlds.common.system.address.AddressBookValidator.isGenesisAddressBookValid;
import static com.swirlds.common.system.address.AddressBookValidator.isNextAddressBookValid;
import static com.swirlds.common.system.address.AddressBookValidator.isNonEmpty;
import static com.swirlds.common.system.address.AddressBookValidator.noAddressReinsertion;
import static com.swirlds.common.system.address.AddressBookValidator.validNextId;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.system.address.AddressBook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AddressBookValidator Tests")
class AddressBookValidatorTests {

    @Test
    @DisplayName("hasNonZeroWeight Test")
    void hasNonZeroWeightTest() {
        final AddressBook emptyAddressBook =
                new RandomAddressBookGenerator().setSize(0).build();
        final AddressBook zeroWeightAddressBook = new RandomAddressBookGenerator()
                .setSize(10)
                .setCustomWeightGenerator(n -> 0)
                .build();
        final AddressBook validAddressBook =
                new RandomAddressBookGenerator().setSize(10).build();

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
        final AddressBook emptyAddressBook =
                new RandomAddressBookGenerator().setSize(0).build();
        final AddressBook validAddressBook =
                new RandomAddressBookGenerator().setSize(10).build();

        assertFalse(isNonEmpty(emptyAddressBook), "should fail validation");
        assertFalse(isGenesisAddressBookValid(emptyAddressBook), "should fail validation");

        assertTrue(isNonEmpty(validAddressBook), "should pass validation");
        assertTrue(isGenesisAddressBookValid(validAddressBook), "should pass validation");
    }

    @Test
    @DisplayName("validNextId Test")
    void validNextIdTest() {
        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator().setSize(10);

        final AddressBook addressBook1 = generator.build();
        final AddressBook addressBook2 = generator.addToAddressBook(addressBook1.copy());
        final AddressBook addressBook3 = generator.addToAddressBook(addressBook2.copy());

        assertTrue(validNextId(addressBook1, addressBook2), "should pass validation");
        assertTrue(isNextAddressBookValid(addressBook1, addressBook2), "should pass validation");
        assertTrue(validNextId(addressBook2, addressBook3), "should pass validation");
        assertTrue(isNextAddressBookValid(addressBook2, addressBook3), "should pass validation");

        assertFalse(validNextId(addressBook3, addressBook2), "should fail validation");
        assertFalse(isNextAddressBookValid(addressBook3, addressBook2), "should fail validation");
        assertFalse(validNextId(addressBook3, addressBook1), "should fail validation");
        assertFalse(isNextAddressBookValid(addressBook3, addressBook1), "should fail validation");
        assertFalse(validNextId(addressBook2, addressBook1), "should fail validation");
        assertFalse(isNextAddressBookValid(addressBook2, addressBook1), "should fail validation");
    }

    @Test
    @DisplayName("noAddressReinsertion Test")
    void noAddressReinsertionTest() {
        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator().setSize(10);

        final AddressBook addressBook1 = generator.build();
        final AddressBook addressBook2 = generator.build();
        final AddressBook reducedAddressBook1 = generator.removeFromAddressBook(addressBook1.copy(), 5);

        assertTrue(noAddressReinsertion(addressBook1, addressBook2), "should pass validation");
        assertTrue(isNextAddressBookValid(addressBook1, addressBook2), "should pass validation");
        assertTrue(noAddressReinsertion(addressBook1, reducedAddressBook1), "should pass validation");
        assertTrue(isNextAddressBookValid(addressBook1, reducedAddressBook1), "should pass validation");

        assertFalse(noAddressReinsertion(reducedAddressBook1, addressBook1), "should fail validation");
        assertFalse(isNextAddressBookValid(reducedAddressBook1, addressBook1), "should fail validation");
    }
}
