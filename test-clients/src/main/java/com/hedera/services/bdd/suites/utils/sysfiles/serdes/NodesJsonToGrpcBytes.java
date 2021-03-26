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
import org.apache.commons.lang3.NotImplementedException;

import java.io.IOException;

import static com.hedera.services.bdd.suites.utils.sysfiles.AddressBookPojo.nodeDetailsFrom;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.AddrBkJsonToGrpcBytes.protoBufVersion;

public class NodesJsonToGrpcBytes implements SysFileSerde<String> {
	private final ObjectMapper mapper = new ObjectMapper();

	@Override
	public String fromRawFile(byte[] bytes) {
		try {
			var pojoBook = nodeDetailsFrom(AddressBook.parseFrom(bytes));
			return mapper
					.writerWithDefaultPrettyPrinter()
					.writeValueAsString(pojoBook);
		} catch (InvalidProtocolBufferException | JsonProcessingException e) {
			throw new IllegalArgumentException("Not a node details file!", e);
		}
	}

	@Override
	public byte[] toRawFile(String styledFile) {
		try {
			var pojoBook = mapper.readValue(styledFile, AddressBookPojo.class);
			AddressBook.Builder addressBook = AddressBook.newBuilder();
			pojoBook.getEntries().stream()
					.flatMap(BookEntryPojo::toNodeDetailsEntry)
					.forEach(addressBook::addNodeAddress);
			return addressBook.build().toByteArray();
		} catch (IOException ex) {
			throw new IllegalArgumentException("Not a Node Details file!", ex);
		}
	}

	@Override
	public String preferredFileName() {
		return "nodeDetails.json";
	}
}
