/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.workflows.onset;

import static java.util.Objects.requireNonNull;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Results of the workflow onset.
 *
 * <p>This is used in every workflow that deals with transactions, i.e. in all workflows except the
 * query workflow. And even in the query workflow, it is used when dealing with the contained {@link
 * com.hederahashgraph.api.proto.java.CryptoTransfer}.
 *
 * @param txBody the deserialized {@link TransactionBody}
 * @param signatureMap the contained {@link SignatureMap}
 * @param functionality the {@link HederaFunctionality} of the transaction
 */
public record OnsetResult(
        @NonNull TransactionBody txBody,
        @NonNull byte[] bodyBytes,
        @NonNull ResponseCodeEnum errorCode,
        @NonNull SignatureMap signatureMap,
        @NonNull HederaFunctionality functionality) {

    /**
     * The constructor of {@code OnsetResult}
     *
     * @param txBody the deserialized {@link TransactionBody}
     * @param bodyBytes the raw byte-array that contains the body
     * @param errorCode the {@link ResponseCodeEnum}, if a validation failed, {@link
     *     ResponseCodeEnum#OK} otherwise
     * @param signatureMap the contained {@link SignatureMap}
     * @param functionality the {@link HederaFunctionality} of the transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public OnsetResult(
            @NonNull final TransactionBody txBody,
            @NonNull final byte[] bodyBytes,
            @NonNull final ResponseCodeEnum errorCode,
            @NonNull final SignatureMap signatureMap,
            @NonNull final HederaFunctionality functionality) {
        this.txBody = requireNonNull(txBody);
        this.bodyBytes = requireNonNull(bodyBytes);
        this.errorCode = requireNonNull(errorCode);
        this.signatureMap = requireNonNull(signatureMap);
        this.functionality = requireNonNull(functionality);
    }
}
