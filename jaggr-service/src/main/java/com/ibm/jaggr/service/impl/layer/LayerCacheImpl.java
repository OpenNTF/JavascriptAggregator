/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.layer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.InitParams;
import com.ibm.jaggr.service.LimitExceededException;
import com.ibm.jaggr.service.layer.ILayer;
import com.ibm.jaggr.service.layer.ILayerCache;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.util.TypeUtil;

/**
 * This class implements the {@link ILayerCache} interface by extending {@link ConcurrentHashMap}
 * and adds methods for cloning and dumping the cache contents.  It also maintains a cache wide
 * count of the number of layer builds currently being cached by the layer objects that are the
 * map entries, as well as the maximum number of layer build cache entries, and throws an exception
 * if an attempt is made to add a new layer to the cache when the layer build cache limit is 
 * reached.
 * <p>
 * TODO: Get rid of limit checking in this cache and implement LRU aging of stale cached entries
 * in the layer build cache.
 */
public class LayerCacheImpl extends ConcurrentHashMap<String, ILayer> implements ILayerCache, Serializable {
	private static final long serialVersionUID = -8918795349757824963L;
	private static final int DEFAULT_MAXLAYERCACHEENTRIES = -1;
	
	final private int maxNumCachedEntries;
	private AtomicInteger numCachedEntries;
	
	// Copy constructor
	// Creates a shallow copy of the map
	private LayerCacheImpl(LayerCacheImpl other) {
		super(other);
		maxNumCachedEntries = other.maxNumCachedEntries;
		numCachedEntries = other.numCachedEntries;
	}
	
	public LayerCacheImpl(IAggregator aggregator) {
		int maxNumCachedEntries = DEFAULT_MAXLAYERCACHEENTRIES;
		numCachedEntries = new AtomicInteger(0);
		InitParams initParams =  aggregator.getInitParams();
		if (initParams != null) {
			List<String> values = initParams.getValues(InitParams.MAXLAYERCACHEENTRIES_INITPARAM);
			maxNumCachedEntries = TypeUtil.asInt(values.size()  > 0 ? values.get(values.size()-1) : null,  DEFAULT_MAXLAYERCACHEENTRIES);
		}
		this.maxNumCachedEntries = maxNumCachedEntries;
	}
	
	/* (non-Javadoc)
	 * @see java.util.AbstractMap#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		LayerCacheImpl cloned = new LayerCacheImpl(this);
		// Copy constructor creates a shallow copy, so we
		// we need to clone the individual values.  Keys don't need to be cloned since
		// strings are immutable.
		for (Map.Entry<String, ILayer> entry : entrySet()) {
			entry.setValue((ILayer)entry.getValue().clone());
		}
		return cloned;
	}

	/**
	 * De-serialize this object from an ObjectInputStream
	 * @param in The ObjectInputStream
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		// Call the default implementation to de-serialize our object
		in.defaultReadObject();
		// Reset numCacheEntries based on calculated size
		int size = 0;
		for (ILayer layer : values()) {
			size += ((LayerImpl)layer).size();
		}
		numCachedEntries.set(size);
	}

	/* (non-Javadoc)
	 * @see java.util.concurrent.ConcurrentHashMap#clear()
	 */
	@Override
	public void clear() {
		super.clear();
		numCachedEntries = new AtomicInteger(0);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.layer.ILayerCache#dump(java.io.Writer, java.util.regex.Pattern)
	 */
	@Override
	public void dump(Writer writer, Pattern filter) throws IOException {
    	String linesep = System.getProperty("line.separator"); //$NON-NLS-1$
    	writer.append("Recorded number of cache entries = " + numCachedEntries + linesep); //$NON-NLS-1$
    	int size = 0;
    	for (Map.Entry<String, ILayer> entry : entrySet()) {
    		size += ((LayerImpl)entry.getValue()).size();
    	}
    	writer.append("Calculated number of cache entries = " + size + linesep); //$NON-NLS-1$
    	writer.append("Configured maximum cached entries = " + maxNumCachedEntries + linesep); //$NON-NLS-1$
    	for (Map.Entry<String, ILayer> entry : entrySet()) {
    		if (filter != null) {
    			Matcher m = filter.matcher(entry.getKey());
    			if (!m.find())
    				continue;
    		}
    		writer.append("ILayer key: ").append(entry.getKey()).append(linesep); //$NON-NLS-1$
    		writer.append(entry.getValue().toString()).append(linesep).append(linesep);
    	}
	}

	@Override
	public ILayer getLayer(HttpServletRequest request) {
		String key = request
        		.getAttribute(IHttpTransport.REQUESTEDMODULES_REQATTRNAME)
        		.toString(); 
        
		String requiredModule = (String)request.getAttribute(IHttpTransport.REQUIRED_REQATTRNAME);
		if (requiredModule != null) {
			key += "{" + requiredModule + "}"; //$NON-NLS-1$ //$NON-NLS-2$
		}
        // Try non-blocking get() request first
        ILayer existingLayer = get(key);
        
        LayerImpl newLayer = null;
        if (existingLayer == null) {
    		// Don't allow new entries if the cache is already maxed out
    		if (maxNumCachedEntries >= 0 && numCachedEntries.get() >= maxNumCachedEntries && !containsKey(key)) {
    			throw new LimitExceededException(
    					MessageFormat.format(
    						Messages.LayerImpl_3,
    						new Object[]{maxNumCachedEntries, InitParams.MAXLAYERCACHEENTRIES_INITPARAM}
    					)
    				);
    		}
            // Now use blocking putIfAbsent to get the layer
            newLayer = new LayerImpl(key, numCachedEntries, maxNumCachedEntries);
	        existingLayer = putIfAbsent(key, newLayer);
        }
        return (existingLayer == null) ? newLayer : existingLayer;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.layer.ILayerCache#get(java.lang.String)
	 */
	@Override
	public ILayer get(String key) {
		return super.get(key);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.layer.ILayerCache#contains(java.lang.String)
	 */
	@Override
	public boolean contains(String key) {
		return super.contains(key);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.layer.ILayerCache#remove(java.lang.String)
	 */
	@Override
	public ILayer remove(String key) {
		return super.remove(key);
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.layer.ILayerCache#getKeys()
	 */
	@Override
	public Set<String> getKeys() {
		return super.keySet();
	}

	/**
	 * Return the reference to the counter for number of cached layer builds
	 * 
	 * @return reference to the counter
	 */
	protected AtomicInteger getNumCachedEntriesRef() {
		return numCachedEntries;
	}
}
