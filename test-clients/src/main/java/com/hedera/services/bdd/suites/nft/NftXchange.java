package com.hedera.services.bdd.suites.nft;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.perf.NftXchangeLoadProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiApiSuite.A_THOUSAND_HBARS;
import static com.hedera.services.bdd.suites.HapiApiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiApiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiApiSuite.TEN_MILLION_HBARS;
import static com.hedera.services.bdd.suites.perf.NftXchangeLoadProvider.MAX_OPS_IN_PARALLEL;
import static com.hedera.services.bdd.suites.perf.NftXchangeLoadProvider.NUM_CIVILIANS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_NOT_OWNER_OF_NFT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
import static java.util.stream.Collectors.toList;

public class NftXchange {
	private static final int MAX_PER_MINT = 10;
	private static final long TREASURY_BALANCE = TEN_MILLION_HBARS;
	private static final long CIV_START_BALANCE = A_THOUSAND_HBARS;

	private static final int MAX_NUM_BACK_AND_FORTHS = 1_000;

	private static final SplittableRandom r = new SplittableRandom();

	private final int users;
	private final int serialNos;
	private final int nftTypeId;
	private final String nftType;
	private final String treasury;
	private final boolean swapHbar;
	private final List<String> explicitSerialNos;
	private final List<AtomicReference<Direction>> dirs = new ArrayList<>();

	private int numBnfs = 0;
	private AtomicInteger nextBnf = new AtomicInteger(0);
	private List<BackAndForth> bnfs;

	public NftXchange(
			int users,
			int nftTypeId,
			int serialNos,
			String useCase,
			boolean swapHbar
	) {
		this.users = users;
		this.nftTypeId = nftTypeId;
		this.serialNos = serialNos;
		this.swapHbar = swapHbar;

		nftType = nftType(useCase, nftTypeId);
		treasury = treasury(useCase, nftTypeId);

		for (int i = 0; i < serialNos; i++) {
			dirs.add(new AtomicReference<>(Direction.COMING));
		}

		explicitSerialNos = IntStream.range(0, serialNos)
				.mapToObj(i -> "SN" + i)
				.collect(toList());
	}

	public static String civilianNo(int i) {
		return "_" + (i % NUM_CIVILIANS.get());
	}

	public static String civilianKeyNo(int i) {
		return "CK_" + (i % NftXchangeLoadProvider.NUM_CIVILIAN_KEYS.get());
	}

	public static String nftType(String useCase, int id) {
		return useCase + id;
	}

	public static String treasury(String useCase, int id) {
		return nftType(useCase, id) + "Treasury";
	}

	private String myCivilianNo(int i) {
		/* If users=1000, then
				- nftTypeId=1 starts associating with civilians _0, _1, ..., _999
			 	- nftTypeId=2 starts associating with civilians _1000, _1001, ..., _1999
		 */
		return civilianNo(i + ((nftTypeId - 1) * users));
	}

	private String myCivilianKeyNo(int i) {
		return civilianKeyNo(i + ((nftTypeId - 1) * users));
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

		addAssociations(init);
//		createBnfs(init);
		createFixedBnfs(init);
		addSetupXfers(init);

		init.add(getAccountBalance(treasury).logged());

		return init;
	}

	private void createFixedBnfs(List<HapiSpecOperation> init) {
		numBnfs = users / 2;
		if (numBnfs > serialNos) {
			throw new IllegalStateException(String.format(
					"Using fixed bnf creation with %d users, but not enough serial numbers (%d)!", users, serialNos));
		}
		init.add(withOpContext((spec, opLog) ->
				opLog.info(" - Beginning bNf creation")
		));

		int nextSn = 0;
		bnfs = new ArrayList<>();
		for (int i = 0; i < users / 2; i++) {
			int from = 2 * i, to = from + 1;
			/* Since the treasury is actually id 0, we add 1 to from and to ids */
			bnfs.add(new BackAndForth(from + 1, to + 1, explicitSerialNos.get(nextSn++)));
		}
		init.add(withOpContext((spec, opLog) ->
				opLog.info(" - Finished creating {} bNfs that involve {} accounts", bnfs.size(), users)
		));
	}

