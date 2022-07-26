/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.legacy.core.jproto;

import com.swirlds.common.utility.CommonUtils;
import java.util.Arrays;

/** Maps to proto Key of type ed25519. */
public class JEd25519Key extends JKey {
    public static final int ED25519_BYTE_LENGTH = 32;

    private byte[] ed25519;

    public JEd25519Key(byte[] ed25519) {
        this.ed25519 = ed25519;
    }

    @Override
    public String toString() {
        return "<JEd25519Key: ed25519 hex=" + CommonUtils.hex(ed25519) + ">";
    }

    @Override
    public boolean isEmpty() {
        return ((null == ed25519) || (0 == ed25519.length));
    }

    @Override
    public byte[] getEd25519() {
        return ed25519;
    }

    @Override
    public boolean hasEd25519Key() {
        return true;
    }

    @Override
    public boolean isValid() {
        if (isEmpty()) {
            return false;
        } else {
            return ed25519.length == ED25519_BYTE_LENGTH;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || JEd25519Key.class != o.getClass()) {
            return false;
        }
        final var that = (JEd25519Key) o;
        return Arrays.equals(this.ed25519, that.ed25519);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ed25519);
    }
}
