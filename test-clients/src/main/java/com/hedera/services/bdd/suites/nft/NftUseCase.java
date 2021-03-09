package com.hedera.services.bdd.suites.nft;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;

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
	private final String useCase;
	private final boolean swapHbars;

	private List<NftXchange> xchanges = new ArrayList<>();

	public NftUseCase(int users, int serialNos, int frequency, String useCase, boolean swapHbars) {
		this.users = users;
		this.serialNos = serialNos;
		this.frequency = frequency;
		this.useCase = useCase;
		this.swapHbars = swapHbars;
	}

	public int getUsers() {
		return users;
	}

	public List<HapiSpecOperation> initializers(AtomicInteger nftTypeIds) {
		List<HapiSpecOperation> init = new ArrayList<>();
		for (int i = 0; i < frequency; i++) {
			var xchange = new NftXchange(
					users,
					nftTypeIds.getAndIncrement(),
					serialNos,
					useCase,
					swapHbars);
			init.add(UtilVerbs.withOpContext((spec, opLog) -> {
				opLog.info("Initializing {}...", xchange.getNftType());
			}));
			init.addAll(xchange.initializers());
			xchanges.add(xchange);
		}
		return init;
	}

	public HapiSpecOperation nextOp() {
		int choice = r.nextInt(frequency);
		return xchanges.get(choice).nextOp();
	}

	public List<NftXchange> getXchanges() {
		return xchanges;
	}
}
