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

public class JECDSA_384Key extends JKey {
    private byte[] ecdsa384;

    public JECDSA_384Key(byte[] ecdsa384) {
        this.ecdsa384 = ecdsa384;
    }

    @Override
    public String toString() {
        return "<JECDSA_384Key: ecdsa384Key hex=" + CommonUtils.hex(ecdsa384) + ">";
    }

    @Override
    public boolean isEmpty() {
        return ((null == ecdsa384) || (0 == ecdsa384.length));
    }

    @Override
    public byte[] getECDSA384() {
        return ecdsa384;
    }

    @Override
    public boolean isValid() {
        return !isEmpty();
    }

    @Override
    public boolean hasECDSA384Key() {
        return true;
    }
}
