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
package com.hedera.test.factories.sigs;

import static com.hedera.test.factories.sigs.SigWrappers.asInvalid;
import static com.hedera.test.factories.sigs.SigWrappers.asValid;

import com.hedera.services.sigs.verification.SyncVerifier;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.List;

public class SyncVerifiers {
    public static final SyncVerifier NEVER_VALID =
            l -> {
                List<TransactionSignature> lv = asInvalid(l);
                l.clear();
                l.addAll(lv);
            };

    public static final SyncVerifier ALWAYS_VALID =
            l -> {
                List<TransactionSignature> lv = asValid(l);
                l.clear();
                l.addAll(lv);
            };
}
