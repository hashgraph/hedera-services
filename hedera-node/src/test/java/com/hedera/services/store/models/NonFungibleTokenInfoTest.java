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
package com.hedera.services.store.models;

/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.recipientAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.services.store.contracts.precompile.codec.Expiry;
import com.hedera.services.store.contracts.precompile.codec.HederaToken;
import com.hedera.services.store.contracts.precompile.codec.NonFungibleTokenInfo;
import com.hedera.services.store.contracts.precompile.codec.TokenInfo;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class NonFungibleTokenInfoTest {

    @Test
    void hashCodeDiscriminates() {
        final var aNonFungibleTokenInfo =
                new NonFungibleTokenInfo(
                        null,
                        1,
                        senderAddress,
                        1434353562L,
                        new byte[] {40, -32, 56, 2, -8},
                        recipientAddr);
        final var bNonFungibleTokenInfo =
                new NonFungibleTokenInfo(
                        null,
                        2,
                        senderAddress,
                        43643743437L,
                        new byte[] {56, -32, 73, 2, -8},
                        recipientAddr);
        final var cNonFungibleTokenInfo =
                new NonFungibleTokenInfo(
                        null,
                        3,
                        senderAddress,
                        74643363L,
                        new byte[] {74, -32, 56, 2, -5},
                        recipientAddr);
        final var dNonFungibleTokenInfo =
                new NonFungibleTokenInfo(
                        null,
                        1,
                        senderAddress,
                        1434353562L,
                        new byte[] {40, -32, 56, 2, -8},
                        recipientAddr);

        assertNotEquals(bNonFungibleTokenInfo.hashCode(), aNonFungibleTokenInfo.hashCode());
        assertNotEquals(cNonFungibleTokenInfo.hashCode(), aNonFungibleTokenInfo.hashCode());
        assertEquals(dNonFungibleTokenInfo.hashCode(), aNonFungibleTokenInfo.hashCode());
    }

    @Test
    void equalsDiscriminates() {
        final var aNonFungibleTokenInfo =
                new NonFungibleTokenInfo(
                        createTokenInfo(),
                        1,
                        senderAddress,
                        1434353562L,
                        new byte[] {40, -32, 56, 2, -8},
                        recipientAddr);
        final var bNonFungibleTokenInfo =
                new NonFungibleTokenInfo(
                        createTokenInfo(),
                        2,
                        senderAddress,
                        43643743437L,
                        new byte[] {56, -32, 73, 2, -8},
                        recipientAddr);
        final var cNonFungibleTokenInfo =
                new NonFungibleTokenInfo(
                        createTokenInfo(),
                        3,
                        senderAddress,
                        74643363L,
                        new byte[] {74, -32, 56, 2, -5},
                        recipientAddr);
        final var dNonFungibleTokenInfo =
                new NonFungibleTokenInfo(
                        createTokenInfo(),
                        1,
                        senderAddress,
                        1434353562L,
                        new byte[] {40, -32, 56, 2, -8},
                        recipientAddr);

        assertNotEquals(bNonFungibleTokenInfo, aNonFungibleTokenInfo);
        assertNotEquals(cNonFungibleTokenInfo, aNonFungibleTokenInfo);
        assertEquals(dNonFungibleTokenInfo, aNonFungibleTokenInfo);
    }

    @Test
    void toStringWorks() {
        final var nonFungibleTokenInfo =
                new NonFungibleTokenInfo(
                        createTokenInfo(),
                        1,
                        senderAddress,
                        1434353562L,
                        new byte[] {40, -32, 56, 2, -8},
                        recipientAddr);

        assertEquals(
                "NonFungibleTokenInfo{tokenInfo=TokenInfo[token=HederaToken[name=Name,"
                    + " symbol=Symbol, treasury=0x0000000000000000000000000000000000000008,"
                    + " memo=Memo, tokenSupplyType=true, maxSupply=10000, freezeDefault=false,"
                    + " tokenKeys=[], expiry=Expiry[second=3253253234,"
                    + " autoRenewAccount=0x0000000000000000000000000000000000000008,"
                    + " autoRenewPeriod=10000000]], totalSupply=10, deleted=false,"
                    + " defaultKycStatus=false, pauseStatus=false, fixedFees=[], fractionalFees=[],"
                    + " royaltyFees=[], ledgerId=0x03], serialNumber="
                        + 1
                        + ", ownerId="
                        + senderAddress
                        + ", creationTime="
                        + 1434353562L
                        + ", metadata="
                        + Arrays.toString(new byte[] {40, -32, 56, 2, -8})
                        + ", spenderId="
                        + recipientAddr
                        + '}',
                nonFungibleTokenInfo.toString());
    }

    private TokenInfo createTokenInfo() {
        final HederaToken hederaToken =
                new HederaToken(
                        "Name",
                        "Symbol",
                        senderAddress,
                        "Memo",
                        true,
                        10000,
                        false,
                        new ArrayList<>(),
                        new Expiry(3253253234L, senderAddress, 10000000L));
        return new TokenInfo(
                hederaToken,
                10,
                false,
                false,
                false,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                "0x03");
    }
}
