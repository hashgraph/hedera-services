package com.hedera.services.security.ops;

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
