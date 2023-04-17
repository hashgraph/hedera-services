/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.config.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.service.mono.keys.LegacyContractIdActivations;
import java.util.Set;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class LegacyContractIdActivationsConverterTest {

    @Test
    void testNullValue() {
        //given
        final LegacyContractIdActivationsConverter converter = new LegacyContractIdActivationsConverter();

        //then
        assertThatThrownBy(() -> converter.convert(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testValidValue() {
        //given
        final LegacyContractIdActivationsConverter converter = new LegacyContractIdActivationsConverter();
        final String input = "1058134by[1062784]";

        //when
        final LegacyContractIdActivations contractIdActivations = converter.convert(input);

        //then
        assertThat(contractIdActivations).isNotNull();
        assertThat(contractIdActivations.privilegedContracts()).hasSize(1)
                .containsEntry(
                        Address.fromHexString("0x0000000000000000000000000000000000102556"),
                        Set.of(Address.fromHexString("0x0000000000000000000000000000000000103780")));
    }

    @Test
    void testInvalidValue() {
        //given
        final LegacyContractIdActivationsConverter converter = new LegacyContractIdActivationsConverter();

        //then
        assertThatThrownBy(() -> converter.convert("null"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}