package com.hedera.services.bdd.suites.utils.sysfiles;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;

import java.awt.print.Book;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class AddressBookPojo {
	private List<BookEntryPojo> entries;

	public List<BookEntryPojo> getEntries() {
		return entries;
	}

	public void setEntries(List<BookEntryPojo> entries) {
		this.entries = entries;
	}

	public static AddressBookPojo addressBookFrom(NodeAddressBook book) {
		return from(book, BookEntryPojo::fromAddressBookEntry, AddressBookPojo::withListedIps);
	}

	public static AddressBookPojo nodeDetailsFrom(NodeAddressBook book) {
		return from(book, BookEntryPojo::fromNodeDetailsEntry, pojo -> pojo);
	}

	public static AddressBookPojo withListedIps(AddressBookPojo pojo) {
		Map<Long, List<String>> nodeIps = pojo.entries
				.stream()
				.collect(Collectors.groupingBy(
						AddressBookPojo::effectiveNodeId,
						mapping(BookEntryPojo::getIp, toList())));
		Map<Long, Integer> indices = IntStream.range(0, pojo.entries.size())
				.boxed()
				.collect(toMap(i -> effectiveNodeId(pojo.entries.get(i)), Integer::valueOf, (a, b) -> a));
		List<BookEntryPojo> listedEntries = indices.entrySet()
				.stream()
				.map(nI -> {
					var entry = pojo.getEntries().get(nI.getValue());
					entry.setIp(null);
					entry.setIps(nodeIps.get(nI.getKey()));
					return entry;
				}).collect(toList());
		pojo.setEntries(listedEntries);
		return pojo;
	}

	private static long effectiveNodeId(BookEntryPojo pojo) {
		var id = HapiPropertySource.asAccount(pojo.getMemo());
		return (id.getAccountNum() - 3);
	}

	private static AddressBookPojo from(
			NodeAddressBook book,
			Function<NodeAddress, BookEntryPojo> converter,
			UnaryOperator<AddressBookPojo> finalizer
	) {
		var pojo = new AddressBookPojo();
		pojo.setEntries(book.getNodeAddressList()
				.stream()
				.map(converter)
				.collect(toList()));
		return finalizer.apply(pojo);
	}
}
