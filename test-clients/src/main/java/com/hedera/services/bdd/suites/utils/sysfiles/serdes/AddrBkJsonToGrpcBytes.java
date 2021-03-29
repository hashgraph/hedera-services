package com.hedera.services.bdd.suites.utils.sysfiles.serdes;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.bdd.suites.utils.sysfiles.AddressBookPojo;
import com.hedera.services.bdd.suites.utils.sysfiles.BookEntryPojo;
import com.hederahashgraph.api.proto.java.AddressBook;
import com.hederahashgraph.api.proto.java.AddressBookForClients;

import java.io.IOException;

import static com.hedera.services.bdd.suites.utils.sysfiles.AddressBookPojo.addressBookForClientsFrom;
import static com.hedera.services.bdd.suites.utils.sysfiles.AddressBookPojo.addressBookFrom;

public class AddrBkJsonToGrpcBytes implements SysFileSerde<String> {
	private final ObjectMapper mapper = new ObjectMapper();

	public enum ProtoBufVersion {
		V0_12_0, V0_13_0
	}
	public enum ProtoBuf13Version {
		ADDRESS_BOOK, ADDRESS_BOOK_FOR_CLIENTS
	}

	public static ProtoBufVersion protoBufVersion = ProtoBufVersion.V0_13_0;
	public static ProtoBuf13Version protoBuf13Version = ProtoBuf13Version.ADDRESS_BOOK;

	@Override
	public String fromRawFile(byte[] bytes) {
		try {
			var pojoBook = new AddressBookPojo();
			if (protoBufVersion == ProtoBufVersion.V0_12_0) {
				pojoBook = addressBookFrom(AddressBook.parseFrom(bytes));
			} else {
				// if it is version 13, there are two cases where one can download
				// AddressBook or AddressBookForClients
				pojoBook = addressBookFromVersion13(bytes);
			}
			return mapper
					.writerWithDefaultPrettyPrinter()
					.writeValueAsString(pojoBook);
		} catch (InvalidProtocolBufferException | JsonProcessingException e) {
			throw new IllegalArgumentException("Not an address book!", e);
		}
	}

	@Override
	public byte[] toRawFile(String styledFile) {
		try {
			var pojoBook = mapper.readValue(styledFile, AddressBookPojo.class);
			if (protoBufVersion == ProtoBufVersion.V0_12_0) {
				AddressBook.Builder addressBook = AddressBook.newBuilder();
				pojoBook.getEntries().stream()
						.flatMap(BookEntryPojo::toAddressBookEntries)
						.forEach(addressBook::addNodeAddress);
				return addressBook.build().toByteArray();
			} else {
				// if it is version 13, there are two cases where one can upload
				// AddressBook or AddressBookForClients
				return buildAddressBookForVersion13(pojoBook);
			}
		} catch (IOException ex) {
			throw new IllegalArgumentException("Not an address book!", ex);
		}
	}

	private byte[] buildAddressBookForVersion13(AddressBookPojo pojoBook) {
		if (protoBuf13Version == ProtoBuf13Version.ADDRESS_BOOK_FOR_CLIENTS) {
			AddressBookForClients.Builder addressBookForClients = AddressBookForClients.newBuilder();
			pojoBook.getEntries().stream()
					.flatMap(BookEntryPojo::toAddressBookForClientEntries)
					.forEach(addressBookForClients::addNodeAddressForClients);
			return addressBookForClients.build().toByteArray();
		} else {
			AddressBook.Builder addressBook = AddressBook.newBuilder();
			pojoBook.getEntries().stream()
					.flatMap(BookEntryPojo::toAddressBookEntries)
					.forEach(addressBook::addNodeAddress);
			return addressBook.build().toByteArray();
		}
	}

	private AddressBookPojo addressBookFromVersion13(byte[] bytes) throws InvalidProtocolBufferException {
		if (protoBuf13Version == ProtoBuf13Version.ADDRESS_BOOK_FOR_CLIENTS) {
			return addressBookFrom(AddressBookForClients.parseFrom(bytes));
		} else {
			return addressBookForClientsFrom(AddressBook.parseFrom(bytes));
		}
	}

	@Override
	public String preferredFileName() {
		return "addressBook.json";
	}

	public static void setAppropriateVersion(ProtoBufVersion version, ProtoBuf13Version version13Type) {
		protoBufVersion = version;
		protoBuf13Version = version13Type;
	}
}
