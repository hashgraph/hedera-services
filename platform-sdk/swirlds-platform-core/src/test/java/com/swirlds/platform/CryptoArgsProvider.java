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

package com.swirlds.platform;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.RandomAddressBookGenerator.WeightDistributionStrategy;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.test.framework.ResourceLoader;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/**
 * This class is used for generating unit test method parameters, even though IntelliJ says it is not used.
 */
public class CryptoArgsProvider {
    public static final int NUMBER_OF_ADDRESSES = 10;
    private static final char[] PASSWORD = "password".toCharArray();

    /**
     * @return 2 sets of arguments, 1 generated and 1 loaded from files
     */
    static Stream<Arguments> basicTestArgs() throws Exception {
        final ExecutorService threadPool = Executors.newFixedThreadPool(NUMBER_OF_ADDRESSES);

        Instant start = Instant.now();
        final AddressBook loadedAB = createAddressBook();
        final KeysAndCerts[] loadedC =
                CryptoStatic.loadKeysAndCerts(loadedAB, ResourceLoader.getFile("preGeneratedKeysAndCerts/"), PASSWORD);
        System.out.println(
                "Key loading took " + Duration.between(start, Instant.now()).toMillis());

        start = Instant.now();
        final AddressBook genAB = createAddressBook();
        final KeysAndCerts[] genC = CryptoStatic.generateKeysAndCerts(genAB, threadPool);
        System.out.println(
                "Key generating took " + Duration.between(start, Instant.now()).toMillis());
        return Stream.of(Arguments.of(loadedAB, loadedC), Arguments.of(genAB, genC));
    }

    private static AddressBook createAddressBook() {
        final AddressBook addresses = new RandomAddressBookGenerator()
                .setSize(NUMBER_OF_ADDRESSES)
                .setWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .setSequentialIds(true)
                .build();

        for (int i = 0; i < addresses.getSize(); i++) {
            addresses.add(addresses.getAddress(i).copySetSelfName(memberName(i)).copySetOwnHost(true));
        }

        return addresses;
    }

    private static String memberName(int num) {
        final int base = 26;
        final int padding = 4;
        final StringBuilder res = new StringBuilder();
        int rem;
        while (num > 0) {
            rem = num % base;
            final char c = (char) ('a' + rem);
            res.append(c);
            num /= base;
        }
        res.append("a".repeat(Math.max(0, padding - res.length())));
        return res.reverse().toString();
    }
}
