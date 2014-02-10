package com.ibm.jaggr.core.impl.resource;

import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceFactory;
import com.ibm.jaggr.core.resource.IResourceVisitor;
import com.ibm.jaggr.core.resource.IResourceVisitor.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;

/**
 * This is a wrapper class for other IResource objects that need special handling of the
 * {@link IResource#resolve(String)} method. It is intended for use with derived, or cache,
 * resources where invoking {@link URI#resolve(String)} on the URI associated with the resource
 * provides either an invalid URI, or a URI to a resource that may not exist.
 * <p>
 * Instead, the {@link #resolve(String)} method invokes {@link URI#resolve(String)} on a separate,
 * resolvable, URI (provided to the object constructor) and uses the provided factory object's
 * {@link IResourceFactory#newResource(URI)} method to construct the resolved resource.
 */
public class ResolverResource implements IResource {

	final IResource res;
	final URI resolvableUri;
	final IResourceFactory factory;

	public ResolverResource(IResource res, URI originalUri, IResourceFactory factory) {
		this.res = res;
		this.resolvableUri = originalUri;
		this.factory = factory;
	}

	@Override
	public URI getURI() {
		return res.getURI();
	}

	@Override
	public boolean exists() {
		return res.exists();
	}

	@Override
	public long lastModified() {
		return res.lastModified();
	}

	@Override
	public IResource resolve(String relative) {
		return factory.newResource(resolvableUri.resolve(relative));
	}

	@Override
	public Reader getReader() throws IOException {
		return res.getReader();
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return res.getInputStream();
	}

	@Override
	public void walkTree(IResourceVisitor visitor) throws IOException {
		res.walkTree(visitor);

	}

	@Override
	public Resource asVisitorResource() throws IOException {
		return res.asVisitorResource();
	}

}
