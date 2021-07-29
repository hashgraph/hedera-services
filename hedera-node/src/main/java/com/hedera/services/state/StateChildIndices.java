package com.hedera.services.state;

/* Current order of Merkle node children in Services */
public class StateChildIndices {
	public static final int UNIQUE_TOKENS = 0;
	public static final int TOKENS = 1;
	public static final int TOPICS = 2;
	public static final int STORAGE = 3;
	public static final int ACCOUNTS = 4;
	public static final int NETWORK_CTX = 5;
	public static final int TOKEN_ASSOCIATIONS = 6;
	public static final int DISK_FS = 7;
	public static final int SCHEDULE_TXS = 8;
	public static final int RECORD_STREAM_RUNNING_HASH = 9;
	public static final int ADDRESS_BOOK = 10;
	public static final int NUM_0170_CHILDREN = 11;
}
