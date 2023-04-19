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

import static com.swirlds.common.system.address.AddressBookUtils.parseAddressBookConfigText;
import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("AddressBook Tests")
class AddressBookTests {

    /**
     * Make sure that an address book is internally self consistent.
     */
    private void validateAddressBookConsistency(final AddressBook addressBook) {

        final int size = addressBook.getSize();

        long totalWeight = 0;
        int numberWithWeight = 0;
        final Set<Long> nodeIds = new HashSet<>();

        long previousId = -1;
        int expectedIndex = 0;
        for (final Address address : addressBook) {
            assertTrue(address.getId() > previousId, "iteration is not in proper order");
            previousId = address.getId();

            assertEquals(expectedIndex, addressBook.getIndex(address.getId()), "invalid index");
            assertEquals(address.getId(), addressBook.getId(expectedIndex), "wrong ID returned for index");
            expectedIndex++;

            assertEquals(address.getId(), addressBook.getId(address.getNickname()), "wrong ID returned for public key");

            assertSame(address, addressBook.getAddress(address.getId()), "invalid address returned");

            nodeIds.add(address.getId());
            totalWeight += address.getWeight();
            if (address.getWeight() != 0) {
                numberWithWeight++;
            }
        }

        assertEquals(size, nodeIds.size(), "size metric is incorrect");
        assertEquals(totalWeight, addressBook.getTotalWeight(), "incorrect total weight");
        assertEquals(numberWithWeight, addressBook.getNumberWithWeight(), "incorrect number with weight");

        if (!addressBook.isEmpty()) {
            final Address lastAddress = addressBook.getAddress(addressBook.getId(addressBook.getSize() - 1));
            assertTrue(lastAddress.getId() < addressBook.getNextNodeId(), "incorrect next node ID");
        } else {
            assertEquals(0, size, "address book expected to be empty");
        }

        // Check size using an alternate strategy
        int alternateSize = 0;
        for (int i = 0; i < addressBook.getNextNodeId(); i++) {
            if (addressBook.contains(i)) {
                alternateSize++;
            }
        }
        assertEquals(size, alternateSize, "size is incorrect");
    }

    /**
     * Validate that the address book contains the expected values.
     */
    private void validateAddressBookContents(
            final AddressBook addressBook, final Map<Long, Address> expectedAddresses) {

        assertEquals(expectedAddresses.size(), addressBook.getSize(), "unexpected number of addresses");

        for (final Long nodeId : expectedAddresses.keySet()) {
            assertTrue(addressBook.contains(nodeId), "address book does not have address for node");
            assertEquals(expectedAddresses.get(nodeId), addressBook.getAddress(nodeId), "address should match");
        }
    }

    @Test
    @DisplayName("Address Book Update Weight Test")
    void validateAddressBookUpdateWeightTest() {
        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator(getRandomPrintSeed()).setSize(10);
        final AddressBook addressBook = generator.build();
        final Address address = addressBook.getAddress(addressBook.getId(0));
        final long totalWeight = addressBook.getTotalWeight();
        final long newWeight = address.getWeight() + 1;

        addressBook.updateWeight(address.getId(), newWeight);

        final Address updatedAddress = addressBook.getAddress(addressBook.getId(0));
        assertEquals(newWeight, updatedAddress.getWeight(), "weight should be updated");
        assertEquals(totalWeight + 1, addressBook.getTotalWeight(), "total weight should be updated by 1");
        final Address reverted = updatedAddress.copySetWeight(newWeight - 1);
        assertEquals(address, reverted, "reverted address should be equal to original");
        assertThrows(
                IllegalArgumentException.class,
                () -> addressBook.updateWeight(address.getId(), -1),
                "should not be able to set negative weight");
        assertThrows(
                NoSuchElementException.class,
                () -> addressBook.updateWeight(addressBook.getNextNodeId(), 1),
                "should not be able to set weight for non-existent node");
    }

