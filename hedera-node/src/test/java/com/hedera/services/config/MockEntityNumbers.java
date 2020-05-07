package com.hedera.services.config;

public class MockEntityNumbers extends EntityNumbers {
	public MockEntityNumbers() {
		super(new MockFileNumbers(), new MockAccountNumbers());
	}
}
