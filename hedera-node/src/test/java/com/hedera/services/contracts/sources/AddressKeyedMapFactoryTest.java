package com.hedera.services.contracts.sources;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.utils.EntityIdUtils;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.*;
import static org.junit.jupiter.api.Assertions.*;

class AddressKeyedMapFactoryTest {
	@Test
	public void toAddressConversion() {
		// given:
		var mapper = toAddressMapping(LEGACY_BYTECODE_PATH_PATTERN);
		var key = "/666/s888";
		// and:
		var expected = EntityIdUtils.asSolidityAddress(0, 666, 888);

		// when:
		var actual = mapper.apply(key);

		// then:
		assertArrayEquals(expected, actual);
	}

	@Test
	public void toKeyConversionWorks() {
		// given:
		var mapper = toKeyMapping(LEGACY_BYTECODE_PATH_TEMPLATE);
		var address = EntityIdUtils.asSolidityAddress(0, 666, 888);
		// and:
		var expected = "/666/s888";

		// when:
		var actual = mapper.apply(address);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	public void isRelevantWorks() {
		// given:
		var realKey = "/666/s888";
		var fakeKey = "/a66/s888";
		var pred = toRelevancyPredicate(LEGACY_BYTECODE_PATH_PATTERN);

		// expect:
		assertTrue(pred.test(realKey));
		assertFalse(pred.test(fakeKey));
	}

	@Test
	public void bytecodeProductHasMapSemantics() {
		// setup:
		Map<String, byte[]> delegate = new HashMap<>();
		delegate.put(("/2/s7"), "APRIORI".getBytes());
		// and:
		var address1 = EntityIdUtils.asSolidityAddress(0,2,3);
		var address2 = EntityIdUtils.asSolidityAddress(0,3333,4);
		var address3 = EntityIdUtils.asSolidityAddress(0,4,555555);
		// and:
		var theData = "THE".getBytes();
		var someData = "SOME".getBytes();
		var moreData = "MORE".getBytes();

		// given:
		var storageMap = bytecodeMapFrom(delegate);

		// when:
		storageMap.put(address1, someData);
		storageMap.put(address2, moreData);
		storageMap.put(address3, theData);

		assertFalse(storageMap.isEmpty());
		assertEquals(4, storageMap.size());
		storageMap.remove(address2);
		assertEquals(3, storageMap.size());
		assertEquals(
				"/2/s3->SOME, /4/s555555->THE, /2/s7->APRIORI",
				delegate.entrySet()
						.stream()
						.sorted(Comparator.comparingLong(entry ->
								Long.parseLong(entry.getKey().substring(
										entry.getKey().indexOf('s') + 1, entry.getKey().indexOf('s') + 2
								))))
						.map(entry -> String.format("%s->%s", entry.getKey(), new String(entry.getValue())))
						.collect(Collectors.joining(", ")));

		assertTrue(storageMap.containsKey(address1));
		assertFalse(storageMap.containsKey(address2));

		storageMap.clear();
		assertTrue(storageMap.isEmpty());
	}

	@Test
	public void productHasFilterSet() {
		// setup:
		Map<String, byte[]> delegate = new HashMap<>();
		delegate.put(("NOT-REAL-KEY"), "APRIORI".getBytes());

		// given:
		var storageMap = bytecodeMapFrom(delegate);

		// expect:
		assertTrue(storageMap.entrySet().isEmpty());
	}

	@Test
	public void storageProductHasMapSemantics() {
		// setup:
		Map<String, byte[]> delegate = new HashMap<>();
		delegate.put(("/2/d7"), "APRIORI".getBytes());
		// and:
		var address1 = EntityIdUtils.asSolidityAddress(0,2,3);
		var address2 = EntityIdUtils.asSolidityAddress(0,3333,4);
		var address3 = EntityIdUtils.asSolidityAddress(0,4,555555);
		// and:
		var theData = "THE".getBytes();
		var someData = "SOME".getBytes();
		var moreData = "MORE".getBytes();

		// given:
		var storageMap = storageMapFrom(delegate);

		// when:
		storageMap.put(address1, someData);
		storageMap.put(address2, moreData);
		storageMap.put(address3, theData);

		assertFalse(storageMap.isEmpty());
		assertEquals(4, storageMap.size());
		storageMap.remove(address2);
		assertEquals(3, storageMap.size());
		assertEquals(
				"/2/d3->SOME, /4/d555555->THE, /2/d7->APRIORI",
				delegate.entrySet()
						.stream()
						.sorted(Comparator.comparingLong(entry ->
								Long.parseLong(entry.getKey().substring(
										entry.getKey().indexOf('d') + 1, entry.getKey().indexOf('d') + 2
								))))
						.map(entry -> String.format("%s->%s", entry.getKey(), new String(entry.getValue())))
						.collect(Collectors.joining(", ")));

		assertTrue(storageMap.containsKey(address1));
		assertFalse(storageMap.containsKey(address2));

		storageMap.clear();
		assertTrue(storageMap.isEmpty());
	}
}
