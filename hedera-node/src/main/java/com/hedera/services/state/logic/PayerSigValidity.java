package com.hedera.services.state.logic;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.utils.TxnAccessor;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.function.BiPredicate;

@FunctionalInterface
public interface PayerSigValidity {
	boolean test(TxnAccessor accessor, BiPredicate<JKey, TransactionSignature> test);
}
