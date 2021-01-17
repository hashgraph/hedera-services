package com.hedera.services.keys;

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

import com.hedera.services.files.HederaFs;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.utils.PlatformTxnAccessor;

import java.util.function.Supplier;

public class InHandleActivationHelper {
	private static final PlatformTxnAccessor NO_LAST_ACCESSOR = null;

	private final HederaFs hfs;
	private final HederaSigningOrder keyOrderer;
	private final Supplier<PlatformTxnAccessor>	accessorSource;

	private PlatformTxnAccessor lastAccessor = NO_LAST_ACCESSOR;

	public InHandleActivationHelper(
			HederaFs hfs,
			HederaSigningOrder keyOrderer,
			Supplier<PlatformTxnAccessor> accessorSource
	) {
		this.hfs = hfs;
		this.keyOrderer = keyOrderer;
		this.accessorSource = accessorSource;
	}
}
