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

package com.swirlds.platform.crypto;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator.WeightDistributionStrategy;
import com.swirlds.test.framework.ResourceLoader;
import java.time.Duration;
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
     * @return 2 sets of arguments, 1 generated and 1 loaded from files
     */
    static Stream<Arguments> basicTestArgs() throws Exception {
        Instant start = Instant.now();
        final AddressBook loadedAB = createAddressBook();
        final Map<NodeId, KeysAndCerts> loadedC =
                CryptoStatic.loadKeysAndCerts(loadedAB, ResourceLoader.getFile("preGeneratedKeysAndCerts/"), PASSWORD);
        System.out.println(
                "Key loading took " + Duration.between(start, Instant.now()).toMillis());

        start = Instant.now();
        final AddressBook genAB = createAddressBook();
        final Map<NodeId, KeysAndCerts> genC = CryptoStatic.generateKeysAndCerts(genAB);
        System.out.println(
                "Key generating took " + Duration.between(start, Instant.now()).toMillis());
        return Stream.of(Arguments.of(loadedAB, loadedC), Arguments.of(genAB, genC));
    }

    private static AddressBook createAddressBook() {
        final AddressBook addresses = new RandomAddressBookGenerator()
                .setSize(NUMBER_OF_ADDRESSES)
                .setWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .build();

        for (int i = 0; i < addresses.getSize(); i++) {
            final NodeId nodeId = addresses.getNodeId(i);
            addresses.add(
                    addresses.getAddress(nodeId).copySetSelfName(memberName(i)).copySetHostnameInternal("127.0.0.1"));
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
