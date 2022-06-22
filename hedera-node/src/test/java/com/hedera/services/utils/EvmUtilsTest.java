package com.hedera.services.utils;

import org.junit.jupiter.api.Test;

import static com.hedera.services.utils.EvmUtils.perm64;
import static com.hedera.services.utils.EvmUtils.readableTransferList;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EvmUtilsTest {

    @Test
    void prettyPrintsTransferList() {
        final var transfers = withAdjustments(
                asAccount("0.1.2"), 500L,
                asAccount("1.0.2"), -250L,
                asAccount("1.2.0"), Long.MIN_VALUE);

        final var s = readableTransferList(transfers);

        assertEquals("[0.1.2 <- +500, 1.0.2 -> -250, 1.2.0 -> -9223372036854775808]", s);
    }

    @Test
    void perm64Test() {
        assertEquals(0L, perm64(0L));
        assertEquals(-4328535976359616544L, perm64(1L));
        assertEquals(2657016865369639288L, perm64(7L));
    }
}