    @Test
    @DisplayName("Copy Mutable Test")
    void copyMutableTest() {
        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator(getRandomPrintSeed()).setSize(100);
        final AddressBook original = generator.build();

        validateAddressBookConsistency(original);
        assertTrue(original.isMutable(), "should be mutable");

        final AddressBook copy = original.copy();
        validateAddressBookConsistency(copy);

        assertEquals(original, copy, "copy should be equal");
        assertTrue(original.isMutable(), "original should be mutable");
        assertTrue(copy.isMutable(), "copy should be mutable");

        CryptographyHolder.get().digestSync(original);
        CryptographyHolder.get().digestSync(copy);
        final Hash originalHash = original.getHash();
        final Hash copyHash = copy.getHash();

        // Make sure that basic operations on the copy have no effect on the original

        // remove
        copy.remove(copy.getId(0));
        // update
        copy.add(copy.getAddress(copy.getId(50)).copySetNickname("foobar"));
        // insert
        copy.add(generator.buildNextAddress());

        original.invalidateHash();
        copy.invalidateHash();

        CryptographyHolder.get().digestSync(original);
        CryptographyHolder.get().digestSync(copy);

        assertEquals(originalHash, original.getHash(), "original should be unchanged");
        assertNotEquals(copyHash, copy.getHash(), "copy should be changed");
    }

    @Test
    @DisplayName("Copy Immutable Test")
    void copyImmutableTest() {
        final AddressBook original = new RandomAddressBookGenerator(getRandomPrintSeed())
                .setSize(100)
                .build();

        validateAddressBookConsistency(original);
        original.seal();
        assertTrue(original.isImmutable(), "should be immutable");

        final AddressBook copy = original.copy();
        validateAddressBookConsistency(copy);

        assertEquals(original, copy, "copy should be equal");
        assertTrue(copy.isMutable(), "copy should be mutable");
    }

    @Test
    @DisplayName("Mutability Test")
    void mutabilityTest() {
        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator(getRandomPrintSeed()).setSize(100);

        final AddressBook addressBook = generator.build();
        addressBook.seal();

        assertThrows(
                MutabilityException.class,
                () -> addressBook.add(generator.buildNextAddress()),
                "address book should be immutable");
        assertThrows(
                MutabilityException.class,
                () -> addressBook.remove(addressBook.getId(0)),
                "address book should be immutable");
        assertThrows(MutabilityException.class, addressBook::clear, "address book should be immutable");
    }

    @Test
    @DisplayName("Add/Remove Test")
    void addRemoveTest() {
        final Random random = getRandomPrintSeed();

        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator(random)
                .setMinimumWeight(0)
                .setAverageWeight(100)
                .setWeightStandardDeviation(50)
                .setSize(100);

        final AddressBook addressBook = generator.build();
        final Map<Long, Address> expectedAddresses = new HashMap<>();
        addressBook.iterator().forEachRemaining(address -> expectedAddresses.put(address.getId(), address));

        final int operationCount = 1_000;
        for (int i = 0; i < operationCount; i++) {
            if (random.nextBoolean() && addressBook.getSize() > 0) {
                final int indexToRemove = random.nextInt(addressBook.getSize());
                final long nodeIdToRemove = addressBook.getId(indexToRemove);
                assertNotNull(expectedAddresses.remove(nodeIdToRemove), "item to be removed should be present");
                addressBook.remove(nodeIdToRemove);
            } else {
                final Address newAddress = generator.buildNextAddress();
                expectedAddresses.put(newAddress.getId(), newAddress);
                addressBook.add(newAddress);
            }

            validateAddressBookConsistency(addressBook);
            validateAddressBookContents(addressBook, expectedAddresses);
        }
    }

    @Test
    @DisplayName("Update Test")
    void updateTest() {
        final Random random = getRandomPrintSeed();

        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator(random).setSize(100);

        final AddressBook addressBook = generator.build();
        final Map<Long, Address> expectedAddresses = new HashMap<>();
        addressBook.iterator().forEachRemaining(address -> expectedAddresses.put(address.getId(), address));

        final int operationCount = 1_000;
        for (int i = 0; i < operationCount; i++) {
            final int indexToUpdate = random.nextInt(addressBook.getSize());
            final long nodeIdToUpdate = addressBook.getId(indexToUpdate);

            final Address updatedAddress = generator.buildNextAddress().copySetId(nodeIdToUpdate);

            expectedAddresses.put(nodeIdToUpdate, updatedAddress);
            addressBook.add(updatedAddress);

            validateAddressBookConsistency(addressBook);
            validateAddressBookContents(addressBook, expectedAddresses);
        }
    }

