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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.calc.OverflowCheckingCalc.tinycentsToTinybars;
import static com.hedera.services.store.contracts.precompile.ExchangeRatePrecompiledContract.TO_TINYBARS_SELECTOR;
import static com.hedera.services.store.contracts.precompile.ExchangeRatePrecompiledContract.TO_TINYCENTS_SELECTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

import com.google.common.primitives.Longs;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.HbarCentExchange;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import java.math.BigInteger;
import java.time.Instant;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeRatePrecompiledContractTest {
    @Mock private MessageFrame frame;
    @Mock private HbarCentExchange exchange;
    @Mock private GasCalculator gasCalculator;
    @Mock private GlobalDynamicProperties dynamicProperties;

    private ExchangeRatePrecompiledContract subject;

    @BeforeEach
    void setUp() {
        subject =
                new ExchangeRatePrecompiledContract(
                        gasCalculator, exchange, dynamicProperties, () -> now);
    }

    @Test
    void hasExpectedGasRequirement() {
        given(dynamicProperties.exchangeRateGasReq()).willReturn(GAS_REQUIREMENT);
        assertSame(GAS_REQUIREMENT, subject.gasRequirement(argOf(123)));
    }

    @Test
    void convertsPositiveNumberToTinybarsAsExpected() {
        givenRate(someRate);

        final var someInput = tinycentsInput(someTinycentAmount);
        final var result = subject.compute(someInput, frame);

        assertEquals(unpackedBytesFor(someTinybarAmount), result);
    }

    @Test
    void convertsPositiveNumberToTinycentsAsExpected() {
        givenRate(someRate);

        final var positiveInput = tinybarsInput(someTinybarAmount);
        final var result = subject.compute(positiveInput, frame);

        assertEquals(unpackedBytesFor(someTinycentAmount), result);
    }

    @Test
    void convertsZeroToTinybarsAsExpected() {
        givenRate(someRate);

        final var zeroInput = tinycentsInput(0);
        final var result = subject.compute(zeroInput, frame);

        assertEquals(unpackedBytesFor(0), result);
    }

    @Test
    void inputCannotUnderflow() {
        final var underflowInput =
                tinycentsInput(
                        Bytes.wrap(
                                BigInteger.valueOf(Long.MAX_VALUE)
                                        .multiply(BigInteger.TEN)
                                        .toByteArray()));

        assertNull(subject.compute(underflowInput, frame));
    }

    @Test
    void selectorMustBeFullyPresent() {
        final var fragmentSelector = Bytes.of(0xab);
        assertNull(subject.compute(fragmentSelector, frame));
    }

    @Test
    void selectorMustBeRecognized() {
        final var fragmentSelector = Bytes.of((byte) 0xab, (byte) 0xab, (byte) 0xab, (byte) 0xab);
        final var input = Bytes.concatenate(fragmentSelector, Bytes32.ZERO);
        assertNull(subject.compute(input, frame));
    }

    private static Bytes tinycentsInput(final long validAmount) {
        return input(
                TO_TINYBARS_SELECTOR, Bytes32.leftPad(Bytes.wrap(Longs.toByteArray(validAmount))));
    }

    private static Bytes tinybarsInput(final long validAmount) {
        return input(
                TO_TINYCENTS_SELECTOR, Bytes32.leftPad(Bytes.wrap(Longs.toByteArray(validAmount))));
    }

    private static Bytes tinycentsInput(final Bytes wordInput) {
        return input(TO_TINYBARS_SELECTOR, wordInput);
    }

    private static Bytes input(final int selector, final Bytes wordInput) {
        return Bytes.concatenate(Bytes.ofUnsignedInt(selector & 0xffffffffL), wordInput);
    }

    private static Bytes unpackedBytesFor(final long amount) {
        return unpackedBytesFor((byte) 0, amount);
    }

    private static Bytes unpackedBytesFor(final byte selector, final long amount) {
        final var word = new byte[32];
        final var bytes = Longs.toByteArray(amount);
        System.arraycopy(bytes, 0, word, 24, 8);
        if (selector != 0) {
            word[23] = selector;
        }
        return Bytes32.wrap(word);
    }

    private static Bytes argOf(final long amount) {
        return Bytes.wrap(Longs.toByteArray(amount));
    }

    private void givenRate(final ExchangeRate rate) {
        given(exchange.activeRate(now)).willReturn(rate);
    }

    private static final int someHbarEquiv = 120;
    private static final int someCentEquiv = 100;
    private static final int someTinycentAmount = 123_456_000;
    private static final ExchangeRate someRate =
            ExchangeRate.newBuilder()
                    .setHbarEquiv(someHbarEquiv)
                    .setCentEquiv(someCentEquiv)
                    .build();
    private static final long someTinybarAmount = tinycentsToTinybars(someTinycentAmount, someRate);
    private static final Instant now = Instant.ofEpochSecond(1_234_567, 890);
    private static final long GAS_REQUIREMENT = 100L;
}
