package com.hedera.services.utils;

import com.hedera.services.legacy.proto.utils.CommonUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class CommonUtilsTest {
	@Test
	public void testNap() throws InterruptedException, IOException {
		String filePath = "./src/test/resources/test.txt";
		CommonUtils.writeToFile(filePath, "TEST".getBytes());
		CommonUtils.nap(1);
		Assert.assertTrue(new File(filePath).exists());
	}
}
