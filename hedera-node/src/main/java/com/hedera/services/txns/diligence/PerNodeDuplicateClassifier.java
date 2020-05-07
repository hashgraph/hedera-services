package com.hedera.services.txns.diligence;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionID;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.hedera.services.txns.diligence.DuplicateClassification.BELIEVED_UNIQUE;
import static com.hedera.services.txns.diligence.DuplicateClassification.DUPLICATE;
import static com.hedera.services.txns.diligence.DuplicateClassification.NODE_DUPLICATE;

/**
 * Implements a {@link NodeDuplicateClassifier} by maintaining a {@link DuplicateClassifier}
 * for each active submitting node.
 *
 * @author Michael Tinker
 */
public class PerNodeDuplicateClassifier implements NodeDuplicateClassifier {
	final Supplier<DuplicateClassifier> factory;
	final Map<AccountID, DuplicateClassifier> nodeClassifiers;

	public PerNodeDuplicateClassifier(
			Supplier<DuplicateClassifier> factory,
			Map<AccountID, DuplicateClassifier> nodeClassifiers
	) {
		this.factory = factory;
		this.nodeClassifiers = nodeClassifiers;
	}

	@Override
	public void observe(AccountID nodeSubmitting, TransactionID txnId, long at) {
		nodeClassifiers.computeIfAbsent(nodeSubmitting, ignore -> factory.get()).observe(txnId, at);
	}

	@Override
	public void shiftWindow(long to) {
		nodeClassifiers.values().forEach(classifier -> classifier.shiftWindow(to));
	}

	@Override
	public DuplicateClassification classify(AccountID nodeSubmitting, TransactionID txnId) {
		boolean isNodeDuplicate = Optional.ofNullable(nodeClassifiers.get(nodeSubmitting))
				.map(c -> c.isDuplicate(txnId))
				.orElse(false);
		if (isNodeDuplicate) {
			return NODE_DUPLICATE;
		} else {
			boolean isDuplicate = nodeClassifiers.keySet()
					.stream()
					.filter(id -> !id.equals(nodeSubmitting))
					.anyMatch(id -> nodeClassifiers.get(id).isDuplicate(txnId));

			return isDuplicate ? DUPLICATE : BELIEVED_UNIQUE;
		}
	}
}