    @Test
    @DisplayName("Get/Set Round Test")
    void getSetRoundTest() {
        final AddressBook addressBook = new RandomAddressBookGenerator().build();

        addressBook.setRound(1234);
        assertEquals(1234, addressBook.getRound(), "unexpected round");
    }

    @Test
    @DisplayName("Equality Test")
    void equalityTest() {
        final Random random = getRandomPrintSeed();
        final long seed = random.nextLong();

        final AddressBook addressBook1 =
                new RandomAddressBookGenerator(seed).setSize(100).build();
        final AddressBook addressBook2 =
                new RandomAddressBookGenerator(seed).setSize(100).build();

        assertEquals(addressBook1, addressBook2, "address books should be the same");
        assertEquals(addressBook1.hashCode(), addressBook2.hashCode(), "address books should have the same hash code");

        final Address updatedAddress =
                addressBook1.getAddress(addressBook1.getId(random.nextInt(100))).copySetNickname("foobar");
        addressBook1.add(updatedAddress);

        assertNotEquals(addressBook1, addressBook2, "address books should not be the same");
    }

    @Test
    @DisplayName("toString() Test")
    void atoStringSanityTest() {
        final AddressBook addressBook = new RandomAddressBookGenerator(getRandomPrintSeed()).build();

        // Basic sanity check, make sure this doesn't throw an exception
        System.out.println(addressBook);
    }

    @Test
    @DisplayName("Serialization Test")
    @Tag(TIME_CONSUMING)
    void serializationTest() throws IOException, ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");

        final AddressBook original = new RandomAddressBookGenerator(getRandomPrintSeed())
                .setSize(100)
                .build();

