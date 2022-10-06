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

import static com.swirlds.common.crypto.VerificationStatus.INVALID;
import static com.swirlds.common.crypto.VerificationStatus.VALID;
import static java.util.stream.Collectors.toList;

import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import java.util.List;
import java.util.Map;

public class SigWrappers {
    public static List<TransactionSignature> asValid(List<TransactionSignature> sigs) {
        return sigs.stream().map(sig -> new SigWithKnownStatus(sig, VALID)).collect(toList());
    }

    public static List<TransactionSignature> asInvalid(List<TransactionSignature> sigs) {
        return sigs.stream().map(sig -> new SigWithKnownStatus(sig, INVALID)).collect(toList());
    }

    public static List<TransactionSignature> asKind(
            List<Map.Entry<TransactionSignature, VerificationStatus>> sigToStatus) {
        return sigToStatus.stream()
                .map(entry -> new SigWithKnownStatus(entry.getKey(), entry.getValue()))
                .collect(toList());
    }

    private static class SigWithKnownStatus extends TransactionSignature {
        private final VerificationStatus status;

        public SigWithKnownStatus(TransactionSignature wrapped, VerificationStatus status) {
            super(wrapped);
            this.status = status;
        }

        @Override
        public VerificationStatus getSignatureStatus() {
            return status;
        }
    }
}
