/*
 * (C) Copyright IBM Corp. 2012, 2016
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
public class JSONObject {

	final org.apache.wink.json4j.JSONObject wrapped;

	public JSONObject() {
		wrapped = new org.apache.wink.json4j.JSONObject();
	}

	public JSONObject(String str) throws JSONException {
		try {
			wrapped = new org.apache.wink.json4j.JSONObject(str);
		} catch (org.apache.wink.json4j.JSONException e) {
			throw new JSONException(e);
		}
	}

	public JSONObject(org.apache.wink.json4j.JSONObject toWrap) {
		wrapped = toWrap;
	}

    public int getInt(String key) throws JSONException {
    	try {
    		return wrapped.getInt(key);
    	} catch (org.apache.wink.json4j.JSONException e) {
			throw new JSONException(e);
		}
    }

    public boolean has(String key) {
    	return wrapped.has(key);
    }

    public JSONObject getJSONObject(String key) throws JSONException {
    	try {
    		return new JSONObject(wrapped.getJSONObject(key));
    	} catch (org.apache.wink.json4j.JSONException e) {
			throw new JSONException(e);
		}
    }

    public JSONArray getJSONArray(String key) throws JSONException {
    	try {
    		return new JSONArray((wrapped.getJSONArray(key)));
    	} catch (org.apache.wink.json4j.JSONException e) {
			throw new JSONException(e);
		}
    }

    public String getString(String key) throws JSONException {
    	try {
    		return wrapped.getString(key);
    	} catch (org.apache.wink.json4j.JSONException e) {
			throw new JSONException(e);
		}
    }

    public org.apache.wink.json4j.JSONObject getWrappedObject() {
    	return wrapped;
    }
}
