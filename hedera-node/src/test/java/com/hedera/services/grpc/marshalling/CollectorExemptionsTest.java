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
package com.hedera.services.grpc.marshalling;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.fees.CollectorExemptions;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CollectorExemptionsTest {

    private CollectorExemptions subject;

    @BeforeEach
    void setUp() {
        subject = new CollectorExemptions();
    }

    @Test
    void collectorsHasPayerAndFixedExemptFee() {
        final var fixedFee = collector2FixedFeeExempt;
        final var customFeesMeta =
                newCustomFeeMeta(List.of(fixedFee, collector1FractionalFeeNotExempt));
        assertTrue(subject.isPayerExempt(customFeesMeta, fixedFee, collector2));
    }

    @Test
    void collectorsHasPayerAndFractionalExemptFee() {
        final var fractionalFee = collector2FractionalFeeExempt;
        final var customFeesMeta =
                newCustomFeeMeta(List.of(fractionalFee, newFixedFee(collector1, true)));
        assertTrue(subject.isPayerExempt(customFeesMeta, fractionalFee, collector2));
    }

    @Test
    void collectorsHasPayerAndRoyaltyExemptFee() {
        final var royaltyFee = collector1RoyalFixedFeeExempt;
        final var customFeesMeta =
                newCustomFeeMeta(List.of(royaltyFee, newFractionalFee(collector1, true)));
        assertTrue(subject.isPayerExempt(customFeesMeta, royaltyFee, collector1));
    }

    @Test
    void collectorsHasPayerAndFixedFeeNotExempt() {
        final var fixedFee = collector2FixedFeeNotExempt;
        final var customFeesMeta =
                newCustomFeeMeta(List.of(fixedFee, collector1FractionalFeeNotExempt));
        assertFalse(subject.isPayerExempt(customFeesMeta, fixedFee, collector2));
    }

    @Test
    void collectorsHasPayerAndFractionalFeeNotExempt() {
        final var fractionalFee = collector2FractionalFeeNotExempt;
        final var customFeesMeta =
                newCustomFeeMeta(List.of(fractionalFee, newFixedFee(collector1, false)));
        assertFalse(subject.isPayerExempt(customFeesMeta, fractionalFee, collector1));
    }

    @Test
    void collectorsHasPayerAndRoyaltyFeeNotExempt() {
        final var royaltyFee = collector1RoyaltyFeeNotExempt;
        final var customFeesMeta =
                newCustomFeeMeta(List.of(royaltyFee, collector1FractionalFeeNotExempt));
        assertFalse(subject.isPayerExempt(customFeesMeta, royaltyFee, collector2));
    }

    @Test
    void collectorsWithoutPayerAndFixedExemptFee() {
        final var fixedFee = collector2FixedFeeExempt;
        final var customFeesMeta =
                newCustomFeeMeta(List.of(fixedFee, collector2FractionalFeeExempt));
        assertFalse(subject.isPayerExempt(customFeesMeta, fixedFee, collector3));
    }

    @Test
    void collectorsWithoutPayerAndFractionalExemptFee() {
        final var fractionalFee = collector2FractionalFeeExempt;
        final var customFeesMeta =
                newCustomFeeMeta(List.of(fractionalFee, newFixedFee(collector1, true)));
        assertFalse(subject.isPayerExempt(customFeesMeta, fractionalFee, collector3));
    }

    @Test
    void collectorsWithoutPayerAndRoyaltyExemptFee() {
        final var royaltyFee = collector1RoyalFixedFeeExempt;
        final var customFeesMeta =
                newCustomFeeMeta(List.of(royaltyFee, collector2FractionalFeeExempt));
        assertFalse(subject.isPayerExempt(customFeesMeta, royaltyFee, collector3));
    }

    @Test
    void collectorsWithoutPayerAndFixedFeeNotExempt() {
        final var fixedFee = collector2FixedFeeNotExempt;
        final var customFeesMeta =
                newCustomFeeMeta(List.of(fixedFee, collector1FractionalFeeNotExempt));
        assertFalse(subject.isPayerExempt(customFeesMeta, fixedFee, collector3));
    }

    @Test
    void collectorsWithoutPayerAndFractionalFeeNotExempt() {
        final var fractionalFee = collector2FractionalFeeNotExempt;
        final var customFeesMeta =
                newCustomFeeMeta(List.of(fractionalFee, collector2FixedFeeNotExempt));
        assertFalse(subject.isPayerExempt(customFeesMeta, fractionalFee, collector3));
    }

    @Test
    void collectorsWithoutPayerAndRoyaltyFeeNotExempt() {
        final var royaltyFee = collector1RoyaltyFeeNotExempt;
        final var customFeesMeta =
                newCustomFeeMeta(List.of(royaltyFee, collector1FractionalFeeNotExempt));
        assertFalse(subject.isPayerExempt(customFeesMeta, royaltyFee, collector3));
    }

    private FcCustomFee newFixedFee(boolean exempt) {
        return newFixedFee(collector2, exempt);
    }

    private FcCustomFee newFixedFee(Id collector, boolean exempt) {
        return FcCustomFee.fixedFee(
                1, tokenDemonination.asEntityId(), collector.asEntityId(), exempt);
    }

    private FcCustomFee newFractionalFee(boolean exempt) {
        return newFractionalFee(collector2, exempt);
    }

    private FcCustomFee newFractionalFee(Id collector, boolean exempt) {
        return FcCustomFee.fractionalFee(1, 5, 1, 3, false, collector.asEntityId(), exempt);
    }

    private FcCustomFee newRoyaltyFee(boolean exempt) {
        return FcCustomFee.royaltyFee(1, 100, null, collector1.asEntityId(), exempt);
    }

    private CustomFeeMeta newCustomFeeMeta(List<FcCustomFee> customFees) {
        return new CustomFeeMeta(collector1, treasury, customFees);
    }

    private final Id collector1 = new Id(1, 2, 3);
    private final Id collector2 = new Id(2, 3, 4);
    private final Id collector3 = new Id(3, 4, 5);
    private final Id treasury = new Id(10, 20, 30);
    private final Id tokenDemonination = new Id(6, 6, 6);
    private final FcCustomFee collector1FractionalFeeNotExempt =
            newFractionalFee(collector1, false);
    private final FcCustomFee collector1RoyalFixedFeeExempt = newRoyaltyFee(true);
    private final FcCustomFee collector1RoyaltyFeeNotExempt = newRoyaltyFee(false);
    private final FcCustomFee collector2FixedFeeExempt = newFixedFee(true);
    private final FcCustomFee collector2FixedFeeNotExempt = newFixedFee(false);
    private final FcCustomFee collector2FractionalFeeExempt = newFractionalFee(true);
    private final FcCustomFee collector2FractionalFeeNotExempt = newFractionalFee(false);
}
