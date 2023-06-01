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

package com.hedera.node.app.service.contract.impl.test.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.node.app.spi.meta.bni.Dispatch;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversionUtilsTest {
    @Mock
    private Dispatch dispatch;

    @Test
    void convertsNumberToLongZeroAddress() {
        final var number = 0x1234L;
        final var expected = Address.fromHexString("0x1234");
        final var actual = ConversionUtils.asLongZeroAddress(number);
        assertEquals(expected, actual);
    }

    @Test
    void justReturnsNumberFromSmallLongZeroAddress() {
        final var smallNumber = 0x1234L;
        final var address = Address.fromHexString("0x1234");
        final var actual = ConversionUtils.maybeMissingNumberOf(address, dispatch);
        assertEquals(smallNumber, actual);
    }

    @Test
    void justReturnsNumberFromLargeLongZeroAddress() {
        final var largeNumber = 0x7fffffffffffffffL;
        final var address = Address.fromHexString("0x7fffffffffffffff");
        final var actual = ConversionUtils.maybeMissingNumberOf(address, dispatch);
        assertEquals(largeNumber, actual);
    }

    @Test
    void returnsZeroIfMissingAlias() {
        final var address = Address.fromHexString("0x010000000000000000");
        final var actual = ConversionUtils.maybeMissingNumberOf(address, dispatch);
        assertEquals(-1L, actual);
    }

    @Test
    void returnsGivenIfPresentAlias() {
        given(dispatch.resolveAlias(any())).willReturn(new EntityNumber(0x1234L));
        final var address = Address.fromHexString("0x010000000000000000");
        final var actual = ConversionUtils.maybeMissingNumberOf(address, dispatch);
        assertEquals(0x1234L, actual);
    }
}
