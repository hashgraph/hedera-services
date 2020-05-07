package com.hedera.services.config;

public class EntityNumbers {
	public static final long UNKNOWN_NUMBER = Long.MIN_VALUE;

	private final FileNumbers fileNumbers;
	private final AccountNumbers accountNumbers;

	public EntityNumbers(FileNumbers fileNumbers, AccountNumbers accountNumbers) {
		this.fileNumbers = fileNumbers;
		this.accountNumbers = accountNumbers;
	}

	public FileNumbers ofFile() {
		return fileNumbers;
	}

	public AccountNumbers ofAccount() {
		return accountNumbers;
	}
}
