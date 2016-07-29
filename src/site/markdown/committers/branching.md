<!--
   (C) Copyright IBM Corp. 2012, 2016

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
##Branching For Older Releases##

I recently had to create a branch for an older release in order to issue
fixes for it (jaggr-1.1.8).  We had moved on since to `jaggr-1.2.0-SNAPSHOT`
so I had to figure out the best way to do it.  These are the steps that
worked for me at the time.

First, I created a branch called `staging-jaggr-1.1.x` from the `jaggr-1.1.8`
tag to stage the change. For some reason this process wouldn't work if I
simply checked out the tag.

```    
    git checkout -b staging-jaggr-1.1.x jaggr-1.1.8
```

Next, I used maven to create the `jaggr-1.1.x` branch following
[these maven:release instructions](http://maven.apache.org/maven-release/maven-release-plugin/examples/branch.html).

```
    mvn release:branch -DbranchName=jaggr-1.1.x -DupdateBranchVersions=true -DupdateWorkingCopyVersions=false
```

Finally, I deleted the local branch `staging-jaggr-1.1.x` as well as the
remote one that was pushed to the parent project.
