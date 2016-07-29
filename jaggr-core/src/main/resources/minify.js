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

define([], function() {
	return function(commentIgnores /* array of comment strings to leave alone */) {
		return function (css) {
			
			var trim = function(val) {
				// removes extra spaces from val, respecting quoted strings.
				val = val.replace(/^\s+/, '');
				val = val.replace(/\s+$/, '');
				var toks = [], s = val, quoteChar = '', i;
				while (s) {
					var finish = true;
					for (i = 0; i < s.length; i++) {
						var ch = s.charAt(i);
						if (ch === '\\') {
							// escape char.  Swallow the next character then continue looping
							i++;
							continue;
						} 
						if (ch === quoteChar || quoteChar === '' && (ch === '"' || ch === '\'')) {
							toks.push(quoteChar + s.substring(0, i) + quoteChar);
							s = s.substring(i+1);
							quoteChar = quoteChar !== '' ? '' : ch;
							finish = false;
							break;
						}
					}
					if (finish) {
						toks.push(s);
						break;
					}
				}
				for (i = 0; i < toks.length; i++) {
					var tok = toks[i];
					if (tok.charAt(0) !== '"' && tok.charAt(0) !== '\'') {
						tok = tok.replace(/([(),:])\s+/g, '$1');
						tok = tok.replace(/\s+([),:])/g, '$1');
						toks[i] = tok;
					}
				}
				return toks.join('');	
			};
			
			css.before='';
			css.after=''; 
			css.eachDecl(function (decl) {
				decl.semicolon=false; 
				decl.before  = '';
				decl.between = ':';
				decl.value = trim(decl.value);
			});
			css.eachRule(function (rule) {
				rule.semicolon=false; 
				rule.before  = '';
				rule.between = '';
				rule.after   = '';
			}); 
			css.eachAtRule(function (atRule) {
				atRule.semicolon=false; 
				atRule.before  = '';
				atRule.between = '';
				atRule.after   = '';
				atRule.afterName = ' ';
				atRule.params = trim(atRule.params);
		
			}); 
			css.eachComment(function (comment) {
				var leaveAlone = false;
				if (commentIgnores) {
					// see if we should leave this comment alone
					for (var i = 0; i < commentIgnores.length; i++) {
						if (comment.text.indexOf(commentIgnores[i]) === 0) {
							leaveAlone = true;
							break;
						}
					}
				}
				if (!leaveAlone) {
					comment.removeSelf();
				} else {
					comment.before = (comment === css.first) ? '' : '\r\n';
				}
			});
		};
	};
});