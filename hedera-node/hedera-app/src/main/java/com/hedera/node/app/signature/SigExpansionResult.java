package com.hedera.node.app.signature;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.List;

/**
 * Represents the result of attempting to expand a transaction's signature list.
 *
 * @param cryptoSigs the expanded list of crypto signatures
 * @param status the status of the expansion attempt
 */
public record SigExpansionResult(List<TransactionSignature> cryptoSigs, ResponseCodeEnum status) {
}
