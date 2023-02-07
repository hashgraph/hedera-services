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
package com.hedera.node.app.service.evm.store.contracts.utils;

import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_ERC_BALANCE_OF_TOKEN;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.addressFromBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class DescriptorUtilsTest {

    @Test
    void test() {
        final var address =
                Bytes.fromHexString(
                        "0x000000000000000000000000000000000000000000000000000000000000077a");

        assertEquals(
                Address.fromHexString("0x000000000000000000000000000000000000077a"),
                addressFromBytes(address.toArrayUnsafe()));
    }

    @Test
    void decodesExplicitRedirect() {
        final var redirectTarget =
                DescriptorUtils.getRedirectTarget(
                        Bytes.fromHexString(
                                "0x618dc65e000000000000000000000000000000000000000000000000000000000000043c0000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000002470a08231000000000000000000000000000000000000000000000000000000000000043b00000000000000000000000000000000000000000000000000000000"));

        assertEquals(ABI_ID_ERC_BALANCE_OF_TOKEN, redirectTarget.descriptor());
        assertEquals(
                Address.fromHexString("000000000000000000000000000000000000043c"),
                redirectTarget.token());
        assertEquals(
                Bytes.fromHexString(
                        "0x618dc65e000000000000000000000000000000000000043c70a08231000000000000000000000000000000000000000000000000000000000000043b"),
                redirectTarget.massagedInput());
    }
}
