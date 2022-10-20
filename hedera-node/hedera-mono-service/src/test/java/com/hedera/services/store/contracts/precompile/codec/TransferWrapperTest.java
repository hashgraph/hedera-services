/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.store.contracts.precompile.*;
import com.hedera.services.store.models.*;
import com.hederahashgraph.api.proto.java.*;
import java.util.*;
import org.junit.jupiter.api.*;

class TransferWrapperTest {

    @Test
    void translatesFungibleTransfersAsExpected() {
        final var inputTransfers = wellKnownTransfers();
        final var expectedAdjustments =
                TransferList.newBuilder()
                        .addAccountAmounts(aaWith(anAccount, aChange))
                        .addAccountAmounts(aaWith(otherAccount, bChange))
                        .addAccountAmounts(aaWith(anotherAccount, cChange))
                        .build();
        final var subject = new TransferWrapper(inputTransfers);

        final var builder = subject.asGrpcBuilder();
        assertEquals(expectedAdjustments, builder.build());
    }

    private AccountAmount aaWith(final AccountID account, final long amount) {
        return AccountAmount.newBuilder().setAccountID(account).setAmount(amount).build();
    }

    private List<SyntheticTxnFactory.HbarTransfer> wellKnownTransfers() {
        return List.of(
                new SyntheticTxnFactory.HbarTransfer(Math.abs(aChange), false, anAccount, null),
                new SyntheticTxnFactory.HbarTransfer(Math.abs(bChange), false, null, otherAccount),
                new SyntheticTxnFactory.HbarTransfer(
                        Math.abs(cChange), false, null, anotherAccount));
    }

    private final long aChange = -100L;
    private final long bChange = +75;
    private final long cChange = +25;
    private final AccountID anAccount = new Id(0, 0, 1234).asGrpcAccount();
    private final AccountID otherAccount = new Id(0, 0, 2345).asGrpcAccount();
    private final AccountID anotherAccount = new Id(0, 0, 3456).asGrpcAccount();
}
