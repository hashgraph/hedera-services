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

package com.hedera.node.app.service.token.impl.util;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import edu.umd.cs.findbugs.annotations.NonNull;

public final class IdConvenienceUtils {

    private IdConvenienceUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Constructs an {@code AccountID} from the given account number
     *
     * @param accountNum the token number to construct a {@code AccountID} from
     * @return the constructed {@code AccountID}
     * @throws IllegalArgumentException if the given account number is not valid
     */
    @NonNull
    public static AccountID fromAccountNum(final long accountNum) {
        if (!isValidAccountNum(accountNum)) {
            throw new IllegalArgumentException("Account number must be positive");
        }
        return AccountID.newBuilder().accountNum(accountNum).build();
    }

    /**
     * Constructs a {@code TokenID} from the given token number
     *
     * @param tokenNum the token number to construct a {@code TokenID} from
     * @return the constructed {@code TokenID}
     * @throws IllegalArgumentException if the given token number is not valid
     */
    @NonNull
    public static TokenID fromTokenNum(final long tokenNum) {
        if (!isValidTokenNum(tokenNum)) {
            throw new IllegalArgumentException("Token number must be positive");
        }

        return TokenID.newBuilder().tokenNum(tokenNum).build();
    }

    /**
     * Determines if a given token number is valid
     *
     * @param tokenNum the token number to check
     * @return true if the token number is valid
     */
    public static boolean isValidTokenNum(final long tokenNum) {
        return tokenNum > 0;
    }

    /**
     * Determines if a given account number is valid
     *
     * @param accountNum the account number to check
     * @return true if the account number is valid
     */
    public static boolean isValidAccountNum(final long accountNum) {
        return accountNum > 0;
    }
}
