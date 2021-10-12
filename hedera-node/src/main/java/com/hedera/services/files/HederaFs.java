package com.hedera.services.files;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;

/**
 * A non-hierarchical collection of files managed by {@link FileID} using create/read/update/delete semantics.
 *
 * Each file has an associated expiration time and key encapsulated in a {@link HFileMeta}, which also indicates
 * if the file has been deleted. If a file is deleted before it expires, its contents are no longer be mutable or
 * readable; however, files are only purged from the system after they expire.
 *
 * The system's behavior can be extended by registering {@link FileUpdateInterceptor} instances.
 */
public interface HederaFs {
	/**
	 * Gives the number of registered interceptors.
	 *
	 * @return the number of registered interceptors
	 */
	int numRegisteredInterceptors();

	/**
	 * Registers a new {@link FileUpdateInterceptor} with the file system.
	 *
	 * @param updateInterceptor the interceptor to register
	 */
	void register(FileUpdateInterceptor updateInterceptor);

	/**
	 * Creates a new file in the collection with the given data and metadata.
	 *
	 * @param contents the data for the file
	 * @param attr the metadata of the file
	 * @param sponsor the payer for the creation of the file
	 * @return a globally unique entity id
	 */
	FileID create(byte[] contents, HFileMeta attr, AccountID sponsor);

	/**
	 * Sets a file meta attribute of a targeted file, with less validations, contrary to the setAttr method.
	 *
	 * @param id the file to look for
	 * @param attr the meta attribute set in the file
	 */
	void sudoSetattr(FileID id, HFileMeta attr);

	/**
	 * Sets a file meta attribute of a targeted file.
	 *
	 * @param id the file to look for
	 * @param attr the meta attribute set in the file
	 */
	void setattr(FileID id, HFileMeta attr);

	/**
	 * Replaces current content in a targeted file with a new one.
	 *
	 * @param id the file to look for
	 * @param newContents the content to replace the old one
	 */
	void overwrite(FileID id, byte[] newContents);

	/**
	 * Adds additional content to a targeted file.
	 *
	 * @param id the file to look for
	 * @param moreContents the content to be added to what there already is
	 */
	void append(FileID id, byte[] moreContents);

	/**
	 * Deletes a targeted file.
	 *
	 * @param id the file to look for
	 */
	void delete(FileID id);

	/**
	 * Checks for existence of a the given file; this succeeds even after deletion.
	 *
	 * @param id the file to look for
	 * @return its existence
	 */
	boolean exists(FileID id);

	/**
	 * Returns the contents of the given file.
	 *
	 * @param id the file to cat
	 * @return its contents
	 */
	byte[] cat(FileID id);

	/**
	 * Returns the metadata for the given file.
	 *
	 * @param id the file to examine
	 * @return its metadata
	 */
	HFileMeta getattr(FileID id);

	void rm(FileID id);
}