        validateAddressBookConsistency(original);

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);

        out.writeSerializable(original, true);

        final SerializableDataInputStream in =
                new SerializableDataInputStream(new ByteArrayInputStream(byteOut.toByteArray()));

        final AddressBook deserialized = in.readSerializable();
        validateAddressBookConsistency(deserialized);

        assertEquals(original, deserialized);
    }

    @Test
    @DisplayName("clear() test")
    void clearTest() {
        final AddressBook addressBook = new RandomAddressBookGenerator(getRandomPrintSeed())
                .setSize(100)
                .setMinimumWeight(0)
                .setMaximumWeight(10)
                .setAverageWeight(5)
                .setWeightStandardDeviation(5)
                .build();

        validateAddressBookConsistency(addressBook);

        addressBook.clear();

        assertEquals(0, addressBook.getTotalWeight(), "there should be no weight");
        assertEquals(0, addressBook.getNumberWithWeight(), "there should be no nodes with any weight");
        assertEquals(0, addressBook.getSize());

        validateAddressBookConsistency(addressBook);
    }

    @Test
    @DisplayName("Reinsertion Test")
    void reinsertionTest() {
        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator(getRandomPrintSeed());
        final AddressBook addressBook = new AddressBook();

        for (int i = 0; i < 100; i++) {
            addressBook.add(generator.buildNextAddress());
        }

        validateAddressBookConsistency(addressBook);

        final Address addressToRemove = addressBook.getAddress(addressBook.getId(50));
        addressBook.remove(addressBook.getId(50));
        assertThrows(
                IllegalStateException.class,
                () -> addressBook.add(addressToRemove),
                "should not be able to insert an address once it has been removed");
    }

    @Test
    @DisplayName("Out Of Order add() Test")
    void outOfOrderAddTest() {
        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator(getRandomPrintSeed()).setSize(100);
        final AddressBook addressBook = new AddressBook();

        // The address book has gaps. Make sure we can't insert anything into those gaps.
        for (int i = 0; i < addressBook.getNextNodeId(); i++) {

            final Address address = generator.buildNextAddress().copySetId(i);

            if (addressBook.contains(i)) {
                // It's ok to update an existing address
                addressBook.add(address);
            } else {
                // We can't add something into a gap
                assertThrows(
                        IllegalStateException.class,
                        () -> addressBook.add(address),
                        "shouldn't be able to add this address");
            }
        }

        validateAddressBookConsistency(addressBook);
    }

    @Test
    @DisplayName("Max Size Test")
    void maxSizeTest() {
        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator(getRandomPrintSeed());
        final AddressBook addressBook = new AddressBook();

        for (int i = 0; i < AddressBook.MAX_ADDRESSES; i++) {
            addressBook.add(generator.buildNextAddress());
        }

        validateAddressBookConsistency(addressBook);

        assertThrows(
                IllegalStateException.class,
                () -> addressBook.add(generator.buildNextAddress()),
                "shouldn't be able to exceed max address book size");
    }

    @Test
    @DisplayName("setNextNodeId() Test")
    void setNextNodeIdTest() {
        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator(getRandomPrintSeed());
        final AddressBook addressBook = generator.build();

        final long nextId = addressBook.getNextNodeId();
        addressBook.setNextNodeId(nextId + 10);

        assertEquals(nextId + 10, addressBook.getNextNodeId(), "node ID should have been updated");

        assertThrows(
                IllegalArgumentException.class,
                () -> addressBook.setNextNodeId(0),
                "node ID shouldn't be able to be set to a value less than an existing address book");
    }

    @Test
    @DisplayName("Roundtrip address book serialization and deserialization compatible with config.txt")
    void roundTripSerializeAndDeserializeCompatibleWithConfigTxt() throws ParseException {
        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator(getRandomPrintSeed());
        final AddressBook addressBook = generator.build();
        final String addressBookText = addressBook.toConfigText();
        final Map<Long, Long> posToId = new HashMap<>();
        long pos = 0;
        for (Address address : addressBook) {
            posToId.put(pos, address.getId());
            pos++;
        }
        final AddressBook parsedAddressBook =
                parseAddressBookConfigText(addressBookText, posToId::get, ip -> false, id -> "");
        // Equality done on toConfigText() strings since the randomly generated address book has public key data.
        assertEquals(addressBookText, parsedAddressBook.toConfigText(), "The AddressBooks are not equal.");
    }

    @Test
    @DisplayName("Testing exceptions in parsing address books used in config.txt")
    void parseExceptionTestsInParsingAddressBookConfigText() {
        // not enough parts to make an address line
        validateParseException("address", 1);
        validateParseException("address, nickname", 2);
        validateParseException("address, nickname, selfname", 3);
        validateParseException("address, nickname, selfname, 10", 4);
        validateParseException("address, nickname, selfname, 10, 192.168.0.1", 5);
        validateParseException("address, nickname, selfname, 10, 192.168.0.1, 5000", 6);
        validateParseException("address, nickname, selfname, 10, 192.168.0.1, 5000, 8.8.8.8", 7);

        // Too many parts
        validateParseException("address, nickname, selfname, 10, 192.168.0.1, 5000, 8.8.8.8, 5000, extra", 9);

        // bad parsing of parts.
        validateParseException("not an address, nickname, selfname, 10, 192.168.0.1, 5000, 8.8.8.8, 5000", 0);
        validateParseException("address, nickname, selfname, not a weight, 192.168.0.1, 5000, 8.8.8.8, 5000", 3);
        validateParseException("address, nickname, selfname, 10, 192.168.0.1, not a port, 8.8.8.8, 5000", 5);
        validateParseException("address, nickname, selfname, 10, 192.168.0.1, 5000, 8.8.8.8, not a port", 7);
    }

    private void validateParseException(String addressBook, int part) {
        assertThrows(
                ParseException.class, () -> parseAddressBookConfigText(addressBook, pos -> pos, ip -> false, id -> ""));
        try {
            parseAddressBookConfigText(addressBook, pos -> pos, ip -> false, id -> "");
        } catch (ParseException e) {
            assertEquals(part, e.getErrorOffset(), "The part number is wrong in the exception: " + e.getMessage());
        }
    }
}
