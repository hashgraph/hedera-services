package com.hedera.services.legacy.utils;

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

import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.Platform;

import java.util.List;
import java.util.Map;

@Deprecated
public class TransactionValidationUtils {
	private static final int MESSAGE_MAX_DEPTH = 50;

	public static boolean validateTxDepth(Transaction transaction) {
		return getDepth(transaction) <= MESSAGE_MAX_DEPTH;
	}

	public static boolean validateTxBodyDepth(TransactionBody transactionBody) {
		return getDepth(transactionBody) < MESSAGE_MAX_DEPTH;
	}

	/**
	 * Get the depth of message, return 0 if it doesn't have any nesting message
	 */
	public static int getDepth(final GeneratedMessageV3 message) {
		Map<Descriptors.FieldDescriptor, Object> fields = message.getAllFields();
		int depth = 0;
		for (Descriptors.FieldDescriptor descriptor : fields.keySet()) {
			Object field = fields.get(descriptor);
			if (field instanceof GeneratedMessageV3) {
				GeneratedMessageV3 fieldMessage = (GeneratedMessageV3) field;
				depth = Math.max(depth, getDepth(fieldMessage) + 1);
			} else if (field instanceof List) {
				for (Object ele : (List) field) {
					if (ele instanceof GeneratedMessageV3) {
						depth = Math.max(depth, getDepth((GeneratedMessageV3) ele) + 1);
					}
				}
			}
		}
		return depth;
	}

	public static boolean validateTxSize(Transaction transaction) {
		return transaction.toByteArray().length <= Platform.getTransactionMaxBytes();
	}

	public static boolean validateQueryHeader(QueryHeader queryHeader, boolean hasPayment) {
		boolean returnFlag = true;
		if (queryHeader == null || queryHeader.getResponseType() == null) {
			returnFlag = false;
		} else if (hasPayment) {
			returnFlag = queryHeader.hasPayment();
		}
		return returnFlag;
	}
}
