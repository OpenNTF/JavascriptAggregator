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

The JavaScript Aggregator (JAGGR) is an OSGi servlet that provides aggregation and code minification 
services to AMDs loaders that supports the loader extension API.  Features include:

* JavaScript minification using the Google Closure compiler
* Code trimming using has.js feature detection
* Require list expansion to reduce the cascade of requests resulting from dependency discovery as modules are loaded on the client
* CSS Optimizations
* i18n resource consolidation
* Caching of previously built/minified output for quicker response on subsequent requests

The Aggregator supports the Eclipse plugin extension architecture to allow the addition of support for:
* New types of resource repositories locations on the server
* New types of module builders/minifiers
* New AMD loaders

See the [wiki](/OpenNTF/JavascriptAggregator/wiki) for more details.