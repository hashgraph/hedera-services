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

import static com.hedera.services.bdd.suites.utils.sysfiles.AddressBookPojo.addressBookFrom;

public class AddrBkJsonToGrpcBytes implements SysFileSerde<String> {
	private final ObjectMapper mapper = new ObjectMapper();

	public enum ProtoBufVersion {
		V0_12_0, V0_13_0
	}

	public static ProtoBufVersion protoBufVersion = ProtoBufVersion.V0_13_0;

	@Override
	public String fromRawFile(byte[] bytes) {
		try {
			var pojoBook = new AddressBookPojo();
			//if(protoBufVersion == ProtoBufVersion.V0_12_0) {
				pojoBook = addressBookFrom(AddressBook.parseFrom(bytes));
//			} else {
//				pojoBook = addressBookFrom(AddressBookForClients.parseFrom(bytes));
//			}
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
			if(protoBufVersion == ProtoBufVersion.V0_12_0) {
				AddressBook.Builder addressBook = AddressBook.newBuilder();
				pojoBook.getEntries().stream()
						.flatMap(BookEntryPojo::toAddressBookEntries)
						.forEach(addressBook::addNodeAddress);
				return addressBook.build().toByteArray();
			} else {
				AddressBookForClients.Builder addressBook = AddressBookForClients.newBuilder();
				pojoBook.getEntries().stream()
						.flatMap(BookEntryPojo::toAddressBookForClientEntries)
						.forEach(addressBook::addNodeAddressForClients);
				return addressBook.build().toByteArray();
			}

		} catch (IOException ex) {
			throw new IllegalArgumentException("Not an address book!", ex);
		}
	}

	@Override
	public String preferredFileName() {
		return "addressBook.json";
	}

	public static void setAppropriateVersion(String version) {
		if (Integer.parseInt(version) < 13) {
			protoBufVersion = ProtoBufVersion.V0_12_0;
		}
	}
}
