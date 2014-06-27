<!--
   (C) Copyright 2012, IBM Corporation

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

[![Build Status](https://buildhive.cloudbees.com/job/OpenNTF/job/JavascriptAggregator/badge/icon)](https://buildhive.cloudbees.com/job/OpenNTF/job/JavascriptAggregator/)

The JavaScript Aggregator is an OSGi servlet that provides aggregation and code
minification services to AMDs loaders that supports the loader extension API.
Features include:

* JavaScript minification using the Google Closure compiler
* Code trimming using has.js feature detection
* Require list expansion to reduce the cascade of requests resulting from
  dependency discovery as modules are loaded on the client
* CSS Optimizations
* i18n resource consolidation
* Caching of previously built/minified output for quicker response on
  subsequent requests

The Aggregator supports the Eclipse plugin extension architecture to allow the
addition of support for:
* New types of resource repository locations on the server
* New types of module builders/minifiers
* New AMD loaders

See the [wiki](https://github.com/OpenNTF/JavascriptAggregator/wiki) for more
details.

###OPENNTF###
This project is an OpenNTF project, and is available under the Apache License
V2.0. All other aspects of the project, including contributions, defect
reports, discussions, feature requests and reviews are subject to the
[OpenNTF Terms of Use](http://openntf.org/Internal/home.nsf/dx/Terms_of_Use).

###HELP###
For help with this project, please visit the 
[discussion forum](https://groups.google.com/forum/?fromgroups#!forum/jaggr) or
ask a question on [Stack Overflow](http://stackoverflow.com/) with the tag 
`jaggr`. *Remember to check the 
[wiki](https://github.com/OpenNTF/JavascriptAggregator/wiki).*

###UPGRADING###

####1.2.0####
This is a major upgrade.  [Read what it brings](https://github.com/OpenNTF/JavascriptAggregator/wiki/What%27s-new-in-Version-1.2).

We've created a new bundle, `jaggr-core`, to house all non-osgi specific code.
This is now where the vast majority of jaggr code is located. Bundle 
dependencies may need to be updated.

Besides the name changes, there are some interface changes affecting aggregator
extensions.  Extensions developed for previous versions of the aggregator won't
run on 1.2 without changes.

Developers running from within eclipse should use a java7 jre/jdk for 
development and running the bundles out of the eclipse workspace. Maven will 
generate java6 compatible jars from the command line.  

####1.1.8####
The changes described below don't affect casual users. Only implementors of
JAGGR extensions that deal with calculation of expanded dependencies are 
affected:

* com.ibm.jaggr.service.deps.IDependencies.getExpandedDependencies() has been
  removed. Use com.ibm.jaggr.service.util.DependencyList instead.
* com.ibm.jaggr.service.util.DependencyList constructor arguments have changed.

####1.1.1####
* Resources defined in the server-side amd config file that use a 
  "namedbundleresource" scheme url should leave the authority section of the
  uri blank and have the bundle name be the first segment in the path. 
  (ex: namedbundleresource:///bundle.name/path/to/file)
* Interface change in IResource (added IResource resolve(String relative);) 
  will affect any third-party resource providers.

