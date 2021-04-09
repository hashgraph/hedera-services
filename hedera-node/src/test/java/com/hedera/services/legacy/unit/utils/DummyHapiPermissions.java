package com.hedera.services.legacy.unit.utils;

import com.hedera.services.config.MockAccountNumbers;
import com.hedera.services.context.domain.security.HapiOpPermissions;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class DummyHapiPermissions extends HapiOpPermissions {
	final ResponseCodeEnum always;

	public DummyHapiPermissions() {
		super(new MockAccountNumbers());
		always = OK;
	}

	public DummyHapiPermissions(ResponseCodeEnum always) {
		super(new MockAccountNumbers());
		this.always = always;
	}

	@Override
	public ResponseCodeEnum permissibilityOf(HederaFunctionality function, AccountID givenPayer) {
		return always;
	}
}
