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

The JavaScript Aggregator is an OSGi servlet that provides aggregation and code minification 
services to AMDs loaders that supports the loader extension API.  Features include:

* JavaScript minification using the Google Closure compiler
* Code trimming using has.js feature detection
* Require list expansion to reduce the cascade of requests resulting from dependency discovery as modules are loaded on the client
* CSS Optimizations
* i18n resource consolidation
* Caching of previously built/minified output for quicker response on subsequent requests

The Aggregator supports the Eclipse plugin extension architecture to allow the addition of support for:
* New types of resource repository locations on the server
* New types of module builders/minifiers
* New AMD loaders

See the [wiki](https://github.com/OpenNTF/JavascriptAggregator/wiki) for more details.

###OPENNTF###
This project is an OpenNTF project, and is available under the Apache License V2.0. All other aspects of the project, including contributions, defect reports, discussions, feature requests and reviews are subject to the [OpenNTF Terms of Use](http://openntf.org/Internal/home.nsf/dx/Terms_of_Use).

###UPGRADING###
####From 1.0.0 to 1.0.1####
* Resources defined in the server-side amd config file that use a "namedbundleresource" scheme url should leave the authority section of the uri blank and have the bundle name be the first segment in the path. (ex: namedbundleresource:///bundle.name/path/to/file)
####From 1.0.1 to 1.1####
* Interface change in IResource (added IResource resolve(String relative);) will affect any third-party resource providers. 
