/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss.cryptography.nativesupport;

/**
 * Defines the supported architectures.
 */
public enum Architecture {
    /**
     * AMD64
     */
    AMD64,
    /**
     * ARM64
     */
    ARM64,
    /**
     * I386
     */
    I386;

    /**
     * Attempts to determine the current architecture based on the system property "os.arch".
     *
     * @return the current architecture.
     * @throws IllegalStateException if the current architecture is not supported.
     */
    public static Architecture current() {
        final String osArch = System.getProperty("os.arch").toLowerCase();
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            return AMD64;
        } else if (osArch.contains("arm64") || osArch.contains("aarch64")) {
            return ARM64;
        } else if (osArch.contains("i386")) {
            return I386;
        } else {
            throw new IllegalStateException("Unsupported architecture: " + osArch);
        }
    }
}
