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

/**
 * Defines a type able to distinguish between <b>node-duplicate transactions</b>
 * and <b>duplicate transactions</b> within a given window. (In particular, a
 * node-duplicate transaction is a transaction whose {@link TransactionID}
 * already appeared in the window from the same submitting node.)
 *
 * @author Michael Tinker
 */
public interface NodeDuplicateClassifier {
	void observe(AccountID nodeSubmitting, TransactionID txnId, long at);
	void shiftWindow(long to);
	DuplicateClassification classify(AccountID nodeSubmitting, TransactionID txnId);
}
