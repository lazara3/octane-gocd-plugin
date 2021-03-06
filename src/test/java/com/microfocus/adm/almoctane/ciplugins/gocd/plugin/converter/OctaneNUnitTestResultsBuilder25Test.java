/*
 *
 *  (c) Copyright 2018 Micro Focus or one of its affiliates.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * /
 *
 */

package com.microfocus.adm.almoctane.ciplugins.gocd.plugin.converter;

import com.hp.octane.integrations.dto.tests.TestRun;
import org.junit.Assert;
import org.junit.Test;

import javax.xml.bind.JAXBException;
import java.util.List;

/**
 * This class ensures that {@link OctaneNUnit25TestResultsBuilder} is working correctly.
 */
public class OctaneNUnitTestResultsBuilder25Test {

	@Test
	public void testConversionFromNUnitXMLIntoOctaneModel() throws JAXBException {
		final List<TestRun> tests = OctaneNUnit25TestResultsBuilder.convert(getClass().getClassLoader().getResourceAsStream("nunit25.testResults.xml"));
		Assert.assertNotNull("tests should not be null", tests);
		Assert.assertEquals("number of tests", 6, tests.size());
		TestRun testRun = tests.get(0);
		Assert.assertEquals("package name", "MicroFocus.PT.DataGeneration.Tests", testRun.getPackageName());
		Assert.assertEquals("class name", "CommandLineOptionsTests", testRun.getClassName());
		Assert.assertEquals("test name", "TestParseGenerators_MultipleGenerators_OneIncorrect", testRun.getTestName());
	}
}
