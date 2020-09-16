package com.hedera.services.sigs.metadata.lookups;

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

import com.hedera.services.files.HederaFs;
import com.hedera.services.sigs.metadata.FileSigningMetadata;
import com.hederahashgraph.api.proto.java.FileID;
import com.hedera.services.legacy.exception.InvalidFileIDException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Trivial file metadata lookup.
 *
 * @author Michael Tinker
 */
public class HfsSigMetaLookup implements FileSigMetaLookup {
	public static Logger log = LogManager.getLogger(HfsSigMetaLookup.class);

	private final HederaFs hfs;

	public HfsSigMetaLookup(HederaFs hfs) {
		this.hfs = hfs;
	}

	/**
	 * Returns metadata for the given file's signing activity if such metadata
	 * exists in the backing {@code FCStorageWrapper}.
	 *
	 * @param id
	 * 		the file to recover signing metadata for.
	 * @return the desired metadata.
	 * @throws InvalidFileIDException
	 * 		if the backing {@code FCStorageWrapper} has no file at the implied path.
	 */
	@Override
	public FileSigningMetadata lookup(FileID id) throws Exception {
		if (!hfs.exists(id)) {
			throw new InvalidFileIDException("Invalid file!", id);
		}
		return new FileSigningMetadata(hfs.getattr(id).getWacl());
	}
}
