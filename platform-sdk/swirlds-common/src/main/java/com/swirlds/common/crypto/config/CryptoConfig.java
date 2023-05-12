/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
 * @param cpuVerifierThreadRatio
 * 		the ratio of simultaneous CPU threads to utilize for signature verification. A value between {@code 0.0} and
 *        {@code 1.0} inclusive representing the percentage of cores that should be used for signature verification.
 * @param cpuDigestThreadRatio
 * 		the ratio of simultaneous CPU threads to utilize for hashing. A value between {@code 0.0} and {@code 1.0}
 * 		inclusive representing the percentage of cores that should be used for hash computations.
 * @param cpuVerifierQueueSize
 * 		the fixed size of the CPU verifier queue. A value greater than zero representing the upper bound of the CPU
 * 		signature verification queue.
 * @param cpuDigestQueueSize
 * 		the fixed size of the CPU hashing queue. A value greater than zero representing the upper bound of the CPU
 * 		hashing queue.
 * @param forceCpu
 * 		should only the CPU be used for cryptography. true if only the CPU should be used for cryptography and the GPU
 * 		should be bypassed.
 * @param keystorePassword
 * 		the password used to protect the PKCS12 key stores containing the nodes RSA keys. The password used to protect
 * 		the PKCS12 key stores containing the node RSA public/private key pairs.
 */
@ConfigData("crypto")
public record CryptoConfig(
        @ConfigProperty(defaultValue = "0.5") double cpuVerifierThreadRatio,
        @ConfigProperty(defaultValue = "0.5") double cpuDigestThreadRatio,
        @ConfigProperty(defaultValue = "100") int cpuVerifierQueueSize,
        @ConfigProperty(defaultValue = "100") int cpuDigestQueueSize,
        @ConfigProperty(defaultValue = "true") boolean forceCpu,
        @ConfigProperty(defaultValue = "password") String keystorePassword) {

    /**
     * Calculates the number of threads needed to achieve the CPU core ratio given by {@link
     * #cpuVerifierThreadRatio()}.
     *
     * @return the number of threads to be allocated
     */
    public int computeCpuVerifierThreadCount() {
        final int numberOfCores = Runtime.getRuntime().availableProcessors();
        final double interimThreadCount = Math.ceil(numberOfCores * cpuVerifierThreadRatio());

        return (interimThreadCount >= 1.0) ? (int) interimThreadCount : 1;
    }

    /**
     * Calculates the number of threads needed to achieve the CPU core ratio given by {@link
     * #cpuDigestThreadRatio()}.
     *
     * @return the number of threads to be allocated
     */
    public int computeCpuDigestThreadCount() {
        final int numberOfCores = Runtime.getRuntime().availableProcessors();
        final double interimThreadCount = Math.ceil(numberOfCores * cpuDigestThreadRatio());

        return (interimThreadCount >= 1.0) ? (int) interimThreadCount : 1;
    }
}
