package com.hedera.services.sigs.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.exception.KeySignatureCountMismatchException;
import com.hedera.services.legacy.exception.KeySignatureTypeMismatchException;
import com.hederahashgraph.api.proto.java.Signature;

import java.util.List;
import java.util.function.Predicate;

/**
 * Contains static helpers used during precheck to validate signatures.
 *
 * @author Michael Tinker
 */
public class PrecheckUtils {
	private static final String KEYSIGNATURE_COUNT_MISMATCH = "Incompatible key/sig shapes!";

	private PrecheckUtils(){
		throw new IllegalStateException("Utility Class");
	}
	/**
	 * Constructs a predicate testing whether a {@link TransactionBody} should be
	 * considered a query payment for the given node.
	 *
	 * @param deservingNode the id of a node's account.
	 * @return a predicate testing if a txn is a query payment for the given node.
	 */
	public static Predicate<TransactionBody> queryPaymentTestFor(AccountID deservingNode) {
		return txn ->
			txn.hasCryptoTransfer() &&
					txn.getCryptoTransfer().getTransfers().getAccountAmountsList().stream()
							.filter(aa -> aa.getAmount() > 0)
							.map(AccountAmount::getAccountID)
							.anyMatch(deservingNode::equals);
	}

	/**
	 * Throws an exception if the given {@link JKey} and {@link Signature} lists
	 * do not have the same "shape"; i.e. the same hierarchies formed of thresholds,
	 * lists, and simple constituents.
	 *
	 * @param keys a list of keys.
	 * @param sigs a list of legacy signatures.
	 * @throws Exception if the arguments do not have the same shape.
	 */
	public static void assertCompatibility(List<JKey> keys, List<Signature> sigs) throws Exception {
		if (keys.size() != sigs.size())	{
			throw new KeySignatureCountMismatchException(KEYSIGNATURE_COUNT_MISMATCH);
		}
		for (int i = 0 ; i < keys.size(); i++) {
			assertCompatibility(keys.get(i), sigs.get(i));
		}
	}

	private static void assertCompatibility(JKey key, Signature sig) throws Exception {
		if (isSimpleKey(key)) {
			if (!isSimpleSig(sig)) {
				throw new KeySignatureTypeMismatchException(KEYSIGNATURE_COUNT_MISMATCH);
			}
		} else if (key.hasKeyList()) {
			if (!sig.hasSignatureList()) {
				throw new KeySignatureTypeMismatchException(KEYSIGNATURE_COUNT_MISMATCH);
			}
			assertCompatibility(key.getKeyList().getKeysList(), sig.getSignatureList().getSigsList());
		} else {
			if (!sig.hasThresholdSignature()) {
				throw new KeySignatureTypeMismatchException(KEYSIGNATURE_COUNT_MISMATCH);
			}
			assertCompatibility(
					key.getThresholdKey().getKeys().getKeysList(),
					sig.getThresholdSignature().getSigs().getSigsList());
		}
	}

	private static boolean isSimpleKey(JKey key) {
		return !key.hasThresholdKey() && !key.hasKeyList();
	}

	private static boolean isSimpleSig(Signature sig) {
		return !sig.hasThresholdSignature() && !sig.hasSignatureList();
	}
}
