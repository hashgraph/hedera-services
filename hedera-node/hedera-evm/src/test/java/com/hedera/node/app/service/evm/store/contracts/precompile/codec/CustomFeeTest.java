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
package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class CustomFeeTest {

    @Test
    void testFees() {
        final var payerAccount =
            Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000005ce"));

        RoyaltyFee royaltyFee =
            new RoyaltyFee(
                15,
                100,
                50,
                Address.wrap(
                    Bytes.fromHexString("0x00000000000000000000000000000000000005cb")),
                true,
                payerAccount);

        FixedFee fixedFeeInHbar = new FixedFee(100, null, true, false, payerAccount);

        FractionalFee fractionalFee = new FractionalFee(15, 100, 10, 50, false, payerAccount);

        assertNotEquals(royaltyFee, fixedFeeInHbar);
        assertNotEquals(fixedFeeInHbar, royaltyFee);
        assertNotEquals(fractionalFee, royaltyFee);

    }

    @Test
    void testCustomFee() {
        final var customfee = customFees();
        final var customfee2 = customFees();
        final var royaltyFeeNullValues = customFeeWithRoyaltyNullValues().get(0).getRoyaltyFee();
        final var fixedFeeNullValues = customFeesWithFixedNullValues().get(0).getFixedFee();
        final var fractionalFeeNullValues =
                customFeeWithFractionalNullValues().get(0).getFractionalFee();

        assertEquals(Address.ZERO, royaltyFeeNullValues.getFeeCollector());
        assertEquals(Address.ZERO, royaltyFeeNullValues.getDenominatingTokenId());
        assertEquals(Address.ZERO, fixedFeeNullValues.getFeeCollector());
        assertEquals(Address.ZERO, fractionalFeeNullValues.getFeeCollector());
        assertNotEquals(customFeeWithRoyalty(), customFeeWithRoyaltyDiff(14, 100, 50, true));
        assertNotEquals(customFeeWithRoyalty(), customFeeWithRoyaltyDiff(15, 90, 50, true));
        assertNotEquals(customFeeWithRoyalty(), customFeeWithRoyaltyDiff(15, 100, 45, true));
        assertNotEquals(customFeeWithRoyalty(), customFeeWithRoyaltyDiff(15, 100, 50, false));
        assertNotEquals(customFeesWithFixed(), customFeesWithFixedDiff(90, true, false));
        assertNotEquals(customFeesWithFixed(), customFeesWithFixedDiff(100, false, false));
        assertNotEquals(customFeesWithFixed(), customFeesWithFixedDiff(100, true, true));
        assertNotEquals(
                customFeeWithFractional(),
                customFeeWithFractionalDiff(
                        11, 100, 10, 50, false, "0x00000000000000000000000000000000000005ce"));
        assertNotEquals(
                customFeeWithFractional(),
                customFeeWithFractionalDiff(
                        15, 90, 10, 50, false, "0x00000000000000000000000000000000000005ce"));
        assertNotEquals(
                customFeeWithFractional(),
                customFeeWithFractionalDiff(
                        15, 100, 9, 50, false, "0x00000000000000000000000000000000000005ce"));
        assertNotEquals(
                customFeeWithFractional(),
                customFeeWithFractionalDiff(
                        15, 100, 10, 45, false, "0x00000000000000000000000000000000000005ce"));
        assertNotEquals(
                customFeeWithFractional(),
                customFeeWithFractionalDiff(
                        15, 100, 10, 50, true, "0x00000000000000000000000000000000000005ce"));
        assertNotEquals(
                customFeeWithFractional(),
                customFeeWithFractionalDiff(
                        15, 100, 10, 50, true, "0x00000000000000000000000000000000000005cd"));
        assertNotEquals(customFeesWithFixed(), customFeesWithRoyaltyAndFixed());
        assertNotEquals(customFeeWithFractionalAndFixed(), customFeesWithRoyaltyAndFixed());
        assertNotEquals(customFeesWithFixed(), customFeeWithFractional());
        assertEquals(customfee, customfee2);
        assertEquals(customfee.hashCode(), customfee2.hashCode());
    }

    private List<CustomFee> customFees() {
        final var payerAccount =
                Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000005ce"));
        FixedFee fixedFeeInHbar = new FixedFee(100, null, true, false, payerAccount);
        FixedFee fixedFeeInHts =
                new FixedFee(
                        100,
                        Address.wrap(
                                Bytes.fromHexString("0x00000000000000000000000000000000000005ca")),
                        false,
                        false,
                        payerAccount);
        FixedFee fixedFeeSameToken = new FixedFee(50, null, true, false, payerAccount);
        FractionalFee fractionalFee = new FractionalFee(15, 100, 10, 50, false, payerAccount);
        RoyaltyFee royaltyFee =
                new RoyaltyFee(
                        15,
                        100,
                        50,
                        Address.wrap(
                                Bytes.fromHexString("0x00000000000000000000000000000000000005cb")),
                        true,
                        payerAccount);

        CustomFee customFee1 =
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
        customFee1.setFixedFee(fixedFeeInHbar);
        CustomFee customFee2 =
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
        customFee2.setFixedFee(fixedFeeInHts);
        CustomFee customFee3 =
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
        customFee3.setFixedFee(fixedFeeSameToken);
        CustomFee customFee4 =
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
        customFee4.setFractionalFee(fractionalFee);
        CustomFee customFee5 = new CustomFee();
        customFee5.setRoyaltyFee(royaltyFee);

        return List.of(customFee1, customFee2, customFee3, customFee4, customFee5);
    }

    private List<CustomFee> customFeeWithFractionalAndFixed() {
        final var payerAccount =
                Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000005ce"));

        FractionalFee fractionalFee = new FractionalFee(15, 100, 10, 50, false, payerAccount);

        FixedFee fixedFeeInHbar = new FixedFee(100, null, true, false, payerAccount);

        CustomFee customFee = new CustomFee();
        customFee.setFractionalFee(fractionalFee);
        customFee.setFixedFee(fixedFeeInHbar);

        return List.of(customFee);
    }

    private List<CustomFee> customFeeWithFractional() {
        final var payerAccount =
                Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000005ce"));

        FractionalFee fractionalFee = new FractionalFee(15, 100, 10, 50, false, payerAccount);

        CustomFee customFee = new CustomFee();
        customFee.setFractionalFee(fractionalFee);

        return List.of(customFee);
    }

    private List<CustomFee> customFeeWithFractionalNullValues() {
        final var payerAccount =
                Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000005ce"));

        FractionalFee fractionalFee = new FractionalFee(15, 100, 10, 50, false, null);

        CustomFee customFee = new CustomFee();
        customFee.setFractionalFee(fractionalFee);

        return List.of(customFee);
    }

    private List<CustomFee> customFeeWithFractionalDiff(
            long numerator,
            long denominator,
            long getMinimumAmount,
            long getMaximumAmount,
            boolean netOfTransfers,
            String payer) {
        final var payerAccount = Address.wrap(Bytes.fromHexString(payer));

        FractionalFee fractionalFee =
                new FractionalFee(
                        numerator,
                        denominator,
                        getMinimumAmount,
                        getMaximumAmount,
                        netOfTransfers,
                        payerAccount);

        CustomFee customFee = new CustomFee();
        customFee.setFractionalFee(fractionalFee);

        return List.of(customFee);
    }

    private List<CustomFee> customFeesWithFixed() {
        final var payerAccount =
                Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000005ce"));
        FixedFee fixedFeeInHbar = new FixedFee(100, null, true, false, payerAccount);

        CustomFee customFee1 =
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
        customFee1.setFixedFee(fixedFeeInHbar);

        return List.of(customFee1);
    }

    private List<CustomFee> customFeesWithFixedNullValues() {
        final var payerAccount =
                Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000005ce"));
        FixedFee fixedFeeInHbar = new FixedFee(100, null, true, false, null);

        CustomFee customFee1 =
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
        customFee1.setFixedFee(fixedFeeInHbar);

        return List.of(customFee1);
    }

    private List<CustomFee> customFeesWithFixedDiff(
            long amount, boolean useHbarsForPayment, boolean useCurrentTokenForPayment) {
        final var payerAccount =
                Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000005ce"));
        FixedFee fixedFeeInHbar =
                new FixedFee(
                        amount, null, useHbarsForPayment, useCurrentTokenForPayment, payerAccount);

        CustomFee customFee1 =
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
        customFee1.setFixedFee(fixedFeeInHbar);

        return List.of(customFee1);
    }

    private List<CustomFee> customFeeWithRoyalty() {
        final var payerAccount =
                Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000005ce"));

        RoyaltyFee royaltyFee =
                new RoyaltyFee(
                        15,
                        100,
                        50,
                        Address.wrap(
                                Bytes.fromHexString("0x00000000000000000000000000000000000005cb")),
                        true,
                        payerAccount);

        CustomFee customFee = new CustomFee();

        customFee.setRoyaltyFee(royaltyFee);

        return List.of(customFee);
    }

    private List<CustomFee> customFeeWithRoyaltyNullValues() {
        final var payerAccount =
                Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000005ce"));

        RoyaltyFee royaltyFee = new RoyaltyFee(15, 100, 50, null, true, null);

        CustomFee customFee = new CustomFee();

        customFee.setRoyaltyFee(royaltyFee);

        return List.of(customFee);
    }

    private List<CustomFee> customFeeWithRoyaltyDiff(
            long numerator, long denominator, long amount, boolean useHbarsForPayment) {
        final var payerAccount =
                Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000005ce"));

        RoyaltyFee royaltyFee =
                new RoyaltyFee(
                        numerator,
                        denominator,
                        amount,
                        Address.wrap(
                                Bytes.fromHexString("0x00000000000000000000000000000000000005cb")),
                        useHbarsForPayment,
                        payerAccount);

        CustomFee customFee = new CustomFee();

        customFee.setRoyaltyFee(royaltyFee);

        return List.of(customFee);
    }

    private List<CustomFee> customFeesWithRoyaltyAndFixed() {
        final var payerAccount =
                Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000005ce"));

        RoyaltyFee royaltyFee =
                new RoyaltyFee(
                        15,
                        100,
                        50,
                        Address.wrap(
                                Bytes.fromHexString("0x00000000000000000000000000000000000005cb")),
                        true,
                        payerAccount);

        FixedFee fixedFeeInHbar = new FixedFee(100, null, true, false, payerAccount);
        ;

        CustomFee customFee5 = new CustomFee();
        customFee5.setRoyaltyFee(royaltyFee);
        customFee5.setFixedFee(fixedFeeInHbar);

        return List.of(customFee5);
    }
}
