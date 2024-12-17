/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.fixtures.addressbook;

import static org.mockito.Mockito.mock;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.crypto.SerializableX509Certificate;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Utilities for constructing AddressBook needed for tests
 */
public class AddresBookUtils {

    public static AddressBook createPretendBookFrom(final Platform platform, final boolean withKeyDetails) {
        final var cert = mock(X509Certificate.class);
        final var address1 = new Address(
                platform.getSelfId(),
                "",
                "",
                10L,
                null,
                -1,
                "123456789",
                -1,
                new SerializableX509Certificate(cert),
                new SerializableX509Certificate(cert),
                "");
        final var address2 = new Address(
                NodeId.of(1),
                "",
                "",
                10L,
                null,
                -1,
                "123456789",
                -1,
                new SerializableX509Certificate(cert),
                new SerializableX509Certificate(cert),
                "");
        return new AddressBook(List.of(address1, address2));
    }
}
