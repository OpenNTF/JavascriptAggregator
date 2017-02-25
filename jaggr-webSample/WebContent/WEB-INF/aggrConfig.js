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

// This file is AMD Aggregator config for the Aggregator sample application
{
	baseUrl:'jaggrWeb',	
	
	packages: [
		{
			name: 'dojo',			
			location: 'classpath:///WebContent/source_1.8.0-20120830-IBM_dojo/dojo'			
		},
		{
			name: 'dijit',
			location: 'classpath:///WebContent/source_1.8.0-20120830-IBM_dojo/dijit'			
		},
		{
			name: 'dojox',			
			location: 'classpath:///WebContent/source_1.8.0-20120830-IBM_dojo/dojox'			
		}
		
	],
	paths: {
	},
	
	textPluginDelegators: ["js/css"],
	
	deps: ["js/bootstrap"],

	depsIncludeBaseUrl: true,		// include baseUrl in scan for dependencies

	inlinedImageSizeThreshold: 0,	// max image size to inline in CSS
	
	lazyDetection: false,

	//notice: 'embed.txt',

	cacheBust: '${project.version}' // This is a maven variable, replaced at build time (see pom file for filtered resources).
		// You have other options for variable replacement in this file such as "scoped-variables" (see wiki for more info)
}
