/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.cli.test.sign;

import com.swirlds.platform.util.FileSigningUtils;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Objects;

public class TestUtils {
    static String HAPI_VERSION = "0.37.0-allowance-SNAPSHOT";

    static KeyPair loadKey() {
        try {
            return FileSigningUtils.loadPfxKey(
                    Path.of(Objects.requireNonNull(TestUtils.class
                                    .getClassLoader()
                                    .getResource("com.hedera.services.cli.test.sign/private-aaaa.pfx"))
                            .toURI()),
                    "password",
                    "s-aaaa");

        } catch (final URISyntaxException e) {
            throw new RuntimeException("Failed to get resource", e);
        }
    }

    static KeyPair loadNode0Key(String fileName) {
        try {
            return FileSigningUtils.loadPfxKey(
                    Path.of(Objects.requireNonNull(TestUtils.class
                                    .getClassLoader()
                                    .getResource("com.hedera.services.cli.test.sign/" + fileName))
                            .toURI()),
                    "password",
                    "s-node0000");

        } catch (final URISyntaxException e) {
            throw new RuntimeException("Failed to get resource", e);
        }
    }

    static Path loadResourceFile(String resourceFileName) {
        return Path.of(Objects.requireNonNull(TestUtils.class
                        .getClassLoader()
                        .getResource("com.hedera.services.cli.test.sign/" + resourceFileName))
                .getPath());
    }
}
