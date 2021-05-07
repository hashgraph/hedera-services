package com.hedera.services.exceptions;

import static com.hedera.services.utils.EntityIdUtils.readableId;

public class DetachedAccountException extends IllegalArgumentException {
	public DetachedAccountException(Object id) {
		super(readableId(id));
	}
}
