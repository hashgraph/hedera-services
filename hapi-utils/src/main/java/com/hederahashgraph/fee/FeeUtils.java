/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hederahashgraph.fee;

import static com.hedera.services.legacy.proto.utils.CommonUtils.productWouldOverflow;

public class FeeUtils {
    public static long cappedMultiplication(final long a, final long b) {
        if (productWouldOverflow(a, b)) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }

    public static long cappedAddition(final long a, final long b) {
        final var nominal = a + b;
        return nominal >= 0 ? nominal : Long.MAX_VALUE;
    }
}
