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
			return LEGACY_NETWORK_SIZE;
		}
	},
	SERVICE {
		@Override
		public String jsonKey() {
			return "servicedata";
		}

		@Override
		public int multiplier() {
			return LEGACY_NETWORK_SIZE;
		}
	};

	private static final int LEGACY_NETWORK_SIZE = 13;

	public abstract int multiplier();
	public abstract String jsonKey();
}
