package com.hedera.services.context;

import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.submerkle.RandomFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;
import java.util.SplittableRandom;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 5)
@Measurement(iterations = 5, time = 10)
public class EvmFnResultBench {
	private static final long SEED = 1_234_321L;
	private static final SplittableRandom r = new SplittableRandom(SEED);
	private static final RandomFactory randomFactory = new RandomFactory(r);

	private int i;
	private EvmResultRandomParams params;
	private FullEvmResult[] results;

	@Param("4")
	int maxLogs;
	@Param("4096")
	int maxLogData;
	@Param("2")
	int maxCreations;
	@Param("2")
	int maxLogTopics;
	@Param("1024")
	int maxOutputWords;
	@Param("1000")
	int uniqResultsPerIteration;
	@Param("5")
	int numAddressesWithChanges;
	@Param("10")
	int numStateChangesPerAddress;
	@Param("0.1")
	double creationProbability;
	@Param("0.8")
	double callSuccessProbability;
	@Param("true")
	boolean enableTraceability;

	@Setup(Level.Trial)
	public void setupParams() {
		params = new EvmResultRandomParams(
				maxLogs,
				maxLogData,
				maxLogTopics,
				maxCreations,
				maxOutputWords,
				numAddressesWithChanges,
				numStateChangesPerAddress,
				creationProbability,
				callSuccessProbability,
				enableTraceability);
	}

	@Setup(Level.Iteration)
	public void generateIterationInputs() {
		results = new FullEvmResult[uniqResultsPerIteration];
		Arrays.setAll(results, i -> randomFactory.randomEvmResult(params));
		i = 0;
	}

	@Benchmark
	public void externalizeWithGrpcIntermediary(Blackhole blackhole) {
		final var input = results[i++ % uniqResultsPerIteration];
		EvmFnResult result;
		if (input.evmAddress() == null) {
			result = EvmFnResult.fromGrpc(input.result().toGrpc());
		} else {
			result = EvmFnResult.fromGrpc(input.result().toCreationGrpc(input.evmAddress()));
		}
		blackhole.consume(result);
	}

	@Benchmark
	public void externalizeDirectly(Blackhole blackhole) {
		final var input = results[i++ % uniqResultsPerIteration];
		EvmFnResult result;
		if (input.evmAddress() == null) {
			result = EvmFnResult.fromCall(input.result());
		} else {
			result = EvmFnResult.fromCreate(input.result(), input.evmAddress());
		}
		blackhole.consume(result);
	}
}
