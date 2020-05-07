package com.hedera.services.sigs.order;

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
 * Exception containing the result of a failed attempt to list, in canonical order, the
 * Hedera keys that must have active signatures for some gRPC transaction to be valid.
 *
 * @author Michael Tinker
 * @see HederaSigningOrder
 */
public class SigningOrderException extends Exception {
	private final SigningOrderResult<?> errorReport;

	public SigningOrderException(SigningOrderResult<?> errorReport) {
		this.errorReport = errorReport;
	}

	public SigningOrderResult<?> getErrorReport() {
		return errorReport;
	}
}
