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

package com.ibm.jaggr.core.impl.cache;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cache.ICacheManager;

/**
 * Dynamic proxy class for IAggregator that overrides getCacheManager and
 * returns the specified object if the original method returns null. Used by the
 * cache manager during cache manager initialization for de-serialization code
 * that may need to reference the cache manager before the aggregator is able to
 * return the correct value.
 */
public class AggregatorProxy implements java.lang.reflect.InvocationHandler {

    private Object aggr;
    private ICacheManager cacheMgr;

    public static IAggregator newInstance(IAggregator aggr, ICacheManager cacheMgr) {
	return (IAggregator)java.lang.reflect.Proxy.newProxyInstance(
	    aggr.getClass().getClassLoader(),
	    new Class[]{IAggregator.class},
	    new AggregatorProxy(aggr, cacheMgr));
    }

    private AggregatorProxy(Object aggr, ICacheManager cacheMgr) {
    	this.aggr = aggr;
    	this.cacheMgr = cacheMgr;
    }

    public Object invoke(Object proxy, Method m, Object[] args)
	throws Throwable {
        Object result;
		try {
		    result = m.invoke(aggr, args);
			if (result == null && "getCacheManager".equals(m.getName())) { //$NON-NLS-1$
				return cacheMgr;
			}
        } catch (InvocationTargetException e) {
		    throw e.getTargetException();
        } catch (Exception e) {
		    throw new RuntimeException(e);
		}
		return result;
    }
}
