package com.hedera.services.security.ops;

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

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public enum SystemOpAuthorization {
	UNNECESSARY {
		@Override
		public ResponseCodeEnum asStatus() {
			return OK;
		}
	}, UNAUTHORIZED {
		@Override
		public ResponseCodeEnum asStatus() {
			return AUTHORIZATION_FAILED;
		}
	}, IMPERMISSIBLE {
		@Override
		public ResponseCodeEnum asStatus() {
			return ENTITY_NOT_ALLOWED_TO_DELETE;
		}
	}, AUTHORIZED {
		@Override
		public ResponseCodeEnum asStatus() {
			return OK;
		}
	};

	public abstract ResponseCodeEnum asStatus();
}
