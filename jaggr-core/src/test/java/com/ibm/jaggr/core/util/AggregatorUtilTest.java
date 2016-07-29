/*
 * (C) Copyright IBM Corp. 2012, 2016
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

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.options.IOptions;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Test;

import junit.framework.Assert;

public class AggregatorUtilTest {

	@Test
	public void testGetCacheBust() throws Exception {
		IAggregator mockAggregator = EasyMock.createMock(IAggregator.class);
		final IConfig mockConfig = EasyMock.createNiceMock(IConfig.class);
		final IOptions mockOptions = EasyMock.createNiceMock(IOptions.class);
		EasyMock.expect(mockAggregator.getConfig()).andAnswer(new IAnswer<IConfig>() {
			@Override public IConfig answer() throws Throwable { return mockConfig; }
		}).anyTimes();
		EasyMock.expect(mockAggregator.getOptions()).andAnswer(new IAnswer<IOptions>() {
			@Override public IOptions answer() throws Throwable { return mockOptions; }
		}).anyTimes();
		EasyMock.replay(mockAggregator, mockConfig, mockOptions);
		Assert.assertNull(AggregatorUtil.getCacheBust(mockAggregator));

		EasyMock.reset(mockConfig);
		EasyMock.expect(mockConfig.getCacheBust()).andReturn("abc").anyTimes();
		EasyMock.replay(mockConfig);
		Assert.assertEquals("abc", AggregatorUtil.getCacheBust(mockAggregator));

		EasyMock.reset(mockOptions);
		EasyMock.expect(mockOptions.getCacheBust()).andReturn("123").anyTimes();
		EasyMock.replay(mockOptions);
		Assert.assertEquals("abc-123", AggregatorUtil.getCacheBust(mockAggregator));

		EasyMock.reset(mockConfig);
		EasyMock.replay(mockConfig);
		Assert.assertEquals("123", AggregatorUtil.getCacheBust(mockAggregator));

		EasyMock.reset(mockConfig);
		EasyMock.expect(mockConfig.getCacheBust()).andReturn("").anyTimes();
		EasyMock.replay(mockConfig);
		Assert.assertEquals("123", AggregatorUtil.getCacheBust(mockAggregator));

		EasyMock.reset(mockConfig, mockOptions);
		EasyMock.expect(mockConfig.getCacheBust()).andReturn("abc").anyTimes();
		EasyMock.expect(mockOptions.getCacheBust()).andReturn("").anyTimes();
		EasyMock.replay(mockConfig, mockOptions);
		Assert.assertEquals("abc", AggregatorUtil.getCacheBust(mockAggregator));

	}

}