	private void createBnfs(List<HapiSpecOperation> init) {
		int associations = 1 + users;
		int maxAffordableBnfs = (int) (TREASURY_BALANCE / (2 * CIV_START_BALANCE));
		int squaredAssociations = associations * associations;
		if (squaredAssociations < 0) {
			squaredAssociations = MAX_NUM_BACK_AND_FORTHS;
		}
		numBnfs = Math.min(
				Math.min(maxAffordableBnfs, Math.min(serialNos, MAX_NUM_BACK_AND_FORTHS)),
				squaredAssociations);
		init.add(withOpContext((spec, opLog) ->
				opLog.info(" - Beginning bNf creation")
		));
		Set<BackAndForth> created = new HashSet<>();
		int nextSn = 0;
		while (created.size() < numBnfs) {
			int an = r.nextInt(associations);
			int bn = an;
			while (bnfIdsCollide(an, bn)) {
				bn = r.nextInt(associations);
			}
			BackAndForth cand = new BackAndForth(an, bn, explicitSerialNos.get(nextSn++));
			created.add(cand);
		}
		bnfs = new ArrayList<>(created);
		init.add(withOpContext((spec, opLog) ->
				opLog.info(" - Finished creating {} bNfs", bnfs.size())
		));
	}

	private boolean bnfIdsCollide(int i, int j) {
		if (i == j) {
			return true;
		} else if (i == 0 || j == 0) {
			return false;
		} else {
			int modulus = NUM_CIVILIANS.get();
			return ((i - 1) * nftTypeId) % modulus == ((j - 1) * nftTypeId) % modulus;
		}
	}

	private String name(int treasuryOrCivilian) {
		return treasuryOrCivilian == 0 ? treasury : myCivilianNo(treasuryOrCivilian - 1);
	}

	private void addSetupXfers(List<HapiSpecOperation> init) {
		List<HapiSpecOperation> ops = new ArrayList<>();
		for (BackAndForth bnf : bnfs) {
			if (!treasury.equals(bnf.aName())) {
				var op = cryptoTransfer(tinyBarsFromTo(treasury, bnf.aName(), CIV_START_BALANCE))
						.signedBy(DEFAULT_PAYER, treasury)
						.changingOwnership(ofNft(nftType).serialNo(bnf.sn).from(treasury).to(bnf.aName()))
						.noLogging().deferStatusResolution()
						.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
						.blankMemo();
				ops.add(op);
			}
			if (!treasury.equals(bnf.bName())) {
				var op = cryptoTransfer(tinyBarsFromTo(treasury, bnf.bName(), CIV_START_BALANCE))
						.signedBy(DEFAULT_PAYER, treasury)
						.noLogging().deferStatusResolution()
						.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
						.blankMemo();
				ops.add(op);
			}
			if (ops.size() >= MAX_OPS_IN_PARALLEL.get()) {
				init.add(inParallel(ops.toArray(HapiSpecOperation[]::new)));
				init.add(sleepFor(NftXchangeLoadProvider.SETUP_PAUSE_MS.get())
						.because("can't set up bNf accounts too aggressively!"));
				ops.clear();
			}
		}
		if (!ops.isEmpty()) {
			init.add(inParallel(ops.toArray(HapiSpecOperation[]::new)));
			init.add(sleepFor(NftXchangeLoadProvider.SETUP_PAUSE_MS.get())
					.because("can't set up bNf accounts too aggressively!"));
		}
	}

