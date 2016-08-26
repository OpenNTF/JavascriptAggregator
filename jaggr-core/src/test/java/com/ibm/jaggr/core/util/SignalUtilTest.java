/*
 * (C) Copyright IBM Corp. 2012, 2016 All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ibm.jaggr.core.util;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SignalUtilTest {

	@Before
	public void setUp() throws Exception {}

	@After
	public void tearDown() throws Exception {}

	@Test
	public void testFormatMessage() {
		String result = SignalUtil.formatMessage("{0}, {1}", new Object[]{"1", "2"});
		Assert.assertEquals("1, 2", result);

		result = SignalUtil.formatMessage("{0}, {1}", new Object[]{"1", "2", "3"});
		Assert.assertEquals("1, 2: 3", result);

		result = SignalUtil.formatMessage("{0}, {1}", new Object[]{"1", "2", "3", "4"});
		Assert.assertEquals("1, 2: 3, 4", result);
	}

	@Test
	public void testLogging() {
		Logger mockLogger = EasyMock.createMock(Logger.class);
		final ArrayList<String> logged = new ArrayList<String>();
		String sourceClass = "testClass";
		mockLogger.logp(EasyMock.eq(Level.WARNING), EasyMock.eq(sourceClass), EasyMock.isA(String.class), EasyMock.isA(String.class));
		EasyMock.expectLastCall().andAnswer(new IAnswer<Void>() {
			@Override
			public Void answer() throws Throwable {
				logged.add(
						EasyMock.getCurrentArguments()[1].toString() +
						" - " +
						EasyMock.getCurrentArguments()[2].toString() +
						" - " +
						EasyMock.getCurrentArguments()[3].toString()
				);
				return null;
			}
		}).anyTimes();
		EasyMock.expect(mockLogger.isLoggable(Level.WARNING)).andReturn(Boolean.TRUE).anyTimes();
		Object mockLock = new Object() {
			@Override public String toString() {
				return "MockLock";
			}
		};
		EasyMock.replay(mockLogger);

		long start = System.currentTimeMillis() - SignalUtil.SIGNAL_LOG_INTERVAL_SECONDS * 1000;
		boolean result = SignalUtil.logWaiting(mockLogger, sourceClass, "method1", mockLock, start);
		String expected = sourceClass + " - " + "method1" + " - " + MessageFormat.format(
				com.ibm.jaggr.core.util.Messages.SignalUtil_0,
				new Object[]{Thread.currentThread().getId(), SignalUtil.SIGNAL_LOG_INTERVAL_SECONDS, mockLock});
		System.out.println(logged.get(0));
		Assert.assertEquals(expected, logged.get(0));
		Assert.assertFalse(result);

		// Make sure extra parameters not specified in format string are appended to log message
		start = System.currentTimeMillis() - SignalUtil.SIGNAL_LOG_INTERVAL_SECONDS * 1000;
		result = SignalUtil.logWaiting(mockLogger, sourceClass, "method2", mockLock, start, "extraParam1", 3);
		expected = sourceClass + " - " + "method2" + " - " + MessageFormat.format(
				com.ibm.jaggr.core.util.Messages.SignalUtil_0,
				new Object[]{Thread.currentThread().getId(), SignalUtil.SIGNAL_LOG_INTERVAL_SECONDS, mockLock})
				+ ": extraParam1, 3";
		System.out.println(logged.get(1));
		Assert.assertEquals(expected, logged.get(1));
		Assert.assertFalse(result);

		// Test quiescence
		start = System.currentTimeMillis() - SignalUtil.SIGNAL_LOG_QUIESCE_TIMEOUT_MINUTES * 1000 * 61;
		result = SignalUtil.logWaiting(mockLogger, sourceClass, "method3", mockLock, start);
		expected = sourceClass + " - " + "method3" + " - " + MessageFormat.format(
				com.ibm.jaggr.core.util.Messages.SignalUtil_1,
				new Object[]{Thread.currentThread().getId(), (SignalUtil.SIGNAL_LOG_QUIESCE_TIMEOUT_MINUTES)*61, mockLock});
		System.out.println(logged.get(2));
		Assert.assertEquals(expected, logged.get(2));
		Assert.assertTrue(result);


	}
}
