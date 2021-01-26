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

import com.hedera.services.fees.calculation.FeeCalcUtilsTest;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hedera.services.files.MetadataMapFactory.metaMapFrom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hedera.services.files.MetadataMapFactory.*;

class MetadataMapFactoryTest {
	private long expiry = 1_234_567L;

	@Test
	public void toAttrMirrorsNulls()  {
		// expect:
		assertTrue(null == toAttr(null));
	}

	@Test
	public void toAttrThrowsIaeOnError() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> toAttr("NONSENSE".getBytes()));
	}

	@Test
	public void toValueThrowsIaeOnError() {
		// expect:
		assertThrows(IllegalArgumentException.class, () ->
				toValueBytes(new JFileInfo(false, null, 1_234_567L)));
	}

	@Test
	public void toValueConversionWorks() throws Throwable {
		// given:
		var validKey = TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey();
		var attr = new JFileInfo(false, validKey, expiry);
		// and:
		var expected = attr.serialize();

		// when:
		var actual = toValueBytes(attr);

		// then:
		assertTrue(Arrays.equals(expected, actual));
	}

	@Test
	public void toAttrConversionWorks() throws Throwable {
		// given:
		var validKey = TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey();
		var expected = new JFileInfo(false, validKey, expiry);
		// and:
		var bytes = expected.serialize();

		// when:
		var actual = toAttr(bytes);

		// then:
		assertEquals(expected.toString(), actual.toString());
	}

	@Test
	public void toFidConversionWorks() {
		// given:
		var key = "/666/k888";
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
		var expected = FeeCalcUtilsTest.pathOfMeta(fid);

		// when:
		var actual = toKeyString(fid);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	public void productHasMapSemantics() throws Throwable {
		// setup:
		Map<String, byte[]> delegate = new HashMap<>();
		var wacl = TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey();
		var attr0 = new JFileInfo(true, wacl, 1_234_567L);
		var attr1 = new JFileInfo(true, wacl, 7_654_321L);
		var attr2 = new JFileInfo(false, wacl, 7_654_321L);
		var attr3 = new JFileInfo(false, wacl, 1_234_567L);
		delegate.put(asLegacyPath("0.2.7"), attr0.serialize());
		// and:
		var fid1 = IdUtils.asFile("0.2.3");
		var fid2 = IdUtils.asFile("0.3333.4");
		var fid3 = IdUtils.asFile("0.4.555555");

		// given:
		var metaMap = metaMapFrom(delegate);

		// when:
		metaMap.put(fid1, attr1);
		metaMap.put(fid2, attr2);
		metaMap.put(fid3, attr3);

		assertFalse(metaMap.isEmpty());
		assertEquals(4, metaMap.size());
		metaMap.remove(fid2);
		assertEquals(3, metaMap.size());
		assertEquals(
				String.format(
						"/2/k3->%s, /4/k555555->%s, /2/k7->%s",
						Arrays.toString(attr1.serialize()),
						Arrays.toString(attr3.serialize()),
						Arrays.toString(attr0.serialize())),
				delegate.entrySet()
						.stream()
						.sorted(Comparator.comparingLong(entry ->
								Long.parseLong(entry.getKey().substring(
										entry.getKey().indexOf('k') + 1, entry.getKey().indexOf('k') + 2
								))))
						.map(entry -> String.format(
								"%s->%s",
								entry.getKey(),
								Arrays.toString(entry.getValue())))
						.collect(Collectors.joining(", ")));

		assertTrue(metaMap.containsKey(fid1));
		assertFalse(metaMap.containsKey(fid2));

		metaMap.clear();
		assertTrue(metaMap.isEmpty());
	}

	private String asLegacyPath(String fid) {
		return FeeCalcUtilsTest.pathOfMeta(IdUtils.asFile(fid));
	}

	@Test
	public void cannotBeConstructed() {
		// expect:
		assertThrows(IllegalStateException.class, MetadataMapFactory::new);
	}
}
