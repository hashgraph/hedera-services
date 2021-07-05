package com.hedera.services.pricing;

import com.hederahashgraph.api.proto.java.FeeComponents;

import java.util.function.ToLongFunction;

public enum UsableResource {
	CONSTANT {
		@Override
		public ToLongFunction<FeeComponents> getter() {
			return FeeComponents::getConstant;
		}
	},
	BPT {
		@Override
		public ToLongFunction<FeeComponents> getter() {
			return FeeComponents::getBpt;
		}
	},
	VPT {
		@Override
		public ToLongFunction<FeeComponents> getter() {
			return FeeComponents::getVpt;
		}
	},
	RBH {
		@Override
		public ToLongFunction<FeeComponents> getter() {
			return FeeComponents::getRbh;
		}
	},
	SBH {
		@Override
		public ToLongFunction<FeeComponents> getter() {
			return FeeComponents::getSbh;
		}
	},
	GAS {
		@Override
		public ToLongFunction<FeeComponents> getter() {
			return FeeComponents::getGas;
		}
	},
	BPR {
		@Override
		public ToLongFunction<FeeComponents> getter() {
			return FeeComponents::getBpr;
		}
	},
	SBPR {
		@Override
		public ToLongFunction<FeeComponents> getter() {
			return FeeComponents::getSbpr;
		}
	};

	public abstract ToLongFunction<FeeComponents> getter();
}
