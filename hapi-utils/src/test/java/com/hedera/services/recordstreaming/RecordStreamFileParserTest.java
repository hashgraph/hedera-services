package com.hedera.services.recordstreaming;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RecordStreamFileParserTest {

	public static final String PATH_TO_FILES = "src/test/resources/recordstream";

	@Test
	void parsingRecordFilesSucceeds() throws IOException {
		final var allStreamFiles = Files.walk(Path.of(PATH_TO_FILES))
				.filter(path -> path.toString().endsWith(".rcd"))
				.toList();
		for (var file : allStreamFiles) {
			final var pair = RecordStreamFileParser.readRecordStreamFile(file.toString());
			assertEquals(6, pair.getLeft());
			assertNotNull(pair.getRight());
		}
	}

	@Test
	void parsingSignatureRecordFilesSucceeds() throws IOException {
		final var signatureFiles = Files.walk(Path.of(PATH_TO_FILES))
				.filter(path -> path.toString().endsWith(".rcd_sig"))
				.toList();
		for (final var file : signatureFiles) {
			final var signatureFile = RecordStreamFileParser.readRecordStreamSignatureFile(file.toString());
			assertNotNull(signatureFile);
		}
	}
}