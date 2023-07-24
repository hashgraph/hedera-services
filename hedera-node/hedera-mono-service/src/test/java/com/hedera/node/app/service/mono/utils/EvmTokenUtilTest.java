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

package com.hedera.node.app.service.mono.utils;

import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import java.util.ArrayList;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class EvmTokenUtilTest {
    @Test
    void includesNonZeroFractionalFeesWithoutMinimumAmount() {
        final var fracNoMinFee = CustomFee.newBuilder()
                .setFractionalFee(FractionalFee.newBuilder()
                        .setFractionalAmount(Fraction.newBuilder()
                                .setNumerator(1)
                                .setDenominator(2)
                                .build())
                        .build())
                .setFeeCollectorAccountId(AccountID.newBuilder().setAccountNum(0x1234))
                .build();
        final List<com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee> accum =
                new ArrayList<>();
        EvmTokenUtil.extractFees(fracNoMinFee, accum);
        assertEquals(1, accum.size());
        final var expectedEvmFee = new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
        expectedEvmFee.setFractionalFee(
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.FractionalFee(
                        1, 2, 0, 0, false, Address.fromHexString("0x1234")));
        assertEquals(expectedEvmFee, accum.get(0));
    }
}
