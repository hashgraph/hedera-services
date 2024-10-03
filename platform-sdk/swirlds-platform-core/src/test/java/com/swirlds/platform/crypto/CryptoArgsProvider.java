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

package com.swirlds.platform.crypto;

import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.common.test.fixtures.io.ResourceLoader;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.config.PathsConfig;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder.WeightDistributionStrategy;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/**
 * This class is used for generating unit test method parameters, even though IntelliJ says it is not used.
 */
public class CryptoArgsProvider {
    public static final int NUMBER_OF_ADDRESSES = 10;
    private static final char[] PASSWORD = "password".toCharArray();

    /**
     * @return 2 sets of arguments, 1 generated, 1 loaded from files.
     */
    static Stream<Arguments> basicTestArgs() throws Exception {
        Instant start = Instant.now();
        final AddressBookAndCerts addressBookAndCerts = loadAddressBookWithKeys(NUMBER_OF_ADDRESSES);
        start = Instant.now();
        final AddressBook genAB = createAddressBook(NUMBER_OF_ADDRESSES);
        final Map<NodeId, KeysAndCerts> genC = CryptoStatic.generateKeysAndCerts(genAB);
        start = Instant.now();
        return Stream.of(
                Arguments.of(addressBookAndCerts.addressBook(), addressBookAndCerts.nodeIdKeysAndCertsMap()),
                Arguments.of(genAB, genC));
    }

    public static AddressBook createAddressBook(final int size) {
        final AddressBook addresses = RandomAddressBookBuilder.create(Randotron.create())
                .withSize(size)
                .withWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .build();

        for (int i = 0; i < addresses.getSize(); i++) {
            final NodeId nodeId = addresses.getNodeId(i);
            addresses.add(addresses
                    .getAddress(nodeId)
                    .copySetSelfName(RosterUtils.formatNodeName(nodeId.id()))
                    .copySetHostnameInternal("127.0.0.1"));
        }

        return addresses;
    }

    private static Configuration configure(final Path keyDirectory) {
        final ConfigurationBuilder builder = ConfigurationBuilder.create();

        builder.withConfigDataTypes(PathsConfig.class, CryptoConfig.class);

        builder.withValue("paths.keysDirPath", keyDirectory.toAbsolutePath().toString());
        builder.withValue("crypto.password", new String(PASSWORD));

        return builder.build();
    }

    /**
     * returns a record with the addressBook and keys loaded from file.
     *
     * @param size the size of the required address book
     */
    @NonNull
    public static AddressBookAndCerts loadAddressBookWithKeys(final int size)
            throws URISyntaxException, UnrecoverableKeyException, KeyLoadingException, KeyStoreException,
                    NoSuchAlgorithmException, KeyGeneratingException, NoSuchProviderException {
        final AddressBook createdAB = createAddressBook(size);
        final Map<NodeId, KeysAndCerts> loadedC = EnhancedKeyStoreLoader.using(
                        createdAB, configure(ResourceLoader.getFile("preGeneratedPEMKeysAndCerts/")))
                .scan()
                .generateIfNecessary()
                .verify()
                .injectInAddressBook()
                .keysAndCerts();
        return new AddressBookAndCerts(createdAB, loadedC);
    }
}
