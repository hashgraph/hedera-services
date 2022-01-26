package com.hedera.services.bdd.suiterunner.reflective_runner;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.bdd.suiterunner.enums.SuitePackage;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.CONTRACT_SUITES;
import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome;

public class SandBox {
	public static void main(String[] args) throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
		final var packageName = "com.hedera.services.bdd.suites.contract";
//		Reflections reflections = new Reflections(packageName);
		Set<Class<?>> allClasses = new Reflections(packageName).getTypesAnnotatedWith(AutomatedSuite.class);

		List<HapiApiSuite> collect = allClasses.stream().map(c -> {
			HapiApiSuite object = null;
			try {
				object = (HapiApiSuite) c.getDeclaredConstructor().newInstance();
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
				e.printStackTrace();
			}
			return object;

		}).toList();

		final Map<SuitePackage, Supplier<List<HapiApiSuite>>> suites = new EnumMap<>(SuitePackage.class);
		suites.put(CONTRACT_SUITES, () -> collect);

		List<FinalOutcome> finalOutcomes = suites.get(CONTRACT_SUITES).get().stream().map(HapiApiSuite::runSuiteSync).toList();


//		List<FinalOutcome> collect1 = collect.stream().map(HapiApiSuite::runSuiteSync).toList();


//		List<?> collect = allClasses
//				.stream()
//				.map(Class::getName)
//				.map(s -> {
//					Class<?> clazz = null;
//					try {
//						clazz = Class.forName(s);
//						System.out.println();
//					} catch (ClassNotFoundException e) {
//						e.printStackTrace();
//					}
//					return clazz;
//
//				})
//				.toList();

//
//		for (Class<?> suite : allClasses) {
//			HapiApiSuite test = (HapiApiSuite) suite.getClass().getDeclaredConstructor().newInstance().cast(HapiApiSuite.class);
//
//			System.out.println();
//
//
//		}

//		DetectorSub d =(DetectorSub)detector.getClass().newInstance().cast(DetectorSub.class);

//		private void getDetectors(){
//
//			Set<Class<? extends DetectorSub>> subTypes =
//					reflections.getSubTypesOf(DetectorSub.class);
//			System.out.println(subTypes); // correct classes included here.
//			for(Class<? extends DetectorSub> detector:subTypes){
//				try {
//					DetectorSub d =(DetectorSub)detector.getClass().newInstance().cast(DetectorSub.class); //returns exceptions at runtime.
//				} catch (InstantiationException e) {
//					e.printStackTrace();
//				} catch (IllegalAccessException e) {
//					e.printStackTrace();
//				}
//			}
//		}


//		collect.forEach(c -> {
//			Method method = null;
//			try {
//				method = c.getClass().getMethod("main", String.class.);
//			} catch (NoSuchMethodException e) {
//				e.printStackTrace();
//			}
//			try {
//				method.invoke(c);
//			} catch (IllegalAccessException | InvocationTargetException e) {
//				e.printStackTrace();
//			}
//		});

//		List<HapiApiSuite.FinalOutcome> collect1 = collect.stream().map(HapiApiSuite::runSuiteSync).toList();

		System.out.println();


	}
}
