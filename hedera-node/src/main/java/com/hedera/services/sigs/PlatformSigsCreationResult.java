package com.hedera.services.sigs;

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
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hedera.services.legacy.exception.KeySignatureCountMismatchException;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Encapsulates a (mutable) result of an attempt to create {@link Signature}
 * instances from a list of public keys and a source of the cryptographic signatures
 * associated to those keys.
 *
 * @author Michael Tinker
 */
public class PlatformSigsCreationResult {
	private List<TransactionSignature> platformSigs = new ArrayList<>();
	private Optional<Exception> terminatingEx = Optional.empty();

	public List<TransactionSignature> getPlatformSigs() {
		return platformSigs;
	}

	public boolean hasFailed() {
		return terminatingEx.isPresent();
	}
	public void setTerminatingEx(Exception terminatingEx) {
		this.terminatingEx = Optional.of(terminatingEx);
	}
	public Exception getTerminatingEx() {
		return terminatingEx.get();
	}

	/**
	 * Represent this result as a {@link SignatureStatus}.
	 *
	 * @param inHandleDynamicContext a flag giving whether this result occurred in the dynamic context of
	 * {@code ServicesState#handleTransaction(long, boolean, Instant, Instant, Transaction, Address)}
	 * @param txnId the id of the related gRPC txn.
	 * @return the desired representation.
	 */
	public SignatureStatus asSignatureStatus(boolean inHandleDynamicContext, TransactionID txnId) {
		SignatureStatusCode sigStatus;
		ResponseCodeEnum responseCode;

		if (!hasFailed()) {
			sigStatus = SignatureStatusCode.SUCCESS;
			responseCode = ResponseCodeEnum.OK;
		} else if (terminatingEx.isPresent() && terminatingEx.get() instanceof KeySignatureCountMismatchException) {
			sigStatus = SignatureStatusCode.KEY_COUNT_MISMATCH;
			responseCode = ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY;
		} else if (terminatingEx.isPresent() && terminatingEx.get() instanceof KeyPrefixMismatchException) {
			sigStatus = SignatureStatusCode.KEY_PREFIX_MISMATCH;
			responseCode = ResponseCodeEnum.KEY_PREFIX_MISMATCH;
		} else {
			sigStatus = SignatureStatusCode.GENERAL_ERROR;
			responseCode = ResponseCodeEnum.INVALID_SIGNATURE;
		}
		return new SignatureStatus(
				sigStatus, responseCode,
				inHandleDynamicContext, txnId, null, null, null, null);
	}
}
