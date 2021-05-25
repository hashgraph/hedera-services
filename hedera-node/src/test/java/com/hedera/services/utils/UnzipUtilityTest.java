package com.hedera.services.utils;

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

import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

@ExtendWith({ LogCaptureExtension.class, MockitoExtension.class })
class UnzipUtilityTest {
	@Mock
	private ZipInputStream zipIn;

	@Inject
	private LogCaptor logCaptor;

	@LoggingSubject
	private UnzipUtility subject;

	@Test
	void privateConstructorShallThrowException() {
		Constructor<?> ctor;
		try {
			ctor = UnzipUtility.class.getDeclaredConstructor();
			ctor.setAccessible(true);

			ctor.newInstance();
		} catch (Exception e) {
			final var cause = e.getCause();
			assertEquals(
					"UnzipUtility is an utility class. Shouldn't create any instance!",
					cause.getMessage());
		}
	}

	@Test
	void unzipAbortWithRiskyFile() throws Exception {
		final String zipFile = "src/test/resources/testfiles/updateFeature/bad.zip";
		final byte[] data = Files.readAllBytes(Paths.get(zipFile));
		final String dstDir = "./temp";

		assertThrows(IllegalArgumentException.class, () -> {
			UnzipUtility.unzip(data, dstDir);
		});
	}

	@Test
	void unzipSuccessfully() throws Exception {
		final String zipFile = "src/test/resources/testfiles/updateFeature/update.zip";
		final byte[] data = Files.readAllBytes(Paths.get(zipFile));
		final String dstDir = "./temp";

		assertDoesNotThrow(() -> UnzipUtility.unzip(data, dstDir));

		final File file3 = new File("./temp/sdk/new3.txt");
		Assertions.assertTrue(file3.exists());
		file3.delete();
	}

	@Test
	void logsAtErrorWhenUnableToExtractFile() throws IOException {
		// setup:
		final var tmpFile = "shortLived.txt";

		given(zipIn.read(any())).willThrow(IOException.class);

		// then:
		Assertions.assertDoesNotThrow(() -> UnzipUtility.extractSingleFile(zipIn, tmpFile));
		// and:
		assertThat(logCaptor.errorLogs(), contains("Unable to write to file shortLived.txt java.io.IOException: null"));

		// cleanup:
		new File(tmpFile).delete();
	}
}
