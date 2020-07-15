package com.hedera.services.bdd.suites.utils.sysfiles.serdes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.bdd.suites.utils.sysfiles.FeeScheduleDeJson;
import com.hedera.services.bdd.suites.utils.sysfiles.FeeSchedulesListEntry;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FeesJsonToGrpcBytes implements SysFileSerde<String> {
	public static void main(String... args) {
		var subject = new FeesJsonToGrpcBytes();
		try {
			var grpcBytes = subject.toRawFile(Files.readString(Paths.get("src/main/resource/FeeSchedule.json")));
			var grpc = CurrentAndNextFeeSchedule.parseFrom(grpcBytes);
			System.out.println(grpc.toString());
			var json = subject.fromRawFile(grpcBytes);
			System.out.println("-----");
			System.out.println(json);
			Files.write(Paths.get("ReconstructedFeeSchedule.json"), json.getBytes());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private final ObjectMapper mapper = new ObjectMapper();

	@Override
	public String fromRawFile(byte[] bytes) {
		try {
			var grpc = CurrentAndNextFeeSchedule.parseFrom(bytes);

			List<FeeSchedulesListEntry> feeSchedules = new ArrayList<>();
			feeSchedules.add(
					FeeSchedulesListEntry.asCurrentFeeSchedule(
							FeeSchedulesListEntry.from(grpc.getCurrentFeeSchedule())));
			feeSchedules.add(
					FeeSchedulesListEntry.asNextFeeSchedule(
							FeeSchedulesListEntry.from(grpc.getNextFeeSchedule())));

			return mapper
					.writerWithDefaultPrettyPrinter()
					.writeValueAsString(feeSchedules);
		} catch (InvalidProtocolBufferException | JsonProcessingException e) {
			throw new IllegalArgumentException("Not a set of fee schedules!", e);
		}
	}

	@Override
	public byte[] toRawFile(String styledFile) {
		try {
			return FeeScheduleDeJson.fromJsonLiteral(styledFile).toByteArray();
		} catch (Exception e) {
			throw new IllegalArgumentException("Not a set of fee schedules!", e);
		}
	}

	@Override
	public String preferredFileName() {
		return "feeSchedules.json";
	}
}
