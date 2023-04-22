/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.signature.hapi;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains the results of the signature verification process for a single transaction. This includes verifying that
 * the payer signature, non-payer signatures, and hollow account signatures are all valid.
 */
public class SignatureVerificationResults {
    private final SignatureVerification payerSignatureVerification;
    private final List<SignatureVerification> nonPayerSignatureVerifications;
    private final List<SignatureVerification> hollowAccountSignatureVerifications;
    private final Map<Key, SignatureVerification> nonPayerSignatureVerificationsMap = new HashMap<>();
    private final Map<Long, SignatureVerification> hollowSignatureVerificationsMap = new HashMap<>();

    public SignatureVerificationResults(
            @NonNull final SignatureVerification payerSignatureVerification,
            @NonNull final List<SignatureVerification> nonPayerSignatureVerifications,
            @NonNull final List<SignatureVerification> hollowAccountSignatureVerifications,
            final boolean passed) {
        this.payerSignatureVerification = payerSignatureVerification;
        this.nonPayerSignatureVerifications = nonPayerSignatureVerifications;
        this.hollowAccountSignatureVerifications = hollowAccountSignatureVerifications;

        for (final var foo : nonPayerSignatureVerifications) {
            nonPayerSignatureVerificationsMap.put(foo.key(), foo);
        }

        for (final var foo : hollowAccountSignatureVerifications) {
            hollowSignatureVerificationsMap.put(foo.hollowAccount().accountNumber(), foo);
        }
    }

    public SignatureVerification getPayerSignatureVerification() {
        return payerSignatureVerification;
    }

    public List<SignatureVerification> getNonPayerSignatureVerifications() {
        return nonPayerSignatureVerifications;
    }

    public List<SignatureVerification> getHollowAccountSignatureVerifications() {
        return hollowAccountSignatureVerifications;
    }
}
