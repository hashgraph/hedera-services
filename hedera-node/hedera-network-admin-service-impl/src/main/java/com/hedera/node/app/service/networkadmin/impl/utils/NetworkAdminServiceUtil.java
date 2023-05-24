package com.hedera.node.app.service.networkadmin.impl.utils;

import static java.lang.System.arraycopy;

import com.google.common.primitives.Longs;
import com.hedera.hapi.node.base.AccountID;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provides utility methods for network admin operations.
 */
public class NetworkAdminServiceUtil {

    /**
     * Function that converts Account ID to Hexed EVM address.
     * @param accountId
     * @return Returns string hexed EVM address for the given account ID.
     */
    @NonNull
    public static String asHexedEvmAddress(@NonNull final AccountID accountId) {
        return CommonUtils.hex(asEvmAddress(accountId.accountNum()));
    }

    @NonNull
    private static byte[] asEvmAddress(final long num) {
        final byte[] evmAddress = new byte[20];
        arraycopy(Longs.toByteArray(num), 0, evmAddress, 12, 8);
        return evmAddress;
    }
}
