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
package com.hedera.evm.exception;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

public class ValidationUtils {
    private ValidationUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static void validateTrue(final boolean flag, final ResponseCodeEnum code) {
        if (!flag) {
            throw new InvalidTransactionException(code);
        }
    }
}
