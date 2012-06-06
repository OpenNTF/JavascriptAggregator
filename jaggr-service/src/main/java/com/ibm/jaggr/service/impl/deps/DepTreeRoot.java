/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.deps;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;

import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.util.PathUtil;

@SuppressWarnings("serial")
public class DepTreeRoot extends DepTreeNode {

	private IConfig config;
	
	public DepTreeRoot(IConfig config) {
		super(""); //$NON-NLS-1$
		this.config = config;
	}
	
	@Override
	public IConfig getConfig() {
		return config;
	}
	
	@Override
	public DepTreeNode getRoot() {
		return this;
	}
	/**
	 * Instances of this object should not be serialized, but the
	 * base class is serializable, so we need to throw an
	 * exception here if an attempt is made to serialize this class.
	 * 
	 * @param in The {@link ObjectInputStream} to read from
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private void readObject(ObjectInputStream in) throws IOException,
			ClassNotFoundException {
		throw new NotSerializableException(this.getClass().getName());
	}	

	/**
	 * Resolves dependency references.  This method must be called on a root
	 * node with an empty name.  The child nodes of the root are the top level
	 * packages and paths specified in the config.  The dependencies may contain
	 * relative paths (./ and ../) and they will be normalized by calling 
	 * {@link PathUtil#normalizePaths(String, String[])}.  Note that relative 
	 * paths cannot be used to jump across top level child nodes (i.e. each 
	 * child of root acts as a virtual root for the purpose of normalizing
	 * path names. 
	 * <p>
	 * After calling this method, you may call {@link getExpandedDependencies}
	 * on any node which is rooted at this node in order to get it's expanded
	 * dependencies.
	 */
	public void resolveDependencyRefs() {
		normalizeDependencies();
		super.resolveDependencyRefs();
	}
	
}