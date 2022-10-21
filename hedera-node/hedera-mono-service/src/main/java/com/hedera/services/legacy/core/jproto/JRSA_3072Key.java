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

/** Maps to proto Key of type RSA_3072. */
public class JRSA_3072Key extends JKey {
    private byte[] rsa3072;

    public JRSA_3072Key(byte[] rsa3072) {
        this.rsa3072 = rsa3072;
    }

    @Override
    public String toString() {
        return "<JRSA_3072Key: rsa3072Key hex=" + CommonUtils.hex(rsa3072) + ">";
    }

    @Override
    public boolean hasRSA3072Key() {
        return true;
    }

    @Override
    public byte[] getRSA3072() {
        return rsa3072;
    }

    @Override
    public boolean isEmpty() {
        return ((null == rsa3072) || (0 == rsa3072.length));
    }

    @Override
    public boolean isValid() {
        return !isEmpty();
    }
}
