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
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import org.apache.commons.lang3.NotImplementedException;

import static com.hedera.services.bdd.suites.utils.sysfiles.AddressBookPojo.nodeDetailsFrom;

public class NodesJsonToGrpcBytes implements SysFileSerde<String> {
	private final ObjectMapper mapper = new ObjectMapper();

	@Override
	public String fromRawFile(byte[] bytes) {
		try {
			var pojoBook = nodeDetailsFrom(NodeAddressBook.parseFrom(bytes));
			return mapper
					.writerWithDefaultPrettyPrinter()
					.writeValueAsString(pojoBook);
		} catch (InvalidProtocolBufferException | JsonProcessingException e) {
			throw new IllegalArgumentException("Not a node details file!", e);
		}
	}

	@Override
	public byte[] toRawFile(String styledFile) {
		throw new NotImplementedException("TBD");
	}

	@Override
	public String preferredFileName() {
		return "nodeDetails.json";
	}
}
