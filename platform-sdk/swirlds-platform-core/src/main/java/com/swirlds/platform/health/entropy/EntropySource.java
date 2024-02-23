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

package com.swirlds.platform.health.entropy;

import java.security.SecureRandom;

/**
 * Source of entropy using {@link SecureRandom}.
 *
 * @param description
 * 		a description of the source of {@link SecureRandom} entropy
 * @param randomSupplier
 * 		supplies an instance of {@link SecureRandom}
 */
public record EntropySource(String description, SecureRandomSupplier randomSupplier) {

    /**
     * Creates an {@link EntropySource} capable of creating new instances of {@link SecureRandom} using the default
     * constructor.
     *
     * @return a new instance of {@link EntropySource}
     */
    public static EntropySource systemDefault() {
        return new EntropySource("System Default", SecureRandom::new);
    }

    /**
     * <p>
     * Creates an {@link EntropySource} capable of creating new instances of {@link SecureRandom} using the specified
     * algorithm and provider ({@link SecureRandom#getInstance(String, String)}.
     * </p>
     * <p>
     * If the algorithm or provider are not valid, {@link java.security.NoSuchAlgorithmException} or
     * {@link java.security.NoSuchProviderException} will be thrown, respectively, when attempting to instantiate the
     * {@link SecureRandom}.
     * </p>
     * <p>
     * Depending on the specified algorithm, calls to the {@link SecureRandom} instance may block.
     * </p>
     *
     * @param algorithm
     * 		the name of the RNG algorithm
     * @param provider
     * 		the name of the provider
     * @return a new instance of {@link EntropySource}
     */
    public static EntropySource of(final String algorithm, final String provider) {
        return new EntropySource(algorithm + ":" + provider, () -> SecureRandom.getInstance(algorithm, provider));
    }

    /**
     * <p>
     * Creates an {@link EntropySource} capable of creating new instances of {@link SecureRandom} using
     * {@link SecureRandom#getInstanceStrong()}. The method of instantiation used by this method depends on
     * configuration and behave differently when invoked from different environments.
     * </p>
     * <p>
     * Using this {@link EntropySource} is not guaranteed to test the availability of entropy.
     * </p>
     *
     * @return a new instance of {@link EntropySource}
     */
    public static EntropySource strong() {
        return new EntropySource("Strong Instance", SecureRandom::getInstanceStrong);
    }
}
