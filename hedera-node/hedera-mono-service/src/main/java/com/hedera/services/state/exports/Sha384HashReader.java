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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Sha384HashReader implements FileHashReader {
    private static final MessageDigest SHA384_MD;

    static {
        try {
            SHA384_MD = MessageDigest.getInstance("SHA-384");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-384 not supported by Java API!");
        }
    }

    @Override
    public byte[] readHash(String targetLoc) {
        try {
            byte[] data = Files.readAllBytes(Paths.get(targetLoc));
            return SHA384_MD.digest(data);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    String.format("I/O error reading hash of '%s'!", targetLoc), e);
        }
    }
}
