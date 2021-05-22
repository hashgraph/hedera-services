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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class UnzipUtilityTest {
	@Test
	public void privateConstructorShallThrowException() {
		Constructor<?> ctor;
		try {
			ctor = UnzipUtility.class.getDeclaredConstructor();
			ctor.setAccessible(true);

			ctor.newInstance();
		} catch (Exception e) {
			IllegalStateException cause = (IllegalStateException) e.getCause();
			assertEquals("UnzipUtility is an utility class. Shouldn't create any instance!", cause.getMessage());
		}
	}

	@Test
	public void unzipAbortWithRiskyFile() throws Exception {
		final String zipFile = "src/test/resources/testfiles/updateFeature/bad.zip";
		final byte[] data = Files.readAllBytes(Paths.get(zipFile));
		final String dstDir = "./temp";

		assertThrows(IllegalArgumentException.class, () -> {
			UnzipUtility.unzip(data, dstDir);
		});
	}

	@Test
	public void unzipSuccessfully() throws Exception {
		final String zipFile = "src/test/resources/testfiles/updateFeature/update.zip";
		final byte[] data = Files.readAllBytes(Paths.get(zipFile));
		final String dstDir = "./temp";

		assertDoesNotThrow(() -> UnzipUtility.unzip(data, dstDir));

		final File file3 = new File("./temp/sdk/new3.txt");
		Assertions.assertTrue(file3.exists());
		file3.delete();
	}
}
