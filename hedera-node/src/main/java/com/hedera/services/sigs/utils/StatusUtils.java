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

import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.legacy.crypto.SignatureStatusCode;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

/**
 * Provides static factories of {@link SignatureStatus} instances representing various
 * outcomes of signature processing.
 *
 * @author Michael Tinker
 */
public class StatusUtils {
	/**
	 * Creates a {@link SignatureStatus} representing success in some aspect of sig processing.
	 *
	 * @param inHandleCtx flag indicating if success occurred in the dynamic context of
	 * {@code handleTransaction}.
	 * @param platformTxn the platform txn experiencing success.
	 * @return the desired representation of success.
	 */
	public static SignatureStatus successFor(boolean inHandleCtx, TxnAccessor platformTxn) {
		return new SignatureStatus(
				SignatureStatusCode.SUCCESS, ResponseCodeEnum.OK,
				inHandleCtx, platformTxn.getTxnId(),
				null, null, null, null);
	}
}
