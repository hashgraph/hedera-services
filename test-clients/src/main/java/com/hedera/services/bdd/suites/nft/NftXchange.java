package com.hedera.services.bdd.suites.nft;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiApiSuite;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_RETRY_PRECHECKS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nftAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nftCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nftMint;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.nft.Acquisition.ofNft;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.resolvingUniquely;
import static com.hedera.services.bdd.suites.HapiApiSuite.A_BILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiApiSuite.A_HUNDRED_THOUSAND_HBARS;
import static com.hedera.services.bdd.suites.HapiApiSuite.A_THOUSAND_HBARS;
import static com.hedera.services.bdd.suites.HapiApiSuite.CIVILIAN;
import static com.hedera.services.bdd.suites.HapiApiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiApiSuite.TEN_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiApiSuite.TEN_THOUSAND_HBARS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_NOT_OWNER_OF_NFT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
import static java.util.stream.Collectors.toList;

public class NftXchange {
	private static final int MAX_PER_MINT = 10;
	private static final int MAX_PARALLEL_OPS = 100;
	private static final long TREASURY_BALANCE = A_BILLION_HBARS;

	private static final int MAX_NUM_BACK_AND_FORTHS = 1_000;

	private static final SplittableRandom r = new SplittableRandom();

	private final int users;
	private final int nftTypeId;
	private final int serialNos;
	private final String nftType;
	private final String treasury;
	private final boolean swapHbar;
	private final List<String> explicitSerialNos;
	private final List<AtomicReference<Direction>> dirs = new ArrayList<>();

	private List<BackAndForth> bnfs;

	public NftXchange(int users, int nftTypeId, int serialNos, boolean swapHbar) {
		this.users = users;
		this.nftTypeId = nftTypeId;
		this.serialNos = serialNos;
		this.swapHbar = swapHbar;

		nftType = "nftType" + nftTypeId;
		treasury = treasury(nftTypeId);

		for (int i = 0; i < serialNos; i++) {
			dirs.add(new AtomicReference<>(Direction.COMING));
		}

		explicitSerialNos = IntStream.range(0, serialNos)
				.mapToObj(i -> "SN" + i)
				.collect(toList());
	}

	public static String treasury(int id) {
		return "nftTreasury" + id;
	}

	public List<HapiSpecOperation> initializers() {
		var init = new ArrayList<HapiSpecOperation>();

		init.add(resolvingUniquely(() -> cryptoCreate(treasury)
				.payingWith(HapiApiSuite.GENESIS)
				.balance(TREASURY_BALANCE)));
		int firstSerialNos = Math.min(10, serialNos), restSerialNos = serialNos - firstSerialNos;
		init.add(nftCreate(nftType)
				.treasury(treasury)
				.initialSerialNos(firstSerialNos));
		if (restSerialNos > 0) {
			addMinting(init, restSerialNos);
		}

		addCivilians(init);
		addAssociations(init);
		createBnfs();
		addSetupXfers(init);

		return init;
	}

	private void createBnfs() {
		int numAssociated = 1 + users;
		int numBnfs = Math.min(MAX_NUM_BACK_AND_FORTHS, numAssociated * numAssociated);
		Set<BackAndForth> created = new HashSet<>();
		while (created.size() < numBnfs) {
			int an = r.nextInt(numAssociated);
			int bn = an;
			while (bn == an) {
				bn = r.nextInt(numAssociated);
			}
			int sn = r.nextInt(serialNos);
			BackAndForth cand = new BackAndForth(name(an), name(bn), explicitSerialNos.get(sn));
			created.add(cand);
		}
		bnfs = new ArrayList<>(created);
	}

	private String name(int treasuryOrCivilian) {
		return treasuryOrCivilian == 0 ? treasury : civilianNo(treasuryOrCivilian - 1);
	}

	private String civilianNo(int i) {
		return CIVILIAN + "_" + nftTypeId + "_" + i;
	}

