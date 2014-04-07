package com.ibm.jaggr.core.transport;

import com.ibm.jaggr.core.BadRequestException;

import java.util.List;

/**
 * This interface encapsulates the modules specified in the request which should be included in the
 * response. The response may also contain nested dependencies of these modules, depending upon
 * configuration and options.
 * <p>
 * The result returned by the {@link #toString()} method is intended to be used as the cache key for
 * layers matching the same requested modules and should not require decoding of the encoded modules
 * names to produce.
 *
 */
public interface IRequestedModuleNames {

	/**
	 * Returns the list of modules requested by the Aggregator. These are modules requested by
	 * Aggregator generated requests and typically require decoding of the request information. As a
	 * performance optimization, the decoding of the request may be deferred until this method is
	 * called in attempts to try and avoid decoding if the request is already cached and the cached
	 * layer can be located using the value returned by {@link #toString()}.
	 *
	 * @return the requested modules.
	 * @throws BadRequestException
	 */
	public List<String> getModules() throws BadRequestException;

	/**
	 * Returns the list of modules specified by the <code>deps</code> request parameter. Deps are
	 * specified in application generated requests to load a boot layer. Dep modules are included in
	 * the boot layer and are automatically required by the loader. The name of this property was
	 * chosen so as to mirror the <code>deps</code> AMD config property.
	 *
	 * @return the list of dep modules
	 * @throws BadRequestException
	 */
	public List<String> getDeps() throws BadRequestException;

	/**
	 * Returns the list of modules specified by the <code>preloads</code> request parameter.
	 * Preloads are specified in application generated requests to load a boot layer. Preload
	 * modules are included in the boot layer but are not activated until required by the
	 * application.
	 *
	 * @return the list of preload modules
	 * @throws BadRequestException
	 */
	public List<String> getPreloads() throws BadRequestException;

	/**
	 * Returns the list of script modules specified by the <code>scripts</code> request parameter.
	 * Script modules are specified in application generated requests to load a boot layer. Script
	 * modules are non-AMD modules that are included at the beginning of a boot layer. These
	 * typically include the AMD loader config, the Aggregator loader config extension and the AMD
	 * loader. It can also include any other non-AMD script files needed by the application, as long
	 * as the file can be located using the AMD configuration.
	 *
	 * @return the list of script modules
	 * @throws BadRequestException
	 */
	public List<String> getScripts() throws BadRequestException;

	/**
	 * @return a (possibly encoded) string representation of the requested modules.
	 */
	@Override
	public String toString();

}
