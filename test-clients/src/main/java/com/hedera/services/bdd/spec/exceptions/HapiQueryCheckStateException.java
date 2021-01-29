package com.hedera.services.bdd.spec.exceptions;

public class HapiQueryCheckStateException extends IllegalStateException {
	public HapiQueryCheckStateException(String msg) {
		super(msg);
	}
}