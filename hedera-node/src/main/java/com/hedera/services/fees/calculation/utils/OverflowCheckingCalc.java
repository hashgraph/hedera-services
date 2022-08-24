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
package com.hedera.services.fees.calculation.utils;

public class OverflowCheckingCalc {
    public OverflowCheckingCalc() {
        /* no-op */
    }

    public static long clampedAdd(final long a, final long b) {
        try {
            return Math.addExact(a, b);
        } catch (final ArithmeticException ae) {
            return a > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    public static long clampedMultiply(final long a, final long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (final ArithmeticException ae) {
            return ((a ^ b) < 0) ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
    }
}
