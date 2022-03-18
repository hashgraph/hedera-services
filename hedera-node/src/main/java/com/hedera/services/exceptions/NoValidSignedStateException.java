package com.hedera.services.exceptions;

/**
 * Captures a failure when an invalid signed state is provided to update state children
 */
public class NoValidSignedStateException extends Exception {
	public NoValidSignedStateException(String message) {
		super(message);
	}
}
