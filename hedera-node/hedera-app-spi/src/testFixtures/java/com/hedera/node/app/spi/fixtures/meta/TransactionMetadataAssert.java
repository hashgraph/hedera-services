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

package com.hedera.node.app.spi.fixtures.meta;

import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Objects;
import org.assertj.core.api.AbstractAssert;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public class TransactionMetadataAssert extends AbstractAssert<TransactionMetadataAssert, TransactionMetadata> {

    public TransactionMetadataAssert(@Nullable TransactionMetadata metadata) {
        super(metadata, TransactionMetadataAssert.class);
    }

    @NonNull
    public static TransactionMetadataAssert assertThat(@Nullable TransactionMetadata actual) {
        return new TransactionMetadataAssert(actual);
    }

    @NonNull
    public TransactionMetadataAssert hasTxnBody(@Nullable TransactionBody txnBody) {
        isNotNull();
        if (!Objects.equals(actual.txnBody(), txnBody)) {
            failWithMessage("Expected metadata's txnBody to be <%s> but was <%s>", txnBody, actual.txnBody());
        }
        return this;
    }

    @NonNull
    public TransactionMetadataAssert hasPayer(@Nullable AccountID payer) {
        isNotNull();
        if (!Objects.equals(actual.payer(), payer)) {
            failWithMessage("Expected TransactionMetadata's payer to be <%s> but was <%s>", payer, actual.payer());
        }
        return this;
    }

    @NonNull
    public TransactionMetadataAssert hasStatus(@Nullable ResponseCodeEnum status) {
        isNotNull();
        if (actual.status() != status) {
            failWithMessage("Expected TransactionMetadata's status to be <%s> but was <%s>", status, actual.status());
        }
        return this;
    }

    @NonNull
    public TransactionMetadataAssert hasPayerKey(@Nullable HederaKey payerKey) {
        isNotNull();
        if (!Objects.equals(actual.payerKey(), payerKey)) {
            failWithMessage(
                    "Expected TransactionMetadata's payerKey to be <%s> but was <%s>", payerKey, actual.payerKey());
        }
        return this;
    }

    @NonNull
    public TransactionMetadataAssert hasRequiredNonPayerKeys(@Nullable List<HederaKey> requiredNonPayerKeys) {
        isNotNull();
        if (!Objects.equals(actual.requiredNonPayerKeys(), requiredNonPayerKeys)) {
            failWithMessage(
                    "Expected TransactionMetadata's requiredNonPayerKeys to be <%s> but was <%s>",
                    requiredNonPayerKeys, actual.requiredNonPayerKeys());
        }
        return this;
    }

    @NonNull
    public TransactionMetadataAssert hasHandlerMetadata(@Nullable Object handlerMetadata) {
        isNotNull();
        if (!Objects.equals(actual.handlerMetadata(), handlerMetadata)) {
            failWithMessage(
                    "Expected TransactionMetadata's handlerMetadata to be <%s> but was <%s>",
                    handlerMetadata, actual.handlerMetadata());
        }
        return this;
    }

    public TransactionMetadataAssert hasPayerSignature(
            @Nullable final TransactionSignature payerSignature) {
        isNotNull();
        if (!Objects.equals(actual.payerSignature(), payerSignature)) {
            failWithMessage(
                    "Expected TransactionMetadata's payerSignature to be <%s> but was <%s>",
                    payerSignature, actual.payerSignature());
        }
        return this;
    }

    public TransactionMetadataAssert hasOtherSignatures(
            @Nullable final List<TransactionSignature> otherSignature) {
        isNotNull();
        if (!Objects.equals(actual.otherSignatures(), otherSignature)) {
            failWithMessage(
                    "Expected TransactionMetadata's otherSignature to be <%s> but was <%s>",
                    otherSignature, actual.otherSignatures());
        }
        return this;
    }

    @NonNull
    public TransactionMetadataAssert hasReadKeys(@Nullable List<TransactionMetadata.ReadKeys> readKeys) {
        isNotNull();
        if (!Objects.equals(actual.readKeys(), readKeys)) {
            failWithMessage(
                    "Expected TransactionMetadata's readKeys to be <%s> but was <%s>", readKeys, actual.readKeys());
        }
        return this;
    }
}
