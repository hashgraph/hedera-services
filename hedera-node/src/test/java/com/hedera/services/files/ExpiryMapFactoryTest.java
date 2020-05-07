package com.hedera.services.files;

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

import com.google.common.primitives.Longs;
import com.hedera.services.fees.calculation.FeeCalcUtils;
import com.hedera.test.utils.IdUtils;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hedera.services.files.ExpiryMapFactory.expiryMapFrom;
import static com.hedera.services.files.ExpiryMapFactory.toFid;
import static com.hedera.services.files.ExpiryMapFactory.toKeyString;
import static org.junit.jupiter.api.Assertions.*;

class ExpiryMapFactoryTest {
	@Test
	public void toFidConversionWorks() {
		// given:
		var key = "/666/e888";
		// and:
		var expected = IdUtils.asFile("0.666.888");

		// when:
		var actual = toFid(key);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	public void toKeyConversionWorks() {
		// given:
		var fid = IdUtils.asFile("0.2.3");
		// and:
		var expected = FeeCalcUtils.pathOf(fid).replace("f", "e");

		// when:
		var actual = toKeyString(fid);

		// then:
		assertEquals(expected, actual);
	}

	private String asLegacyPath(String fid) {
		return FeeCalcUtils.pathOf(IdUtils.asFile(fid)).replace("f", "e");
	}

	@Test
	public void productHasMapSemantics() {
		// setup:
		Map<String, byte[]> delegate = new HashMap<>();
		delegate.put(asLegacyPath("0.2.7"), Longs.toByteArray(111));
		// and:
		var fid1 = IdUtils.asFile("0.2.3");
		var fid2 = IdUtils.asFile("0.3333.4");
		var fid3 = IdUtils.asFile("0.4.555555");
		// and:
		var theData = 222L;
		var someData = 333L;
		var moreData = 444L;
		var overwrittenData = 555L;

		// given:
		var expiryMap = expiryMapFrom(delegate);

		// when:
		expiryMap.put(fid1, overwrittenData);
		expiryMap.put(fid1, someData);
		expiryMap.put(fid2, moreData);
		expiryMap.put(fid3, theData);

		assertFalse(expiryMap.isEmpty());
		assertEquals(4, expiryMap.size());
		assertEquals(java.util.Optional.of(moreData), java.util.Optional.ofNullable(expiryMap.remove(fid2)));
		assertEquals(3, expiryMap.size());
		assertEquals(
				"/2/e3->333, /4/e555555->222, /2/e7->111",
				delegate.entrySet()
						.stream()
						.sorted(Comparator.comparingLong(entry ->
								Long.parseLong(entry.getKey().substring(
										entry.getKey().indexOf('e') + 1, entry.getKey().indexOf('e') + 2
								))))
						.map(entry -> String.format(
								"%s->%d",
								entry.getKey(),
								Longs.fromByteArray(entry.getValue())))
						.collect(Collectors.joining(", ")));

		assertTrue(expiryMap.containsKey(fid1));
		assertFalse(expiryMap.containsKey(fid2));

		expiryMap.clear();
		assertTrue(expiryMap.isEmpty());
	}
}
