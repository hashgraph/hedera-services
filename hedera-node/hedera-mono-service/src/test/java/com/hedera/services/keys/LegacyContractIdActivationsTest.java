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
package com.hedera.services.keys;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LegacyContractIdActivationsTest {
    @Test
    void parsesSimpleActivation() {
        final var spec = "1058134by[1062784]";

        final var subject = LegacyContractIdActivations.from(spec);

        final var accountAddr =
                EntityIdUtils.asTypedEvmAddress(
                        AccountID.newBuilder().setAccountNum(1058134L).build());
        final var missingAccountAddr =
                EntityIdUtils.asTypedEvmAddress(
                        AccountID.newBuilder().setAccountNum(2169245L).build());
        final var relaxedContractAddr =
                EntityIdUtils.asTypedEvmAddress(
                        AccountID.newBuilder().setAccountNum(1062784L).build());

        assertEquals(Set.of(relaxedContractAddr), subject.getLegacyActiveContractsFor(accountAddr));
        assertNull(subject.getLegacyActiveContractsFor(missingAccountAddr));
    }

    @Test
    void parsesMultipleActivations() {
        final var spec = "1058134by[1062784|2173895],857111by[522000|365365]";

        final var subject = LegacyContractIdActivations.from(spec);

        final var firstAccountAddr =
                EntityIdUtils.asTypedEvmAddress(
                        AccountID.newBuilder().setAccountNum(1058134L).build());
        final var secondAccountAddr =
                EntityIdUtils.asTypedEvmAddress(
                        AccountID.newBuilder().setAccountNum(857111L).build());
        final var firstContractAddr =
                EntityIdUtils.asTypedEvmAddress(
                        AccountID.newBuilder().setAccountNum(1062784L).build());
        final var secondContractAddr =
                EntityIdUtils.asTypedEvmAddress(
                        AccountID.newBuilder().setAccountNum(2173895L).build());
        final var thirdContractAddr =
                EntityIdUtils.asTypedEvmAddress(
                        AccountID.newBuilder().setAccountNum(522000L).build());
        final var fourthContractAddr =
                EntityIdUtils.asTypedEvmAddress(
                        AccountID.newBuilder().setAccountNum(365365L).build());

        assertEquals(
                Set.of(firstContractAddr, secondContractAddr),
                subject.getLegacyActiveContractsFor(firstAccountAddr));
        assertEquals(
                Set.of(thirdContractAddr, fourthContractAddr),
                subject.getLegacyActiveContractsFor(secondAccountAddr));
    }

    @Test
    void propagatesNumberFormat() {
        final var spec = "1058134by[106bd2784|2173895],857111by[522000|365365]";

        assertThrows(NumberFormatException.class, () -> LegacyContractIdActivations.from(spec));
    }

    @Test
    void propagatesUnmatchedAsIllegalArgumentException() {
        final var spec = "1058134by[106bd2784|2173895,857111by[522000|365365]";

        assertThrows(IllegalArgumentException.class, () -> LegacyContractIdActivations.from(spec));
    }
}
