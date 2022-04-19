package com.hedera.services.state.backgroundSystemTasks;

public enum SystemTaskType {
	DISSOCIATED_NFT_REMOVALS;

	private static final SystemTaskType[] values = values();
	public static SystemTaskType get(int ordinal) { return values[ordinal]; }
}
