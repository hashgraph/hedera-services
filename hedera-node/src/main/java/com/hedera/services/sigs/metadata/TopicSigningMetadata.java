package com.hedera.services.sigs.metadata;

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

import com.hedera.services.legacy.core.jproto.JKey;

public class TopicSigningMetadata {
	private final JKey adminKey;
	private final JKey submitKey;

	public TopicSigningMetadata(JKey adminKey, JKey submitKey) {
		this.adminKey = adminKey;
		this.submitKey = submitKey;
	}

	public boolean hasAdminKey() { return null != adminKey; }
	public JKey getAdminKey() {
		return adminKey;
	}
	public boolean hasSubmitKey() { return null != submitKey; }
	public JKey getSubmitKey() {
		return submitKey;
	}
}
