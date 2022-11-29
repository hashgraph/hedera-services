package com.hedera.node.app.workflows.onset;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Results of the workflow onset
 *
 * @param txBody the deserialized {@link TransactionBody}
 * @param signatureMap the contained {@link SignatureMap}
 * @param functionality the {@link HederaFunctionality} of the transaction
 */
public record OnsetResult(
        @Nonnull TransactionBody txBody,
        @Nonnull SignatureMap signatureMap,
        @Nonnull HederaFunctionality functionality) {

    /**
     * The constructor of {@code OnsetResult}
     *
     * @param txBody the deserialized {@link TransactionBody}
     * @param signatureMap the contained {@link SignatureMap}
     * @param functionality the {@link HederaFunctionality} of the transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public OnsetResult(
            @Nonnull final TransactionBody txBody,
            @Nonnull final SignatureMap signatureMap,
            @Nonnull final HederaFunctionality functionality) {
        this.txBody = requireNonNull(txBody);
        this.signatureMap = requireNonNull(signatureMap);
        this.functionality = requireNonNull(functionality);
    }
}
