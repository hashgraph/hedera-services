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
package com.hedera.services.sigs.verification;

import com.swirlds.common.crypto.TransactionSignature;
import java.util.List;

/**
 * Defines a type able to synchronously verify {@link
 * com.swirlds.common.crypto.TransactionSignature} instances. (In particular, able to resolve their
 * {@link TransactionSignature#getSignatureStatus()} accessor to a value other than {@link
 * com.swirlds.common.crypto.VerificationStatus}.)
 */
public interface SyncVerifier {
    /**
     * Synchronously verify a list of {@link TransactionSignature} objects <b>in-place</b>.
     *
     * @param unknownSigs the sigs to verify.
     */
    void verifySync(List<TransactionSignature> unknownSigs);
}
