/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.pairings.nativ3support;

public enum Architecture {
    AMD64("amd64"),
    ARM("arm"),
    ARM64("arm64"),
    I386("i386"),
    PPC64LE("ppc64le"),
    S390X("s390x");

    private final String directoryName;

    Architecture(final String directoryName) {
        this.directoryName = directoryName;
    }

    public String directoryName() {
        return directoryName;
    }

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
        } else if (osArch.contains("arm")) {
            return ARM;
        } else if (osArch.contains("i386")) {
            return I386;
        } else if (osArch.contains("ppc64le")) {
            return PPC64LE;
        } else if (osArch.contains("s390x")) {
            return S390X;
        } else {
            throw new IllegalStateException("Unsupported architecture: " + osArch);
        }
    }
}