	private void addAssociations(List<HapiSpecOperation> init) {
		final AtomicInteger soFar = new AtomicInteger(0);
		int n = users;
		while (n > 0) {
			int nextParallelism = Math.min(n, MAX_OPS_IN_PARALLEL.get());
			init.add(inParallel(IntStream.range(0, nextParallelism)
					.mapToObj(i -> nftAssociate(myCivilianNo(soFar.get() + i), nftType)
							.signedBy(DEFAULT_PAYER, myCivilianKeyNo(soFar.get() + i))
							.noLogging().deferStatusResolution()
							.blankMemo()).toArray(HapiSpecOperation[]::new)));
			init.add(sleepFor(NftXchangeLoadProvider.SETUP_PAUSE_MS.get())
					.because("can't associate with NFTs too quickly!"));
			n -= nextParallelism;
			soFar.getAndAdd(nextParallelism);
		}
	}

	private void addMinting(List<HapiSpecOperation> init, final int additional) {
		init.add(withOpContext((spec, opLog) ->
				opLog.info("  - Minting an additional {} serial nos for {}...", additional, nftType)
		));
		int n = additional;
		while (n > 0) {
			int done;
			int nextParallelism = Math.max(1, Math.min(MAX_OPS_IN_PARALLEL.get(), n / MAX_PER_MINT));
			if (nextParallelism == 1) {
				init.add(nftMint(nftType, n));
				done = n;
			} else {
				init.add(inParallel(IntStream.range(0, nextParallelism)
						.mapToObj(i -> nftMint(nftType, MAX_PER_MINT)
								.blankMemo()
								.noLogging().deferStatusResolution())
						.toArray(HapiSpecOperation[]::new)));
				init.add(sleepFor(NftXchangeLoadProvider.SETUP_PAUSE_MS.get())
						.because("can't mint NFTs too fast!"));
				done = nextParallelism * MAX_PER_MINT;
			}
			n -= done;
		}
		init.add(sleepFor(additional).because("we like to mint safely!"));
	}

	public HapiSpecOperation nextOp() {
		/* This is the worst case for G4M reuse! We wait as long as possible to reuse
		each payer/acquirer pair, minimizing the probability G4M will help us in a given
		round.

		Compare to the best case in which there is even "traffic" within each bnf, but
		we cluster it---so we do 10k nextOps from bnfs.get(0); then 10k nextOps from
		bnfs.get(1), and so on. If we did this, then G4M reuse would be very high. */
		int ni = nextBnf.getAndUpdate(i -> (i + 1) % numBnfs);
		return bnfs.get(ni).nextOp();
	}

	class BackAndForth {
		final int a, b;
		final String sn;
		final AtomicReference<Direction> dir = new AtomicReference<>(Direction.COMING);

		public BackAndForth(int a, int b, String sn) {
			this.a = a;
			this.b = b;
			this.sn = sn;
		}

		public String aName() {
			return name(a);
		}

		public String bName() {
			return name(b);
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this)
					.add("a#", a)
					.add("aName", aName())
					.add("b#", b)
					.add("bName", bName())
					.add("sn", sn)
					.toString();
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
			return this.a == that.a && this.b == that.b && this.sn.equals(that.sn);
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
			var payer = name(dir == Direction.COMING ? a : b);
			var acquirer = name(dir == Direction.GOING ? a : b);
			var payerKey = keyName(dir == Direction.COMING ? a : b);
			var acquirerKey = keyName(dir == Direction.GOING ? a : b);

			var op = swapHbar
					? cryptoTransfer(tinyBarsFromTo(acquirer, payer, 1L)).signedBy(payerKey, acquirerKey)
					: cryptoTransfer().signedBy(payerKey);
			op
					.changingOwnership(ofNft(nftType).serialNo(sn).from(payer).to(acquirer))
					.payingWith(payer)
					.fee(ONE_HBAR)
					.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
					.hasKnownStatusFrom(SUCCESS, UNKNOWN, ACCOUNT_NOT_OWNER_OF_NFT)
					.blankMemo()
					.noLogging().deferStatusResolution();
			return op;
		}

		private String keyName(int i) {
			return i == 0 ? treasury : myCivilianKeyNo(i - 1);
		}
	}

	public String getNftType() {
		return nftType;
	}

	public String getTreasury() {
		return treasury;
	}
}
