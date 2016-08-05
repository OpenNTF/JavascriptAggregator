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
package org.json;

/**
 * Minimal implementation of the org.json class using apache wink library to avoid issues with the
 * org.json license. This implementation provides only the functionality required by the
 * com.google.debugging.sourcemap.SourceMapConsumerV3 class.
 */
public class JSONArray {

	final org.apache.wink.json4j.JSONArray wrapped;

	public JSONArray() {
		wrapped = new org.apache.wink.json4j.JSONArray();
	}

	public JSONArray(org.apache.wink.json4j.JSONArray toWrap) {
		wrapped = toWrap;
	}

    public int length() {
    	return wrapped.length();
    }

    public String getString(int index) throws JSONException {
    	try {
    		return wrapped.getString(index);
		} catch (org.apache.wink.json4j.JSONException e) {
			throw new JSONException(e);
		}
    }

    public JSONObject getJSONObject(int index) throws JSONException {
    	try {
    		return new JSONObject(wrapped.getJSONObject(index));
		} catch (org.apache.wink.json4j.JSONException e) {
			throw new JSONException(e);
		}
    }

	public org.apache.wink.json4j.JSONArray getWrapppedArray() {
		return wrapped;
	}

}
