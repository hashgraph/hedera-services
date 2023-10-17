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
import static contract.XTestConstants.INVALID_ACCOUNT_HEADLONG_ADDRESS;
import static contract.XTestConstants.OWNER_HEADLONG_ADDRESS;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;

public class CreatesXTestConstants {
    static final long NEXT_ENTITY_NUM = 1004L;
    static final long INITIAL_TOTAL_SUPPLY = 10L;
    static final BigInteger INITIAL_TOTAL_SUPPLY_BIG_INT = BigInteger.valueOf(10L);
    static final int DECIMALS = 8;
    static final long DECIMALS_LONG = 8L;
    static final BigInteger DECIMALS_BIG_INT = BigInteger.valueOf(8L);
    static final String NAME = "name";
    static final String SYMBOL = "symbol";
    static final String MEMO = "memo";
    static final long MAX_SUPPLY = 1000L;
    static final long SECOND = 123L;
    static final long AUTO_RENEW_PERIOD = 2592000L;

    static final Tuple TOKEN_KEY =
            Tuple.of(BigInteger.valueOf(1), Tuple.of(true, asAddress(""), new byte[] {}, new byte[] {}, asAddress("")));

    static final Tuple TOKEN_KEY_TWO = Tuple.of(
            BigInteger.valueOf(80), Tuple.of(true, asAddress(""), new byte[] {}, new byte[] {}, asAddress("")));

    static final Tuple TOKEN_INVALID_KEY = Tuple.of(
            BigInteger.valueOf(1),
            Tuple.of(false, INVALID_ACCOUNT_HEADLONG_ADDRESS, new byte[] {}, new byte[] {}, asAddress("")));

    static final Tuple EXPIRY = Tuple.of(SECOND, OWNER_HEADLONG_ADDRESS, AUTO_RENEW_PERIOD);

    static final Tuple FIXED_FEE = Tuple.of(100L, ERC20_TOKEN_ADDRESS, false, false, OWNER_HEADLONG_ADDRESS);
    static final Tuple FRACTIONAL_FEE = Tuple.of(100L, 100L, 100L, 100L, true, OWNER_HEADLONG_ADDRESS);
    static final Tuple ROYALTY_FEE = Tuple.of(10L, 10L, 1L, ERC20_TOKEN_ADDRESS, false, OWNER_HEADLONG_ADDRESS);

    static final Tuple hederaTokenFactory(
            String name,
            String symbol,
            com.esaulpaugh.headlong.abi.Address treasury,
            String memo,
            boolean tokenSupplyType,
            long maxSupply,
            boolean freezeDefault,
            Tuple[] tokenKeys,
            Tuple expiry) {
        return Tuple.of(name, symbol, treasury, memo, tokenSupplyType, maxSupply, freezeDefault, tokenKeys, expiry);
    }

    // casts Address to null
    public static Address asAddress(String address) {
        final var addressBytes = Bytes.fromHexString(address.startsWith("0x") ? address : "0x" + address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }
}
