/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.utils;

import java.util.Comparator;
import org.apache.tuweni.bytes.Bytes;

/** Compares Bytes as if they are overly large unsingned integers. */
public class BytesComparator implements Comparator<Bytes> {

    public static final BytesComparator INSTANCE = new BytesComparator();

    private BytesComparator() {
        // private to force singleton usage.
    }

    @Override
    public int compare(final Bytes b1, final Bytes b2) {
        final var result = nullCheck(b1, b2);
        if (result == 2) {
            return bytesCompare(b1, b2);
        }
        return result;
    }

    int nullCheck(Bytes b1, Bytes b2) {
        // null checks
        if (b1 == null) {
            return b2 == null ? 0 : 1;
        } else if (b2 == null) {
            return -1;
        }
        return 2;
    }

    int bytesCompare(Bytes b1, Bytes b2) {
        // size check - Longer is bigger
        int index = b1.size();
        int sizeCheck = Integer.compare(index, b2.size());
        if (sizeCheck != 0) {
            return sizeCheck;
        }

        // bytes check, big endian
        while (index > 0) {
            index--;
            int byteChcek = Integer.compare(b1.get(index), b2.get(index));
            if (byteChcek != 0) {
                return byteChcek;
            }
        }

        // must be equal
        return 0;
    }
}
