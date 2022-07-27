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
package com.hedera.test.factories.keys;

import com.swirlds.common.crypto.SignatureType;

public class LeafFactory implements NodeFactory {
    private final String label;
    private final boolean usedToSign;
    private final SignatureType sigType;

    public LeafFactory(String label, boolean usedToSign, SignatureType sigType) {
        this.label = label;
        this.sigType = sigType;
        this.usedToSign = usedToSign;
    }

    public static final LeafFactory DEFAULT_FACTORY =
            new LeafFactory(null, true, SignatureType.ED25519);

    public String getLabel() {
        return label;
    }

    public boolean isUsedToSign() {
        return usedToSign;
    }

    public SignatureType getSigType() {
        return sigType;
    }
}
