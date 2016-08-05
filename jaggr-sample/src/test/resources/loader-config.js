/*
 * (C) Copyright IBM Corp. 2012, 2016 All Rights Reserved.
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
dojoConfig = {
	baseUrl: '/',
	
	has: {
		'dojo-combo-api': true,
		'dojo-undef-api': true
	},
	
	// Assumes the first package is the app package
	packages: [
		{name: 'dojo', location: 'target/WebContent/dojo/dojo'},
	],

	paths: {
		'js' : '/WebContent/js',
		'postcss': 'target/WebContent/postcss/postcss',
		'colorize': 'src/test/resources/postcssPlugins/colorize'
	},

	async: true
};