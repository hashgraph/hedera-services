package com.hedera.services.state.exports;

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

import com.google.common.primitives.Ints;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.hedera.services.legacy.stream.RecordStream.TYPE_FILE_HASH;
import static com.hedera.services.legacy.stream.RecordStream.TYPE_SIGNATURE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StandardSigFileWriterTest {
	String toSign = "src/test/resources/bootstrap/standard.properties";
	String cannotSign = "src/test/resources/oops/bootstrap/not-so-standard.properties";
	byte[] pretendSig = "not-really-a-sig-at-all".getBytes();
	FileHashReader hashReader = new Sha384HashReader();

	SigFileWriter subject = new StandardSigFileWriter();

	@Test
	public void rethrowsIaeOnIoFailure() {
		// expect:
		Assertions.assertThrows(UncheckedIOException.class, () ->
				subject.writeSigFile(cannotSign, new byte[0], new byte[0]));
	}

	@Test
	public void writesExpectedFile() throws Exception {
		// setup:
		var hash = hashReader.readHash(toSign);

		// given:
		var expectedWritten = legacy(toSign, pretendSig, hash);
		byte[] expectedBytes = Files.readAllBytes(Paths.get(expectedWritten));
		new File(expectedWritten).delete();

		// when:
		var actualWritten = subject.writeSigFile(toSign, pretendSig, hash);
		byte[] actualBytes = Files.readAllBytes(Paths.get(actualWritten));
		new File(actualWritten).delete();

		// then:
		assertEquals(expectedWritten, actualWritten);
		assertArrayEquals(expectedBytes, actualBytes);
	}

	public static String legacy(String fileName, byte[] signature, byte[] fileHash) throws IOException {
			String newFileName = fileName + "_sig";
			try (FileOutputStream output = new FileOutputStream(newFileName, false)) {
				output.write(TYPE_FILE_HASH);
				output.write(fileHash);
				output.write(TYPE_SIGNATURE);
				output.write(Ints.toByteArray(signature.length));
				output.write(signature);
				return newFileName;
			}
	}
}