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

package com.swirlds.platform.crypto;

import com.swirlds.common.config.ConfigUtils;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.resource.ResourceLoader;
import com.swirlds.platform.util.BootstrapUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.util.Map;
import java.util.Set;

import static com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader.loadConfigFile;
import static com.swirlds.platform.state.address.AddressBookNetworkUtils.isLocal;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Execution(ExecutionMode.CONCURRENT)
class EnhancedKeyStoreLoaderTest {

    @TempDir
    Path testDataDirectory;

    @BeforeEach
    void testSetup() throws IOException {
        final ResourceLoader<EnhancedKeyStoreLoaderTest> loader =
                new ResourceLoader<>(EnhancedKeyStoreLoaderTest.class);
        final Path tempDir = loader.loadDirectory("com/swirlds/platform/crypto/EnhancedKeyStoreLoader");

        Files.move(tempDir, testDataDirectory, REPLACE_EXISTING);
    }

    @Test
    @DisplayName("Validate Test Data")
    void validateTestDataDirectory() {
        assertThat(testDataDirectory).exists().isDirectory().isReadable();
        assertThat(testDataDirectory.resolve("legacy-valid")).exists().isNotEmptyDirectory();
        assertThat(testDataDirectory.resolve("legacy-invalid-case-1")).exists().isNotEmptyDirectory();
        assertThat(testDataDirectory.resolve("legacy-invalid-case-2")).exists().isNotEmptyDirectory();
        assertThat(testDataDirectory.resolve("hybrid-valid")).exists().isNotEmptyDirectory();
        assertThat(testDataDirectory.resolve("hybrid-invalid-case-1")).exists().isNotEmptyDirectory();
        assertThat(testDataDirectory.resolve("hybrid-invalid-case-2")).exists().isNotEmptyDirectory();
        assertThat(testDataDirectory.resolve("enhanced-valid")).exists().isNotEmptyDirectory();
        assertThat(testDataDirectory.resolve("enhanced-invalid-case-1")).exists().isNotEmptyDirectory();
        assertThat(testDataDirectory.resolve("enhanced-invalid-case-2")).exists().isNotEmptyDirectory();
        assertThat(testDataDirectory.resolve("legacy-valid").resolve("public.pfx"))
                .exists()
                .isNotEmptyFile();
        assertThat(testDataDirectory.resolve("config.txt")).exists().isNotEmptyFile();
        assertThat(testDataDirectory.resolve("settings.txt")).exists().isNotEmptyFile();
    }

    @ParameterizedTest
    @DisplayName("KeyStore Loader Positive Test")
    @ValueSource(strings = {"legacy-valid", "hybrid-valid", "enhanced-valid"})
    void keyStoreLoaderPositiveTest(final String directoryName) throws IOException, KeyLoadingException, KeyStoreException {
        final Path keyDirectory = testDataDirectory.resolve(directoryName);
        final AddressBook addressBook = addressBook();
        final EnhancedKeyStoreLoader loader = EnhancedKeyStoreLoader.using(addressBook, configure(keyDirectory));

        assertThat(keyDirectory).exists().isDirectory().isReadable().isNotEmptyDirectory();

        assertThat(loader).isNotNull();
        assertThatCode(loader::scan).doesNotThrowAnyException();
        assertThatCode(loader::verify).doesNotThrowAnyException();
        assertThatCode(loader::injectInAddressBook).doesNotThrowAnyException();

        final Map<NodeId, KeysAndCerts> kc = loader.keysAndCerts();
        for (int i = 0; i < addressBook.getSize(); i++) {
            final NodeId nodeId = addressBook.getNodeId(i);
            final Address addr = addressBook.getAddress(nodeId);

            if (!isLocal(addr)) {
                assertThat(kc).doesNotContainKey(nodeId);
            } else {
                assertThat(kc).containsKey(nodeId);
                assertThat(kc.get(nodeId)).isNotNull();

                final KeysAndCerts keysAndCerts = kc.get(nodeId);
                assertThat(keysAndCerts.agrCert()).isNotNull();
                assertThat(keysAndCerts.sigCert()).isNotNull();
                assertThat(keysAndCerts.agrKeyPair()).isNotNull();
                assertThat(keysAndCerts.sigKeyPair()).isNotNull();
            }

            assertThat(addr.getAgreePublicKey()).isNotNull();
            assertThat(addr.getSigPublicKey()).isNotNull();
        }
    }

    @ParameterizedTest
    @DisplayName("KeyStore Loader Negative Type 1 Test")
    @ValueSource(strings = {"legacy-invalid-case-1", "hybrid-invalid-case-1", "enhanced-invalid-case-1"})
    void keyStoreLoaderNegativeCase1Test(final String directoryName) throws IOException {
        final Path keyDirectory = testDataDirectory.resolve(directoryName);
        final AddressBook addressBook = addressBook();
        final EnhancedKeyStoreLoader loader = EnhancedKeyStoreLoader.using(addressBook, configure(keyDirectory));

        assertThat(keyDirectory).exists().isDirectory().isReadable().isNotEmptyDirectory();

        assertThat(loader).isNotNull();
        assertThatCode(loader::scan).doesNotThrowAnyException();
        assertThatCode(loader::verify).isInstanceOf(KeyLoadingException.class);
        assertThatCode(loader::injectInAddressBook).isInstanceOf(KeyLoadingException.class);
        assertThatCode(loader::keysAndCerts).isInstanceOf(KeyLoadingException.class);
    }

    @ParameterizedTest
    @DisplayName("KeyStore Loader Negative Type 2 Test")
    @ValueSource(strings = {"legacy-invalid-case-2", "hybrid-invalid-case-2", "enhanced-invalid-case-2"})
    void keyStoreLoaderNegativeCase2Test(final String directoryName) throws IOException {
        final Path keyDirectory = testDataDirectory.resolve(directoryName);
        final AddressBook addressBook = addressBook();
        final EnhancedKeyStoreLoader loader = EnhancedKeyStoreLoader.using(addressBook, configure(keyDirectory));

        assertThat(keyDirectory).exists().isDirectory().isReadable().isNotEmptyDirectory();

        assertThat(loader).isNotNull();
        assertThatCode(loader::scan).doesNotThrowAnyException();
        assertThatCode(loader::verify).isInstanceOf(KeyLoadingException.class);
        assertThatCode(loader::injectInAddressBook).doesNotThrowAnyException();
        assertThatCode(loader::keysAndCerts).isInstanceOf(KeyLoadingException.class);
    }

    private Configuration configure(final Path keyDirectory) throws IOException {
        ConfigurationBuilder builder = ConfigurationBuilder.create();
        ConfigUtils.scanAndRegisterAllConfigTypes(builder, Set.of("com.swirlds"));
        BootstrapUtils.setupConfigBuilder(builder, testDataDirectory.resolve("settings.txt"));

        builder.withValue("paths.keysDirPath", keyDirectory.toAbsolutePath().toString());

        return builder.build();
    }

    private AddressBook addressBook() {
        return loadConfigFile(testDataDirectory.resolve("config.txt")).getAddressBook();
    }
}
