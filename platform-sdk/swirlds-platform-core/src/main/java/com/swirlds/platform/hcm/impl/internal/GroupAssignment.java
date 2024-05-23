/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.hcm.impl.internal;

import com.swirlds.platform.hcm.api.pairings.BilinearPairing;
import com.swirlds.platform.hcm.api.pairings.Group;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;

public enum GroupAssignment {
    GROUP1_FOR_SIGNING(BilinearPairing::getGroup1, BilinearPairing::getGroup2),
    GROUP1_FOR_PUBLIC_KEY(BilinearPairing::getGroup2, BilinearPairing::getGroup1);

    private final Function<BilinearPairing, Group> signatureGroup;
    private final Function<BilinearPairing, Group> publicKeyGroup;

    GroupAssignment(Function<BilinearPairing, Group> signatureGroup, Function<BilinearPairing, Group> publicKeyGroup) {
        this.signatureGroup = signatureGroup;
        this.publicKeyGroup = publicKeyGroup;
    }

    @NonNull
    public Group getSignatureGroupFor(@NonNull final BilinearPairing pairing) {
        return signatureGroup.apply(pairing);
    }

    @NonNull
    public Group getPublicKeyGroupFor(@NonNull final BilinearPairing pairing) {
        return publicKeyGroup.apply(pairing);
    }
}
