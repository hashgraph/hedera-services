package com.hedera.services.bdd.suites.utils.sysfiles;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduleEntryWrapperPojo {
	Long expiryTime;
	ScheduleEntryPojo transactionFeeSchedule;

	public static ScheduleEntryWrapperPojo from(TransactionFeeSchedule grpc) {
		var pojo = new ScheduleEntryWrapperPojo();
		pojo.setTransactionFeeSchedule(ScheduleEntryPojo.from(grpc));
		return pojo;
	}

	public ScheduleEntryPojo getTransactionFeeSchedule() {
		return transactionFeeSchedule;
	}

	public void setTransactionFeeSchedule(ScheduleEntryPojo transactionFeeSchedule) {
		this.transactionFeeSchedule = transactionFeeSchedule;
	}

	public Long getExpiryTime() {
		return expiryTime;
	}

	public void setExpiryTime(Long expiryTime) {
		this.expiryTime = expiryTime;
	}
}
