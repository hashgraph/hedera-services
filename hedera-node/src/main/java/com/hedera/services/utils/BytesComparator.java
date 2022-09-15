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
        if (b1 == null) {
            return b2 == null ? 0 : 1;
        } else if (b2 == null) {
            return -1;
        }
        return b1.compareTo(b2);
    }
}
