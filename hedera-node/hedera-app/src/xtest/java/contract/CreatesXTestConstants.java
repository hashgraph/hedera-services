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

package contract;

import static contract.XTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.OWNER_HEADLONG_ADDRESS;
import static contract.XTestConstants.RECEIVER_HEADLONG_ADDRESS;

import com.esaulpaugh.headlong.abi.Tuple;
import java.math.BigInteger;

public class CreatesXTestConstants {
    static final long NEXT_ENTITY_NUM = 1004L;
    static final long INITIAL_TOTAL_SUPPLY = 10L;
    static final int DECIMALS = 8;
    static final String NAME = "name";
    static final String SYMBOL = "symbol";
    static final String MEMO = "memo";
    static final long MAX_SUPPLY = 1000L;
    static final int KEY_TYPE = 112;
    static final long SECOND = 123L;
    static final long AUTO_RENEW_PERIOD = 2592000L;

    static final Tuple TOKEN_KEY = Tuple.of(
            BigInteger.valueOf(KEY_TYPE),
            Tuple.of(false, RECEIVER_HEADLONG_ADDRESS, new byte[] {}, new byte[] {}, RECEIVER_HEADLONG_ADDRESS));

    static final Tuple EXPIRY = Tuple.of(SECOND, OWNER_HEADLONG_ADDRESS, AUTO_RENEW_PERIOD);

    static final Tuple HEDERA_TOKEN_STRUCT = Tuple.of(
            NAME,
            SYMBOL,
            OWNER_HEADLONG_ADDRESS,
            MEMO,
            true,
            MAX_SUPPLY,
            false,
            // TokenKey
            new Tuple[] {TOKEN_KEY},
            // Expiry
            EXPIRY);

    static final Tuple FIXED_FEE = Tuple.of(100L, ERC20_TOKEN_ADDRESS, false, false, OWNER_HEADLONG_ADDRESS);
    static final Tuple FRACTIONAL_FEE = Tuple.of(100L, 100L, 100L, 100L, true, OWNER_HEADLONG_ADDRESS);

    static final Tuple ROYALTY_FEE = Tuple.of(10L, 10L, 1L, ERC20_TOKEN_ADDRESS, false, OWNER_HEADLONG_ADDRESS);
}
