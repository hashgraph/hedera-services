/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.state.exports;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;

class Sha384HashReaderTest {
    String extantLoc = "src/test/resources/bootstrap/standard.properties";
    String imaginaryLoc = "src/test/resources/bootstrap/not-so-standard.properties";

    Sha384HashReader subject = new Sha384HashReader();

    @Test
    void rethrowsIllegalArgumentExceptionIfMissingFile() {
        // expect:
        assertThrows(UncheckedIOException.class, () -> subject.readHash(imaginaryLoc));
    }

    @Test
    void matchesLegacyValue() throws IOException, NoSuchAlgorithmException {
        // expect:
        assertArrayEquals(legacy(extantLoc), subject.readHash(extantLoc));
    }

    public static byte[] legacy(String fileName) throws NoSuchAlgorithmException, IOException {
        MessageDigest md;
        md = MessageDigest.getInstance("SHA-384");

        byte[] array = Files.readAllBytes(Paths.get(fileName));
        byte[] fileHash = md.digest(array);
        return fileHash;
    }
}
