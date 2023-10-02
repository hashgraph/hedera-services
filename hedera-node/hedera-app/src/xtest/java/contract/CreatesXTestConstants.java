package contract;

import static contract.XTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.OWNER_HEADLONG_ADDRESS;
import static contract.XTestConstants.RECEIVER_HEADLONG_ADDRESS;

import com.esaulpaugh.headlong.abi.Tuple;
import java.math.BigInteger;

public class CreatesXTestConstants {

     static final long INITIAL_TOTAL_SUPPLY = 10L;
     static final int DECIMALS = 8;
     static final String NAME = "name";
     static final String SYMBOL = "symbol";
     static final String MEMO = "memo";
     static final long MAX_SUPPLY = 1000L;
     static final long KEY_TYPE = 1L;
     static final long SECOND = 123L;
     static final long AUTO_RENEW_PERIOD = 2592000L;

     static final Tuple HEDERA_TOKEN_STRUCT = Tuple.of(
            NAME,
            SYMBOL,
            OWNER_HEADLONG_ADDRESS,
            MEMO,
            true,
            MAX_SUPPLY,
            false,
            // TokenKey
            new Tuple[] {
                    Tuple.of(
                            BigInteger.valueOf(KEY_TYPE),
                            Tuple.of(
                                    true,
                                    RECEIVER_HEADLONG_ADDRESS,
                                    new byte[] {},
                                    new byte[] {},
                                    RECEIVER_HEADLONG_ADDRESS))
            },
            // Expiry
            Tuple.of(SECOND, OWNER_HEADLONG_ADDRESS, AUTO_RENEW_PERIOD));

     static final Tuple FIXED_FEE = Tuple.of(100L, ERC20_TOKEN_ADDRESS, false, false, OWNER_HEADLONG_ADDRESS);
     static final Tuple FRACTIONAL_FEE = Tuple.of(100L, 100L, 100L, 100L, true, OWNER_HEADLONG_ADDRESS);

}
