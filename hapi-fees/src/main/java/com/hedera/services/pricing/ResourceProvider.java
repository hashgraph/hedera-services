package com.hedera.services.pricing;

public enum ResourceProvider {
	NODE {
		@Override
		public String jsonKey() {
			return "nodedata";
		}

		@Override
		public int multiplier() {
			return 1;
		}
	},
	NETWORK {
		@Override
		public String jsonKey() {
			return "networkdata";
		}

		@Override
		public int multiplier() {
			return NETWORK_SIZE;
		}
	},
	SERVICE {
		@Override
		public String jsonKey() {
			return "servicedata";
		}

		@Override
		public int multiplier() {
			return NETWORK_SIZE;
		}
	};

	private static final int RELEASE_0160_NETWORK_SIZE = 20;
	private static final int NETWORK_SIZE = RELEASE_0160_NETWORK_SIZE;

	public abstract int multiplier();
	public abstract String jsonKey();
}