	private void addSetupXfers(List<HapiSpecOperation> init) {
		List<HapiSpecOperation> ops = new ArrayList<>();
		for (BackAndForth bnf : bnfs) {
			if (treasury.equals(bnf.a)) {
				continue;
			}
			var op = cryptoTransfer(tinyBarsFromTo(treasury, bnf.a, TEN_THOUSAND_HBARS))
					.changingOwnership(ofNft(nftType).serialNo(bnf.sn).from(treasury).to(bnf.a))
					.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
					.blankMemo();
			ops.add(op);
			if (ops.size() == MAX_PARALLEL_OPS) {
				init.add(inParallel(ops.toArray(HapiSpecOperation[]::new)));
			}
		}
		if (!ops.isEmpty()) {
			init.add(inParallel(ops.toArray(HapiSpecOperation[]::new)));
		}
	}

	private void addCivilians(List<HapiSpecOperation> init) {
		int n = users;
		while (n > 0) {
			int nextParallelism = Math.min(n, MAX_PARALLEL_OPS);
			init.add(inParallel(IntStream.range(0, nextParallelism)
					.mapToObj(i -> cryptoCreate(civilianNo(i))
							.blankMemo()
							.emptyBalance()).toArray(HapiSpecOperation[]::new)));
			n -= nextParallelism;
		}
	}

	private void addAssociations(List<HapiSpecOperation> init) {
		int n = users;
		while (n > 0) {
			int nextParallelism = Math.min(n, MAX_PARALLEL_OPS);
			init.add(inParallel(IntStream.range(0, nextParallelism)
					.mapToObj(i -> nftAssociate(civilianNo(i), nftType)
							.blankMemo()).toArray(HapiSpecOperation[]::new)));
			n -= nextParallelism;
		}
	}

	private void addMinting(List<HapiSpecOperation> init, int n) {
		while (n > 0) {
			int done;
			int nextParallelism = Math.max(1, Math.min(MAX_PARALLEL_OPS, n / MAX_PER_MINT));
			if (nextParallelism == 1) {
				init.add(nftMint("mintAlone", n));
				done = n;
			} else {
				init.add(inParallel(IntStream.range(0, nextParallelism)
						.mapToObj(i -> nftMint("mintCopy" + i, MAX_PER_MINT))
						.toArray(HapiSpecOperation[]::new)));
				done = nextParallelism * MAX_PER_MINT;
			}
			n -= done;
		}
	}

	public HapiSpecOperation nextOp() {
		var nextBnf = r.nextInt(bnfs.size());
		return bnfs.get(nextBnf).nextOp();
	}

	class BackAndForth {
		final String a, b, sn;
		final AtomicReference<Direction> dir = new AtomicReference<>(Direction.COMING);

		public BackAndForth(String a, String b, String sn) {
			this.a = a;
			this.b = b;
			this.sn = sn;
		}

		@Override
		public int hashCode() {
			return Objects.hash(a, b, sn);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || o.getClass() != BackAndForth.class) {
				return false;
			}
			BackAndForth that = (BackAndForth) o;
			return this.a.equals(that.a) && this.b.equals(that.b) && this.sn.equals(that.sn);
		}

		public HapiSpecOperation nextOp() {
			for (; ; ) {
				var witnessDir = dir.get();
				var newDir = (witnessDir == Direction.COMING)
						? Direction.GOING
						: Direction.COMING;
				if (dir.compareAndSet(witnessDir, newDir)) {
					return xferFor(newDir);
				}
			}
		}

		private HapiCryptoTransfer xferFor(Direction dir) {
			var payer = dir == Direction.COMING ? a : b;
			var acquirer = dir == Direction.GOING ? a : b;
			var op = swapHbar
					? cryptoTransfer(tinyBarsFromTo(acquirer, payer, ONE_HBAR)).signedBy(payer, acquirer)
					: cryptoTransfer().signedBy(payer);
			return op
					.changingOwnership(ofNft(sn).serialNo(sn).from(payer).to(acquirer))
					.payingWith(payer)
					.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
					.hasKnownStatusFrom(SUCCESS, UNKNOWN, ACCOUNT_NOT_OWNER_OF_NFT)
					.blankMemo()
					.noLogging()
					.deferStatusResolution();
		}
	}
}
