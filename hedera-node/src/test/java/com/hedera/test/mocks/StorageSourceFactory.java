package com.hedera.test.mocks;

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

import com.hedera.services.contracts.sources.BlobStorageSource;
import com.hedera.services.files.store.FcBlobsBytesStore;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.swirlds.fcmap.FCMap;
import org.ethereum.datasource.DbSource;

import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.bytecodeMapFrom;

public class StorageSourceFactory {
	public static DbSource<byte[]> from(FCMap<MerkleBlobMeta, MerkleOptionalBlob> storageMap) {
		return new BlobStorageSource(bytecodeMapFrom(
				new FcBlobsBytesStore(MerkleOptionalBlob::new, () -> storageMap)));
	}
}
