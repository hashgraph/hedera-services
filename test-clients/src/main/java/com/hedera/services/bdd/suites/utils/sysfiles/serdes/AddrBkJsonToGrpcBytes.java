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
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.suites.utils.sysfiles.AddressBookPojo;
import com.hedera.services.bdd.suites.utils.sysfiles.BookEntryPojo;
import com.hederahashgraph.api.proto.java.NodeAddressBook;

import java.io.IOException;

import static com.hedera.services.bdd.suites.utils.sysfiles.AddressBookPojo.addressBookFrom;

public class AddrBkJsonToGrpcBytes implements SysFileSerde<String> {
	private final ObjectMapper mapper = new ObjectMapper();

	@Override
	public String fromRawFile(byte[] bytes) {
		try {
			var pojoBook = addressBookFrom(NodeAddressBook.parseFrom(bytes));
			return mapper
					.writerWithDefaultPrettyPrinter()
					.writeValueAsString(pojoBook);
		} catch (InvalidProtocolBufferException | JsonProcessingException e) {
			throw new IllegalArgumentException("Not an address book!", e);
		}
	}

	@Override
	public byte[] toRawFile(String styledFile) {
		return grpcBytesFromPojo(pojoFrom(styledFile));
	}

	@Override
	public byte[] toValidatedRawFile(String styledFile) {
		var pojo = pojoFrom(styledFile);
		for (var entry : pojo.getEntries()) {
			try {
				BookEntryPojo.asOctets(entry.getDeprecatedIp());
			} catch (Exception e) {
				throw new IllegalStateException(
						"Deprecated IP field cannot be set to '" + entry.getDeprecatedIp() + "'", e);
			}

			try {
				HapiPropertySource.asAccount(entry.getDeprecatedMemo());
			} catch (Exception e) {
				throw new IllegalStateException(
						"Deprecated memo field cannot be set to '" + entry.getDeprecatedMemo() + "'", e);
			}

			if (entry.getDeprecatedPortNo() <= 0) {
				throw new IllegalStateException(
						"Deprecated portno field cannot be set to '" + entry.getDeprecatedPortNo() + "'");
			}
		}
		return grpcBytesFromPojo(pojo);
	}

	private AddressBookPojo pojoFrom(String styledFile) {
		try {
			return mapper.readValue(styledFile, AddressBookPojo.class);
		} catch (IOException ex) {
			throw new IllegalArgumentException("Not an address book!", ex);
		}
	}

	private byte[] grpcBytesFromPojo(AddressBookPojo pojoBook) {
		NodeAddressBook.Builder addressBookForClients = NodeAddressBook.newBuilder();
		pojoBook.getEntries().stream()
				.flatMap(BookEntryPojo::toGrpcStream)
				.forEach(addressBookForClients::addNodeAddress);
		return addressBookForClients.build().toByteArray();
	}

	@Override
	public String preferredFileName() {
		return "addressBook.json";
	}
}
