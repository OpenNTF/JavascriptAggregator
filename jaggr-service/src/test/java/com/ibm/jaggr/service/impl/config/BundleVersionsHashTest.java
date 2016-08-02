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
package com.ibm.jaggr.service.impl.config;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.IPlatformServices;
import com.ibm.jaggr.core.IServiceReference;
import com.ibm.jaggr.core.InitParams;
import com.ibm.jaggr.core.InitParams.InitParam;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.config.IConfigScopeModifier;
import com.ibm.jaggr.core.impl.config.ConfigImpl;
import com.ibm.jaggr.core.test.TestUtils;

import com.ibm.jaggr.service.IBundleResolver;

import org.apache.commons.codec.binary.Base64;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Test;
import org.mozilla.javascript.WrappedException;
import org.osgi.framework.Bundle;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public class BundleVersionsHashTest {

	@Test
	public void testBundleVersionsHash() throws Exception {
		URI tmpDir = new File(System.getProperty("user.dir")).toURI();
		IAggregator mockAggregator = TestUtils.createMockAggregator();
		InitParams initParams = new InitParams(Arrays.asList(new InitParam[]{new InitParam("propName", "getBundleVersionsHash", mockAggregator)}));
		final Map<String, Bundle> bundleMap = new HashMap<String, Bundle>();
		IBundleResolver bundleResolver = new IBundleResolver() {
			@Override
			public Bundle getBundle(String bundleName) {
				return bundleMap.get(bundleName);
			}
		};
		BundleVersionsHash bvh = new BundleVersionsHash(bundleResolver);
		IServiceReference mockServiceReference = EasyMock.createNiceMock(IServiceReference.class);
		IServiceReference[] serviceReferences = new IServiceReference[]{mockServiceReference};
		IPlatformServices mockPlatformServices = EasyMock.createNiceMock(IPlatformServices.class);
		IAggregatorExtension mockExtension = EasyMock.createMock(IAggregatorExtension.class);
		EasyMock.expect(mockAggregator.getPlatformServices()).andReturn(mockPlatformServices).anyTimes();
		EasyMock.replay(mockAggregator);
		Dictionary<String, String> dict = new Hashtable<String, String>();
		dict.put("name", mockAggregator.getName());
		EasyMock.expect(mockPlatformServices.getService(mockServiceReference)).andReturn(bvh).anyTimes();
		EasyMock.expect(mockExtension.getInitParams()).andReturn(initParams).anyTimes();
		EasyMock.expect(mockPlatformServices.getServiceReferences(IConfigScopeModifier.class.getName(), "(name="+mockAggregator.getName()+")")).andReturn(serviceReferences).anyTimes();
		EasyMock.replay(mockServiceReference, mockPlatformServices, mockExtension);
		bvh.initialize(mockAggregator, mockExtension, null);
		EasyMock.verify(mockPlatformServices);

		Bundle mockBundle1 = EasyMock.createMock(Bundle.class);
		Bundle mockBundle2 = EasyMock.createMock(Bundle.class);
		bundleMap.put("com.test.bundle1", mockBundle1);
		bundleMap.put("com.test.bundle2", mockBundle2);
		final Dictionary<String, String> bundle1Headers = new Hashtable<String, String>();
		final Dictionary<String, String> bundle2Headers = new Hashtable<String, String>();
		EasyMock.expect(mockBundle1.getHeaders()).andAnswer(new IAnswer<Dictionary<String, String>>() {
			@Override public Dictionary<String, String> answer() throws Throwable {
				return bundle1Headers;
			}
		}).anyTimes();
		EasyMock.expect(mockBundle2.getHeaders()).andAnswer(new IAnswer<Dictionary<String, String>>() {
			@Override public Dictionary<String, String> answer() throws Throwable {
				return bundle2Headers;
			}
		}).anyTimes();
		EasyMock.replay(mockBundle1, mockBundle2);

		bundle1Headers.put("Bnd-LastModified", "123456789");
		bundle2Headers.put("Bnd-LastModified", "234567890");
		bundle1Headers.put("Bundle-Version", "1.2.3.20140414");
		bundle2Headers.put("Bundle-Version", "1.2.3.20140412");
		String config =  "{cacheBust:getBundleVersionsHash(['Bundle-Version', 'Bnd-LastModified'], 'com.test.bundle1', 'com.test.bundle2')}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config, true);
		String cacheBust = cfg.getCacheBust();

		bundle1Headers.put("Bnd-LastModified", "123456780");
		cfg = new ConfigImpl(mockAggregator, tmpDir, config, true);
		Assert.assertFalse(cacheBust.equals(cfg.getCacheBust()));

		bundle1Headers.put("Bnd-LastModified", "123456789");
		cfg = new ConfigImpl(mockAggregator, tmpDir, config, true);
		Assert.assertEquals(cacheBust, cfg.getCacheBust());

		bundle2Headers.put("Bundle-Version", "1.2.4");
		cfg = new ConfigImpl(mockAggregator, tmpDir, config, true);
		Assert.assertFalse(cacheBust.equals(cfg.getCacheBust()));

		bundle2Headers.put("Bundle-Version", "1.2.3.20140412");
		cfg = new ConfigImpl(mockAggregator, tmpDir, config, true);
		Assert.assertEquals(cacheBust, cfg.getCacheBust());

		// Test that when header names are not specified, it defaults to 'Bundle-Version'.
		config =  "{cacheBust:getBundleVersionsHash('com.test.bundle1', 'com.test.bundle2')}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config, true);
		cacheBust = cfg.getCacheBust();

		bundle1Headers.put("Bnd-LastModified", "123456780");
		cfg = new ConfigImpl(mockAggregator, tmpDir, config, true);
		Assert.assertEquals(cacheBust, cfg.getCacheBust());

		bundle2Headers.put("Bundle-Version", "1.2.4");
		cfg = new ConfigImpl(mockAggregator, tmpDir, config, true);
		Assert.assertFalse(cacheBust.equals(cfg.getCacheBust()));

		bundle2Headers.put("Bundle-Version", "1.2.3.20140412");
		cfg = new ConfigImpl(mockAggregator, tmpDir, config, true);
		Assert.assertEquals(cacheBust, cfg.getCacheBust());

		// Ensure exception thrown if a specified bundle is not found
		config =  "{cacheBust:getBundleVersionsHash('com.test.bundle1', 'com.test.bundle2', 'com.test.bundle3')}";
		try {
			cfg = new ConfigImpl(mockAggregator, tmpDir, config, true);
			Assert.fail("Expected exception");
		} catch (WrappedException ex) {
			Assert.assertTrue(NotFoundException.class.isInstance(ex.getCause()));
		}

		// ensure exception thrown if argument is wrong type
		config =  "{cacheBust:getBundleVersionsHash({})}";
		try {
			cfg = new ConfigImpl(mockAggregator, tmpDir, config, true);
			Assert.fail("Expected exception");
		} catch (WrappedException ex) {
			Assert.assertTrue(IllegalArgumentException.class.isInstance(ex.getCause()));
		}


		// ensure value is null if no bundle names specified
		config =  "{cacheBust:getBundleVersionsHash()}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config, true);
		Assert.assertNull(cfg.getCacheBust());

		config =  "{cacheBust:getBundleVersionsHash(['Bundle-Version'])}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config, true);
		Assert.assertNull(cfg.getCacheBust());

		config = "{}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config, true);
		Assert.assertNull(cfg.getCacheBust());

		// Ensure that cacheBust is a base64 encoded array of 16 bytes.
		byte[] bytes = Base64.decodeBase64(cacheBust);
		Assert.assertEquals(16, bytes.length);
	}

}
