package com.haufelexware.report.nunit;

import com.haufelexware.report.nunit.dom.NUnitFailure;
import com.haufelexware.report.nunit.dom.NUnitTestCase;
import com.haufelexware.report.nunit.dom.NUnitTestResults;
import com.haufelexware.report.nunit.dom.NUnitTestSuite;
import org.junit.Assert;
import org.junit.Test;

import javax.xml.bind.JAXBException;
import java.util.List;

/**
 * Ensure that {@link NUnitReportParser} is working correctly.
 */
public class NUnitReportParserTest {

	@Test
	public void testParsingNUnit35Report() throws JAXBException {
		NUnitTestResults results = new NUnitReportParser().parseFrom(getClass().getClassLoader().getResourceAsStream("nunit.testResults.xml"));
		Assert.assertNotNull("results should not be null", results);
		Assert.assertEquals("run date", "2017-11-02", results.getDate());
		Assert.assertEquals("run time", "09:34:09", results.getTime());
		NUnitTestSuite testSuite = results.getTestSuite();
		Assert.assertNotNull("test-suite should not be null", testSuite);
		Assert.assertEquals("test-suite type", "Assembly", testSuite.getType());
		Assert.assertEquals("test-suite name", "\\go\\pipelines\\pt_build_datageneration_tool\\top\\datageneration\\Haufe.PT.DataGeneration\\test\\Haufe.PT.DataGeneration.Tests\\bin\\Release\\Haufe.PT.DataGeneration.Tests.dll", testSuite.getName());
		Assert.assertEquals("test-suite executed", Boolean.TRUE, testSuite.wasExecuted());
		Assert.assertEquals("test-suite result", "Success", testSuite.getResult());
		Assert.assertEquals("test-suite success", Boolean.TRUE, testSuite.wasSuccess());
		Assert.assertTrue("test-suite time is greater zero", 0 < testSuite.getTime());
		Assert.assertEquals("test-suite number of asserts", 15, testSuite.getAsserts());
		List<NUnitTestCase> testCases = testSuite.getAllTestCases();
		Assert.assertNotNull("testCases should not be null", testCases);
		Assert.assertEquals("number of test cases", 6, testCases.size());
	}

	@Test
	public void testParsingFailedNUnit35Report() throws JAXBException {
		NUnitTestResults results = new NUnitReportParser().parseFrom(getClass().getClassLoader().getResourceAsStream("nunit.failed.testResults.xml"));
		Assert.assertNotNull("results should not be null", results);
		Assert.assertEquals("run date", "2017-11-07", results.getDate());
		Assert.assertEquals("run time", "14:57:13", results.getTime());
		NUnitTestSuite testSuite = results.getTestSuite();
		Assert.assertNotNull("test-suite should not be null", testSuite);
		Assert.assertEquals("test-suite type", "Assembly", testSuite.getType());
		Assert.assertEquals("test-suite name", "\\go\\pipelines\\pt_actors_release\\top\\backend\\Provisioning\\PubSub\\Haufe.PT.Actors.IntegrationTests\\ActorsEndToEndTest\\bin\\Release\\ActorsEndToEndTest.dll", testSuite.getName());
		Assert.assertEquals("test-suite executed", Boolean.TRUE, testSuite.wasExecuted());
		Assert.assertEquals("test-suite result", "Failure", testSuite.getResult());
		Assert.assertEquals("test-suite success", Boolean.FALSE, testSuite.wasSuccess());
		Assert.assertTrue("test-suite time is greater zero", 0 < testSuite.getTime());
		Assert.assertEquals("test-suite number of asserts", 0, testSuite.getAsserts());
		List<NUnitTestCase> testCases = testSuite.getAllTestCases();
		Assert.assertNotNull("testCases should not be null", testCases);
		Assert.assertEquals("number of test cases", 1, testCases.size());
		NUnitTestCase testCase = testCases.get(0);
		Assert.assertTrue("test was executed", testCase.wasExecuted());
		Assert.assertFalse("test was not successful", testCase.wasSuccess());
		NUnitFailure failure = testCase.getFailure();
		Assert.assertNotNull("test should have a failure", failure);
		Assert.assertEquals("test failure message should be", "Test exceeded Timeout value of 1800000ms", failure.getMessage());
		Assert.assertNull("test failure stacktrace should be null", failure.getStacktrace());
	}
}
