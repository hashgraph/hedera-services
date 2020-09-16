package com.hedera.services.legacy.unit;

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

import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.swirlds.blob.BinaryObjectNotFoundException;
import com.swirlds.blob.internal.db.BlobStoragePipeline;
import com.swirlds.blob.internal.db.DbManager;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class LegacyMerkleOptionalBlobTest {
	Random random = new Random();

	@Test
	public void testCreates() throws SQLException {
		try (BlobStoragePipeline pipeline = DbManager.getInstance().blob()) {
			final long binaryObjectCountBefore = pipeline.retrieveNumberOfBlobs();
			byte[] fileContents = new byte[1024];
			random.nextBytes(fileContents);

			MerkleOptionalBlob sv = new MerkleOptionalBlob(fileContents);

			final long binaryObjectCountAfter = pipeline.retrieveNumberOfBlobs();

			assertEquals(binaryObjectCountBefore + 1, binaryObjectCountAfter);
		}
	}

	@Test
	public void testDeletes() throws SQLException {
		try (final BlobStoragePipeline pipeline = DbManager.getInstance().blob()) {
			final long binaryObjectCountBefore = pipeline.retrieveNumberOfBlobs();

			byte[] fileContents = new byte[1024];
			random.nextBytes(fileContents);
			final MerkleOptionalBlob sv = new MerkleOptionalBlob(fileContents);
			final long svId = sv.getDelegate().getId();

			byte[] fileContents2 = new byte[1024];
			random.nextBytes(fileContents2);
			final MerkleOptionalBlob sv2 = new MerkleOptionalBlob(fileContents2);

			final long binaryObjectCountAfterCreate = pipeline.retrieveNumberOfBlobs();

			assertEquals(binaryObjectCountBefore + 2, binaryObjectCountAfterCreate);

			sv.delete();

			final long binaryObjectCountAfterDelete = pipeline.retrieveNumberOfBlobs();

			assertEquals(binaryObjectCountAfterCreate - 1, binaryObjectCountAfterDelete);
			assertThrows(BinaryObjectNotFoundException.class, () -> pipeline.get(svId));
			assertDoesNotThrow(() -> sv2.getData());
		}
	}
}
