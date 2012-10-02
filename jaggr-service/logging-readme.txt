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

This file specifies options for the AMD module aggregator servlet.  To customize
settings, place a copy of this file in the home directory for the user that runs the 
server and edit the settings below.  

To enable logging on Domino:
 * Edit Domino/data/domino/workspace/.config/rcpinstall.properties
 * Add 'com.ibm.domino.servlets.aggrsvc.level=FINEST' to the end of the file (no quotes)
 * Restart the http task
 * Open Domino/data/domino/workspace/logs/trace-log-0.xml in a browser (IE/FF) and refresh for new output as needed.