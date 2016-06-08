/*
 * (C) Copyright 2014, IBM Corporation
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

/*
 * Implementation of less parser fileLoader function used to load files by @import
 * statements.  Calls the global readFileExt function implemented in Java by 
 * JAGGR.
 */
less.Parser.fileLoader = function (file, currentFileInfo, callback, env) {
    currentFileInfo = currentFileInfo || {};
    var params = {
    	file: file,
    	ref: currentFileInfo.filename
    };
    
    var data = null;
    try {
    	/*
    	 * readFileExt() updates params.ref to be the fully qualified file name of the
    	 * file that was loaded.  In the event of an exception being thrown, params.ref
    	 * should specify the name of the file that the function attempted to load.
    	 */
        data = readFileExt(params);
    } catch (e) {
        callback({ type: 'File', message: "'" + params.ref + "' wasn't found" });
        return;
    }

    var newFileInfo = {
        entryPath: currentFileInfo.entryPath,
        rootFilename: currentFileInfo.rootFilename,
        rootpath: currentFileInfo.rootpath,
        currentDirectory: less.modules.path.dirname(params.ref) + '/',
        filename: params.ref
    };

    try {
        callback(null, data, params.ref, newFileInfo, { lastModified: 0 });
    } catch (e) {
        callback(e, null, href);
    }
};