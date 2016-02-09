/*
 * (C) Copyright 2012, IBM Corporation
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
package com.ibm.jaggr.core.impl.options;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IPlatformServices;
import com.ibm.jaggr.core.IServiceReference;
import com.ibm.jaggr.core.IServiceRegistration;
import com.ibm.jaggr.core.IShutdownListener;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.options.IOptionsListener;
import com.ibm.jaggr.core.util.SequenceNumberProvider;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Properties;

public class OptionsImplTest {

	@SuppressWarnings("unchecked")
	@Test
	public void testCtor() throws IOException {
		IAggregator mockAggregator = EasyMock.createNiceMock(IAggregator.class);
		IPlatformServices mockPlatformServices = EasyMock.createNiceMock(IPlatformServices.class);
		EasyMock.expect(mockAggregator.getName()).andReturn("test");
		final IServiceRegistration mockShutdownRegistration = EasyMock.createMock(IServiceRegistration.class);
		final IServiceRegistration mockOptionsImplRegistration = EasyMock.createMock(IServiceRegistration.class);
		EasyMock.expect(mockAggregator.getPlatformServices()).andReturn(mockPlatformServices).anyTimes();
		EasyMock.expect(mockPlatformServices.registerService(
				EasyMock.eq(OptionsImpl.class.getName()),
				EasyMock.isA(IOptions.class),
				EasyMock.isA(Dictionary.class))).andAnswer(new IAnswer<IServiceRegistration>() {
					@Override
					public IServiceRegistration answer() throws Throwable {
						OptionsImpl options = (OptionsImpl)EasyMock.getCurrentArguments()[1];
						Dictionary<?, ?> dict = (Dictionary<?,?>)EasyMock.getCurrentArguments()[2];
						Assert.assertEquals("test", dict.get("name"));
						Assert.assertEquals(options.getPropsFile().getAbsolutePath(), dict.get("propsFileName"));
						return mockOptionsImplRegistration;
					}
				}).once();
		EasyMock.expect(mockPlatformServices.registerService(
				EasyMock.eq(IShutdownListener.class.getName()),
				EasyMock.isA(IOptions.class),
				EasyMock.isA(Dictionary.class))).andAnswer(new IAnswer<IServiceRegistration>() {
					@Override
					public IServiceRegistration answer() throws Throwable {
						return mockShutdownRegistration;
					}
				}).once();
		EasyMock.replay(mockAggregator, mockPlatformServices);
		final int[] callCounts = new int[]{0, 0};
		final File propsFile = File.createTempFile("aggregator", ".properties");
		Properties testProps = new Properties();
		testProps.put("foo", "bar");
		testProps.store(new FileOutputStream(propsFile), "");
		OptionsImpl options = new OptionsImpl(true, mockAggregator) {
			@Override
			protected void tryCreatePropsFile() {
				callCounts[0]++;
			}
			@Override
			public File getPropsFile() {
				return propsFile;
			}
		};
		EasyMock.verify(mockAggregator, mockPlatformServices);
		Assert.assertEquals("test", options.getName());
		Assert.assertEquals(1, callCounts[0]);
		Assert.assertEquals("bar", options.getProps().get("foo"));


		// Make sure service registrations are unregistered when aggregator is shutdown
		mockShutdownRegistration.unregister();
		EasyMock.expectLastCall().once();
		mockOptionsImplRegistration.unregister();
		EasyMock.expectLastCall().once();
		EasyMock.replay(mockOptionsImplRegistration, mockShutdownRegistration);
		options.shutdown(mockAggregator);
	}

	@Test
	public void testSetProp() throws IOException {
		final int[] callCounts = new int[]{0,0,0};
		final long[] startSeq = new long[]{SequenceNumberProvider.incrementAndGetSequenceNumber()};
		OptionsImpl options = new OptionsImpl(false, null) {
			@Override
			protected void saveProps(Properties props) {
				callCounts[0]++;
			}

			@Override
			protected void updateNotify(long seq) {
				Assert.assertEquals(startSeq[0]+1, seq);
				callCounts[1]++;
			}
			@Override
			protected void propsFileUpdateNotify(Properties props, long seq) {
				Assert.assertEquals(startSeq[0]+1, seq);
				callCounts[2]++;
			}
		};
		options.setOption("foo", "bar");
		Assert.assertEquals(1, callCounts[0]);
		Assert.assertEquals(1, callCounts[1]);
		Assert.assertEquals(1, callCounts[2]);
		Assert.assertEquals("bar", options.getProps().get("foo"));

		startSeq[0] = SequenceNumberProvider.incrementAndGetSequenceNumber();
		options.setOption("foo", null);
		Assert.assertEquals(2, callCounts[0]);
		Assert.assertEquals(2, callCounts[1]);
		Assert.assertEquals(2, callCounts[2]);
		Assert.assertFalse(options.getProps().contains("foo"));
	}

	@Test
	public void testUpdateNotify() throws Exception {
		final OptionsImpl options = new OptionsImpl(false, null);
		final int[] callCounts = new int[]{0, 0};
		IAggregator mockAggregator = EasyMock.createNiceMock(IAggregator.class);
		IPlatformServices mockPlatformServices = EasyMock.createMock(IPlatformServices.class);
		IServiceReference[] mockServiceRefs = new IServiceReference[] {
				EasyMock.createMock(IServiceReference.class),
				EasyMock.createMock(IServiceReference.class)
		};
		Whitebox.setInternalState(options, "aggregator", mockAggregator);
		Whitebox.setInternalState(options, "registrationName", "test");
		EasyMock.expect(mockAggregator.getPlatformServices()).andReturn(mockPlatformServices).anyTimes();
		EasyMock.expect(mockPlatformServices.getServiceReferences(IOptionsListener.class.getName(), "(name=test)")).andReturn(mockServiceRefs);
		EasyMock.expect(mockPlatformServices.getService(mockServiceRefs[0])).andAnswer(new IAnswer<Object>() {
			@Override public Object answer() throws Throwable {
				return new IOptionsListener() {
					@Override public void optionsUpdated(IOptions updated, long sequence) {
						callCounts[0]++;
						Assert.assertSame(options, updated);
						Assert.assertEquals(1L, sequence);
					}
				};
			}
		});
		EasyMock.expect(mockPlatformServices.getService(mockServiceRefs[1])).andAnswer(new IAnswer<Object>() {
			@Override public Object answer() throws Throwable {
				return new IOptionsListener() {
					@Override public void optionsUpdated(IOptions updated, long sequence) {
						callCounts[1]++;
						Assert.assertSame(options, updated);
						Assert.assertEquals(1L, sequence);
						throw new RuntimeException();
					}
				};
			}
		});
		EasyMock.expect(mockPlatformServices.ungetService(mockServiceRefs[0])).andReturn(true);
		EasyMock.expect(mockPlatformServices.ungetService(mockServiceRefs[1])).andReturn(true);
		EasyMock.replay(mockAggregator, mockPlatformServices, mockServiceRefs[0], mockServiceRefs[1]);
		options.updateNotify(1L);
		EasyMock.verify(mockAggregator, mockPlatformServices, mockServiceRefs[0], mockServiceRefs[1]);
		Assert.assertEquals(1, callCounts[0]);
		Assert.assertEquals(1, callCounts[1]);
	}

	@Test
	public void testPropsFileUpdateNotify() throws Exception {
		final File propsFile = new File("test.properties");
		final OptionsImpl options = new OptionsImpl(true, null) {
			@Override public File getPropsFile() { return propsFile; }
			@Override public void saveProps(Properties props) { }
		};
		options.setOption("foo", "bar");
		final int[] callCounts = new int[]{0, 0};
		IAggregator mockAggregator = EasyMock.createNiceMock(IAggregator.class);
		IPlatformServices mockPlatformServices = EasyMock.createMock(IPlatformServices.class);
		IServiceReference[] mockServiceRefs = new IServiceReference[] {
				EasyMock.createMock(IServiceReference.class),
				EasyMock.createMock(IServiceReference.class)
		};
		Whitebox.setInternalState(options, "aggregator", mockAggregator);
		Whitebox.setInternalState(options, "registrationName", "test");
		EasyMock.expect(mockAggregator.getPlatformServices()).andReturn(mockPlatformServices).anyTimes();
		EasyMock.expect(mockPlatformServices.getServiceReferences(
				OptionsImpl.class.getName(),
				"(propsFileName=" + propsFile.getAbsolutePath().replace("\\", "\\\\") + ")")
		).andReturn(mockServiceRefs);
		EasyMock.expect(mockServiceRefs[0].getProperty("name")).andReturn("test");		// callback shouldn't be called for mockServiceRefs[0] (same name as calling object)
		EasyMock.expect(mockServiceRefs[1].getProperty("name")).andReturn("otherTest");	// callback should be called for mockServiceRefs[1]
		EasyMock.expect(mockPlatformServices.getService(mockServiceRefs[1])).andAnswer(new IAnswer<Object>() {
			@Override public Object answer() throws Throwable {
				return new OptionsImpl(false, null) {
					@Override public void propertiesFileUpdated(Properties props, long sequence) {
						callCounts[1]++;
						Assert.assertEquals("bar", props.get("foo"));
						Assert.assertEquals(1L, sequence);
						throw new RuntimeException();
					}
				};
			}
		});
		EasyMock.expect(mockPlatformServices.ungetService(mockServiceRefs[1])).andReturn(true);
		EasyMock.replay(mockAggregator, mockPlatformServices, mockServiceRefs[0], mockServiceRefs[1]);
		options.propsFileUpdateNotify(options.getProps(), 1L);
		EasyMock.verify(mockAggregator, mockPlatformServices, mockServiceRefs[0], mockServiceRefs[1]);
		Assert.assertEquals(0, callCounts[0]);
		Assert.assertEquals(1, callCounts[1]);
	}
}
