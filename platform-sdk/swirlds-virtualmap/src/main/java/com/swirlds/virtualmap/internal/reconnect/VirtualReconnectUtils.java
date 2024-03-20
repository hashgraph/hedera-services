package com.swirlds.virtualmap.internal.reconnect;

import java.io.IOException;
import java.io.InputStream;

public class VirtualReconnectUtils {

    public static int completelyRead(final InputStream in, final byte[] dst) throws IOException {
        int totalBytesRead = 0;
        while (totalBytesRead < dst.length) {
            final int bytesRead = in.read(dst, totalBytesRead, dst.length - totalBytesRead);
            if (bytesRead < 0) {
                // Reached EOF
                break;
            }
            totalBytesRead += bytesRead;
        }
        return totalBytesRead;
    }
}
