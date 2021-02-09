package com.hedera.test.forensics.domain;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.fcmap.FCMap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

public class PojoFs {
	private List<PojoFile> files;

	public static PojoFs from(FCMap<MerkleBlobMeta, MerkleOptionalBlob> fs) {
		var pojo = new PojoFs();
		var readable = fs.entrySet()
				.stream()
				.map(PojoFile::fromEntry)
				.sorted(comparing(PojoFile::getPath))
				.collect(toList());
		pojo.setFiles(readable);
		return pojo;
	}

	public void asJsonTo(String readableLoc) throws IOException {
		var om = new ObjectMapper();
		om.writerWithDefaultPrettyPrinter().writeValue(new File(readableLoc), this);
	}

	public List<PojoFile> getFiles() {
		return files;
	}

	public void setFiles(List<PojoFile> files) {
		this.files = files;
	}
}
