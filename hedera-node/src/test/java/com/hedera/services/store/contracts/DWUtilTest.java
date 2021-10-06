package com.hedera.services.store.contracts;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import org.apache.tuweni.units.bigints.UInt256;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DWUtilTest {
    @Test
    void fromUInt256() {
        var argument = UInt256.valueOf(10L);

        var expecting = DataWord.of(argument.toArray());

        Assertions.assertEquals(expecting, DWUtil.fromUInt256(argument));
    }

    @Test
    void fromDataWord() {
        var expecting = UInt256.valueOf(10L);

        var argument = DataWord.of(expecting.toArray());

        Assertions.assertEquals(expecting, DWUtil.fromDataWord(argument));
    }
}
