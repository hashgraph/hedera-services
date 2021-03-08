package com.hedera.services.bdd.suites.nft;

import com.hedera.services.bdd.spec.HapiSpecOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class NftUseCase {
	enum Usage { COLLECTING, TRADING }

	private static final SplittableRandom r = new SplittableRandom();

	private final int users;
	private final int serialNos;
	private final int frequency;
	private final boolean swapHbars;

	private List<NftXchange> xchanges = new ArrayList<>();

	public NftUseCase(int users, int serialNos, int frequency, boolean swapHbars) {
		this.users = users;
		this.serialNos = serialNos;
		this.frequency = frequency;
		this.swapHbars = swapHbars;
	}

	public List<HapiSpecOperation> initializers(AtomicInteger nftTypeIds) {
		List<HapiSpecOperation> init = new ArrayList<>();
		for (int i = 0; i < frequency; i++) {
			var xchange = new NftXchange(users, nftTypeIds.getAndIncrement(), serialNos, swapHbars);
			init.addAll(xchange.initializers());
		}
		return init;
	}

	public HapiSpecOperation nextOp() {
		int choice = r.nextInt(frequency);
		return xchanges.get(choice).nextOp();
	}
}
