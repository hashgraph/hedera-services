/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.crypto.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Configuration of the crypto system.
 *
 * @param cpuDigestThreadRatio   the ratio of simultaneous CPU threads to utilize for hashing. A value between
 *                               {@code 0.0} and {@code 1.0} inclusive representing the percentage of cores that should
 *                               be used for hash computations.
 * @param keystorePassword       the password used to protect the PKCS12 key stores containing the nodes RSA keys. The
 *                               password used to protect the PKCS12 key stores containing the node RSA public/private
 *                               key pairs.
 * @param enableNewKeyStoreModel whether to enable the new key store model which uses separate PKCS #8 key stores for
 *                               each node. This model is compatible with most industry standard tools and libraries
 *                               including OpenSSL, Java Keytool, and many others.
 */
@ConfigData("crypto")
public record CryptoConfig(
        @ConfigProperty(defaultValue = "0.5") double cpuDigestThreadRatio,
        @ConfigProperty(defaultValue = "password") String keystorePassword,
        @ConfigProperty(defaultValue = "true") boolean enableNewKeyStoreModel) {

    /**
     * Calculates the number of threads needed to achieve the CPU core ratio given by {@link #cpuDigestThreadRatio()}.
     *
     * @return the number of threads to be allocated
     */
    public int computeCpuDigestThreadCount() {
        final int numberOfCores = Runtime.getRuntime().availableProcessors();
        final double interimThreadCount = Math.ceil(numberOfCores * cpuDigestThreadRatio());

        return (interimThreadCount >= 1.0) ? (int) interimThreadCount : 1;
    }
}
