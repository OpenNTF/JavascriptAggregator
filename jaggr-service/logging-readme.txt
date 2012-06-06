To enable logging on Domino:
 * Edit Domino/data/domino/workspace/.config/rcpinstall.properties
 * Add 'com.ibm.domino.servlets.aggrsvc.level=FINEST' to the end of the file (no quotes)
 * Restart the http task
 * Open Domino/data/domino/workspace/logs/trace-log-0.xml in a browser (IE/FF) and refresh for new output as needed.