/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system.address;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Objects;
import java.util.function.Function;

/**
 * A utility class for AddressBook functionality.
 */
public class AddressBookUtils {
    private AddressBookUtils() {}

    /**
     * Parses an address from a single line of text.  The address must be in the form used in config.txt
     *
     * @param addressLine         the string to parse an Address form.
     * @param id                  the id to give to the parsed Address.
     * @param isOwnHostDeterminer a function to determine if isOwn should be true given an InetAddress.
     * @param memo                the memo text for the address.
     * @return the Address parsed from the addressLine.
     * @throws ParseException if there is any problem with creating an Address from the addressLine.
     */
    public static Address parseAddressConfigText(
            @NonNull final String addressLine,
            final long id,
            @NonNull final Function<InetAddress, Boolean> isOwnHostDeterminer,
            @NonNull final String memo)
            throws ParseException {
        Objects.requireNonNull(addressLine, "The addressLine must not be null.");
        Objects.requireNonNull(isOwnHostDeterminer, "The isOwnHostDeterminer must not be null.");
        Objects.requireNonNull(memo, "The memo must not be null.");
        final String[] parts = addressLine.trim().split(",");
        if (parts.length != 8) {
            throw new ParseException("Not enough parts in the address line to parse correctly.", parts.length);
        }
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        if (!parts[0].equals("address")) {
            throw new ParseException("The address line must start with 'address' and not '" + parts[0] + "'", 0);
        }
        final String nickname = parts[1];
        final String selfname = parts[2];
        final Long weight;
        try {
            weight = Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            throw new ParseException("Cannot parse value of weight from '" + parts[3] + "'", 3);
        }
        final InetAddress internalIp;
        try {
            internalIp = InetAddress.getByName(parts[4]);
        } catch (UnknownHostException e) {
            throw new ParseException("Cannot parse ip address from '" + parts[4] + ",", 4);
        }
        final int internalPort;
        try {
            internalPort = Integer.parseInt(parts[5]);
        } catch (NumberFormatException e) {
            throw new ParseException("Cannot parse ip port from '" + parts[5] + "'", 5);
        }
        final InetAddress externalIp;
        try {
            externalIp = InetAddress.getByName(parts[6]);
        } catch (UnknownHostException e) {
            throw new ParseException("Cannot parse ip address from '" + parts[6] + ",", 6);
        }
        final int externalPort;
        try {
            externalPort = Integer.parseInt(parts[7]);
        } catch (NumberFormatException e) {
            throw new ParseException("Cannot parse ip port from '" + parts[7] + "'", 7);
        }
        final boolean isOwnHost = isOwnHostDeterminer.apply(internalIp);

        return new Address(
                id,
                nickname,
                selfname,
                weight,
                isOwnHost,
                internalIp.getAddress(),
                internalPort,
                externalIp.getAddress(),
                externalPort,
                memo);
    }

    /**
     * Parses an address book from text in the form described by config.txt
     *
     * @param addressBookText the config.txt compatible serialized address book to parse.
     * @param posToId         a function to determine the address id given the position of the address in the text.
     * @param isOwnDeterminer a function to determine if the address isOwn property should be true given an
     *                        InetAddress.
     * @param memoSource      a function to render memo text given the address id.
     * @return a parsed AddressBook.
     * @throws ParseException if any Address throws a ParseException when being parsed.
     */
    public static AddressBook parseAddressBookConfigText(
            @NonNull final String addressBookText,
            @NonNull final Function<Long, Long> posToId,
            @NonNull final Function<InetAddress, Boolean> isOwnDeterminer,
            @NonNull final Function<Long, String> memoSource)
            throws ParseException {
        Objects.requireNonNull(addressBookText, "The addressBookText must not be null.");
        Objects.requireNonNull(posToId, "The posToId must not be null.");
        Objects.requireNonNull(isOwnDeterminer, "The isOwnDeterminer must not be null.");
        Objects.requireNonNull(memoSource, "The memoSource must not be null.");
        final AddressBook addressBook = new AddressBook();
        long pos = 0;
        for (final String addressLine : addressBookText.split("\\r?\\n")) {
            final long id = posToId.apply(pos);
            addressBook.add(parseAddressConfigText(addressLine, id, isOwnDeterminer, memoSource.apply(id)));
            pos++;
        }
        return addressBook;
    }
}
