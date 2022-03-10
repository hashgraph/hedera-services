package com.hedera.services.state.org;

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

import com.hedera.services.ServicesApp;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.merkle.Archivable;

/**
 * Contains the part of the Hedera Services world state that does influence
 * handling of consensus transactions, but is not hashed or serialized.
 */
public class StateMetadata implements FastCopyable, Archivable {
	private final ServicesApp app;

	public StateMetadata(ServicesApp app) {
		this.app = app;
	}

	private StateMetadata(StateMetadata that) {
		this.app = that.app;
	}

	@Override
	public void archive() {
		// No-op
	}

	@Override
	public StateMetadata copy() {
		return new StateMetadata(this);
	}

	@Override
	public void release() {
		// No-op
	}

	public ServicesApp app() {
		return app;
	}
}
