package com.hedera.services.store.models;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.test.utils.IdUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class UniqueTokenTest {
	@Test
	void objectContractWorks() {
		var subj = new UniqueToken(Id.DEFAULT, 1);
		assertEquals(1, subj.getSerialNumber());
		assertEquals(Id.DEFAULT, subj.getTokenId());

		var metadata = new byte[] { 107, 117, 114 };
		subj = new UniqueToken(Id.DEFAULT, 1, RichInstant.MISSING_INSTANT, new Id(1, 2, 3), new byte[] { 111, 23, 85 });
		assertEquals(RichInstant.MISSING_INSTANT, subj.getCreationTime());
		assertEquals(new Id(1, 2, 3), subj.getOwner());
		subj.setSerialNumber(2);
		assertEquals(2, subj.getSerialNumber());

		metadata = new byte[] { 1, 2, 3 };
		subj.setMetadata(metadata);
		assertEquals(metadata, subj.getMetadata());
		subj.setTokenId(Id.DEFAULT);
		assertEquals(Id.DEFAULT, subj.getTokenId());
		subj.setCreationTime(RichInstant.MISSING_INSTANT);
		assertEquals(RichInstant.MISSING_INSTANT, subj.getCreationTime());
	}


	@Test
	void equalsWorks() {
		final var token1 = IdUtils.asModelId("0.0.12345");
		final var token2 = IdUtils.asModelId("0.0.12346");
		final var owner1 = IdUtils.asModelId("0.0.12347");
		final var spender1 = IdUtils.asModelId("0.0.12349");
		final var meta1 = "aa".getBytes(StandardCharsets.UTF_8);
		final var sub1 = new UniqueToken(token1, 1L);
		final var sub2 = new UniqueToken(token1, 2L);
		final var sub3 = new UniqueToken(token2, 1L);
		final var identical = new UniqueToken(token1, 1L);
		sub1.setOwner(owner1);
		sub1.setSpender(spender1);
		sub1.setMetadata(meta1);
		sub2.setOwner(owner1);
		sub2.setSpender(spender1);
		sub2.setMetadata(meta1);
		sub3.setOwner(owner1);
		sub3.setSpender(spender1);
		sub3.setMetadata(meta1);
		identical.setOwner(owner1);
		identical.setSpender(spender1);
		identical.setMetadata(meta1);

		assertEquals(sub1, identical);
		assertNotEquals(sub1, sub2);
		assertNotEquals(sub2, sub3);
		assertNotEquals(sub1, sub3);
	}

	@Test
	void toStringWorks() {
		final var token1 = IdUtils.asModelId("0.0.12345");
		final var owner1 = IdUtils.asModelId("0.0.12346");
		final var spender1 = IdUtils.asModelId("0.0.12347");
		final var meta1 = "aa".getBytes(StandardCharsets.UTF_8);
		final var subject = new UniqueToken(token1, 1L);
		subject.setOwner(owner1);
		subject.setSpender(spender1);
		subject.setMetadata(meta1);
		subject.setCreationTime(RichInstant.MISSING_INSTANT);

		final var expected = "UniqueToken{tokenID=Id[shard=0, realm=0, num=12345], serialNum=1," +
				" metadata=[97, 97], creationTime=RichInstant{seconds=0, nanos=0}, " +
				"owner=Id[shard=0, realm=0, num=12346], spender=Id[shard=0, realm=0, num=12347]}";

		assertEquals(expected, subject.toString());
	}
}
