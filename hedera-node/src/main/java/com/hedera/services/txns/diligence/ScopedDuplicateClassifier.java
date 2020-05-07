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

/**
 * Defines a type able to perform duplicate classification within
 * the context of a well-known transaction.
 *
 * @author Michael Tinker
 */
public interface ScopedDuplicateClassifier {
	/**
	 * Take the opportunity to update internal data structures based
	 * on the new consensus time. (Note that this method will probably
	 * be invoked as a callback added to a global data-driven clock
	 * at some point.)
	 */
	void shiftDetectionWindow();

	/**
	 * Provide the {@link DuplicateClassification} believed to obtain
	 * for the transaction currently being processed.
	 *
	 * @return the duplicity
	 */
	DuplicateClassification duplicityOfActiveTxn();

	/**
	 * Triggers the classifier's official recognition of the active
	 * transaction as a consensus event which should be considered
	 * for its implications on duplicate classification.
	 */
	void incorporateCommitment();
}
