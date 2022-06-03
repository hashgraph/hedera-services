package com.hedera.services.recordstreaming;

/*-
 * ‌
 * Hedera Services API Utilities
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RecordStreamingUtilsTest {

	public static final String PATH_TO_FILES = "src/test/resources/recordstream";

	@Test
	void parsingRecordFilesSucceeds() throws IOException {
		final var allStreamFiles = Files.walk(Path.of(PATH_TO_FILES))
				.filter(path -> !path.toString().contains("V5") && path.toString().endsWith(".rcd"))
				.toList();
		for (var file : allStreamFiles) {
			final var pair = RecordStreamingUtils.readRecordStreamFile(file.toString());
			assertEquals(6, pair.getLeft());
			assertNotNull(pair.getRight());
		}
	}

	@Test
	void parsingSignatureRecordFilesSucceeds() throws IOException {
		final var signatureFiles = Files.walk(Path.of(PATH_TO_FILES))
				.filter(path -> path.toString().contains(".rcd_sig"))
				.toList();
		for (final var file : signatureFiles) {
			final var signatureFile = RecordStreamingUtils.readSignatureFile(file.toString());
			assertNotNull(signatureFile);
		}
	}

	@Test
	void parsingUnknownRecordFilesReturnsEmptyPair() throws IOException {
		final var allStreamFiles = Files.walk(Path.of(PATH_TO_FILES))
				.filter(path -> path.toString().contains("V5") && path.toString().endsWith(".rcd"))
				.toList();
		for (var file : allStreamFiles) {
			final var pair = RecordStreamingUtils.readRecordStreamFile(file.toString());
			assertEquals(-1, pair.getLeft());
			assertFalse(pair.getRight().isPresent());
		}
	}

	@Test
	void parsingUnknownSignatureRecordFilesReturnsEmpty() throws IOException {
		final var signatureFiles = Files.walk(Path.of(PATH_TO_FILES))
				.filter(path -> path.toString().contains("V5") && path.toString().endsWith(".rcd_sig"))
				.toList();
		for (final var file : signatureFiles) {
			final var signatureFile = RecordStreamingUtils.readSignatureFile(file.toString());
			assertFalse(signatureFile.isPresent());
		}
	}
}