package com.hedera.services.state;

public class StateVersions {
	/* For the record,
	     - Release 0.7.0 was state version 1
	     - Release 0.8.0 was state version 2
	     - Release 0.9.0 was state version 3
	     - Release 0.10.0 was state version 4
	     - Release 0.11.0 was state version 5
	     - Release 0.12.0 was state version 6
	     - Release 0.13.0 was state version 7
	     - Release 0.14.0 was state version 8
	     - Release 0.15.0 was state version 9 */

	public static final int RELEASE_0160_VERSION = 10;
	public static final int RELEASE_0170_VERSION = 11;
	public static final int MINIMUM_SUPPORTED_VERSION = RELEASE_0160_VERSION;
	public static final int CURRENT_VERSION = RELEASE_0170_VERSION;
}
