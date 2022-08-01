/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.setup;

import static com.hedera.services.setup.InfrastructureBundle.allImplied;
import static com.hedera.services.setup.InfrastructureInitializer.initializeBundle;

import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.state.virtual.VirtualMapFactory.JasperDbBuilderFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.engine.CryptoEngine;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import org.apache.commons.io.FileUtils;

public class InfrastructureManager {
    private static final String BASE_STORAGE_DIR = "databases";
    public static final Cryptography CRYPTO = new CryptoEngine();

    private InfrastructureManager() {
        throw new UnsupportedOperationException();
    }

    public static InfrastructureBundle loadOrCreateBundle(
            final Map<String, Object> config, Collection<InfrastructureType> types) {
        types = allImplied(types);
        final InfrastructureBundle bundle;
        final var dir = bundleDirFor(config, types);
        ensure(dir);
        if (bundleExistsWith(config, types)) {
            System.out.println("\n- Found saved bundle in " + dir + ", loading...");
            bundle = loadBundle(config, types);
        } else {
            System.out.println("\n- No saved bundle at " + dir + ", creating now...");
            bundle = newBundle(config, types);
        }
        System.out.println("- done.");
        return bundle;
    }

    private static InfrastructureBundle newBundle(
            final Map<String, Object> config, final Collection<InfrastructureType> types) {
        final var dir = bundleDirFor(config, types);
        try {
            FileUtils.cleanDirectory(new File(dir));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final var bundle = new InfrastructureBundle(types);
        bundle.abInitio(dir);
        System.out.println("\n- And initializing bundle at " + dir + "...");
        initializeBundle(config, bundle);
        bundle.toStorage(dir);
        return bundle;
    }

    private static InfrastructureBundle loadBundle(
            final Map<String, Object> config, final Collection<InfrastructureType> types) {
        if (!bundleExistsWith(config, types)) {
            throw new IllegalArgumentException(
                    "Bundle " + bundleDirFor(config, types) + " was not found");
        }
        final var dir = bundleDirFor(config, types);
        final var bundle = new InfrastructureBundle(types);
        bundle.loadFrom(dir);
        return bundle;
    }

    private static boolean bundleExistsWith(
            final Map<String, Object> config, final Collection<InfrastructureType> types) {
        final var dir = bundleDirFor(config, types);
        boolean allPresent = true;
        for (final var type : types) {
            final var f = new File(type.locWithin(dir));
            allPresent &= f.exists();
        }
        return allPresent;
    }

    public static VirtualMapFactory newVmFactory(final String storageLoc) {
        final var jdbBuilderFactory =
                new JasperDbBuilderFactory() {
                    @Override
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    public <K extends VirtualKey<? super K>, V extends VirtualValue>
                            JasperDbBuilder<K, V> newJdbBuilder() {
                        return new JasperDbBuilder().storageDir(Paths.get(storageLoc));
                    }
                };
        return new VirtualMapFactory(jdbBuilderFactory);
    }

    private static String bundleDirFor(
            final Map<String, Object> config, final Collection<InfrastructureType> types) {
        final var sb = new StringBuilder("bundle").append(InfrastructureBundle.codeFor(types));
        config.keySet().stream()
                .sorted()
                .forEach(key -> sb.append("_").append(key).append(config.get(key)));
        return BASE_STORAGE_DIR + File.separator + sb;
    }

    private static void ensure(final String loc) {
        final var f = new File(loc);
        if (!f.exists()) {
            if (!f.mkdirs()) {
                throw new IllegalStateException(
                        "Failed to create directory " + f.getAbsolutePath());
            }
        } else if (!f.isDirectory()) {
            throw new IllegalStateException(f.getAbsolutePath() + " is not a directory");
        }
    }
}
