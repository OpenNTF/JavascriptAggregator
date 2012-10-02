/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.layer;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

import org.easymock.EasyMock;
import org.junit.Test;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.InitParams;
import com.ibm.jaggr.service.LimitExceededException;
import com.ibm.jaggr.service.layer.ILayer;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.transport.IHttpTransport;

public class LayerCacheTest {
	
	@Test
	public void test() throws Exception {
		Map<String, Object> requestAttributes = new HashMap<String, Object>();
		List<InitParams.InitParam> initParams = new LinkedList<InitParams.InitParam>();
		initParams.add(new InitParams.InitParam(InitParams.MAXLAYERCACHEENTRIES_INITPARAM, "-1"));
		IAggregator mockAggregator = TestUtils.createMockAggregator(null, null, initParams);
		EasyMock.replay(mockAggregator);
		HttpServletRequest mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes);
		EasyMock.replay(mockRequest);
		LayerCacheImpl layerCache = new LayerCacheImpl(mockAggregator);
		Assert.assertEquals(0, layerCache.getNumCachedEntriesRef().get());
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, "layer1");
		ILayer layer = layerCache.getLayer(mockRequest);
		Assert.assertEquals("layer1", layer.getKey());
		
		initParams.clear();
		initParams.add(new InitParams.InitParam(InitParams.MAXLAYERCACHEENTRIES_INITPARAM, "10"));
		layerCache = new LayerCacheImpl(mockAggregator);
		AtomicInteger numCachedEntries = layerCache.getNumCachedEntriesRef();
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, "layer0");
		ILayer layer0 = layerCache.getLayer(mockRequest);
		Assert.assertEquals(numCachedEntries.get(), 0);
		numCachedEntries.set(9);
		// Assert that the cached layer is returned
		Assert.assertTrue(layer0 == layerCache.getLayer(mockRequest));
		Assert.assertEquals(1, layerCache.size());
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, "layer1");
		layerCache.getLayer(mockRequest);
		Assert.assertEquals(2, layerCache.size());
		numCachedEntries.set(10);
		boolean exceptionThrown = false;
		try {
			requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, "layer3");
			layerCache.getLayer(mockRequest);
		} catch (LimitExceededException e) {
			exceptionThrown = true;
		}
		Assert.assertTrue(exceptionThrown);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, "layer0");
		Assert.assertTrue(layer0 == layerCache.getLayer(mockRequest));
		
		layerCache.clear();
		Assert.assertEquals(0, layerCache.getNumCachedEntriesRef().get());
		Assert.assertEquals(0, layerCache.size());
	}
}
