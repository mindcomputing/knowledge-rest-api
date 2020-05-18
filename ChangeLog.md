Knowledge Rest Changelog 

This changelog summarizes changes and fixes which are a part of each revision.  For more details on the fixes, refer tracking numbers where provided, and the 
git commit history.  Note that this is not the same as the API Changelog.md.  This file will contain information on all changes - including bug fixes.  The API 
Changelog will only contain documentation on changes of the API - and those are tied to the API version number, not the release version number.


* 2020/04/29 - 7.31
    * remove some unused google auth token params.  Internal code alignment with auth app.

* 2020/04/21 - 7.30
    * If classify is requested during a release job, classify is now run twice - once before the promotion, and once again after the promotion.
        Previously, it only ran before.
    * Return release job name (target module) in release job results
    * Removed some unused / unsupported token options dealing with 3rd party SSO.

* 2020/04/08 - 7.29
    * Disconnected anon read-only mode from debug deploy mode.  anon read only mode is now only triggered by a config option.

* 2020/04/03 - 7.28
    * Configure for header logging - on by default in dev / test mode, off by default when deployed.
    * RestQAInfo now returns with terminology types populated
    * RestClassifierResult now includes term types for affected, equivalent, and orphaned concepts
    * Fix a bug where the content type was set wrong on the /export/artifact
    * Support reading ssoToken from cookie (which is set in the auth gui, upon login)

* 2020/03/25 - 7.27
    * Fixing bugs with the order of operations in content promotion, which lead to weird times on commits.
    * Fixing a bug that was being logged internally during classifier results readback
    * Added a fallback Language / Dialect to the stamp coordinate of english, whenever the primary stamp coordinate is set to 
        something other than english, to ensure that changing the language doesn't leave the UI without text.
    * Fixing bugs in the execution of write/1/release/releaseJob
    * Fixing default mapset field read behavior when a mapset has been saved without display field info
    * Fixing a bug in the classifier results write-back when there were no changes to commit
    * Finished hooking up the export rf2 APIs, standalone and release

* 2020/03/17 - 7.26
    * No longer require an edit token for access to write/1/userDataStore

* 2020/03/13 - 7.25
    * Corrected numerous bugs in genSCTIDs
    * Corrected various bugs in lucene indexes in underlying komet code

* 2020/03/06 - 7.24
    * Added the ability to specify the default edit module in the properties file KNW-210
    * Tweak the modules uses during concept create validation to align with our module changes
    * Add a few more QA rules to the QA processor for KNW-87
    * Fix issue with QA running on inactive concepts KNW-420
    * Sort the qaFailures in the RestQAResult by message

* 2020/02/28 - 7.23
    * Fixed a bug that prevented the classify flag from working in write/1/release/releaseJob
    * Fixed a bug in write/concept/create that made it not read semantic tags properly when a logic graph was passed (instead of a parent concept)
    * Improved the search API implementations, so that pages are filled accurately - previously, pages were filled in such a way that a state 
        filter could result in a page having less results than requested (even though there are still more remaining pages)
    * Fixed bugs with the handling of the coordinates for classification.
    * Numerous bug fixes with STAMP handling for releaseJob, as it wasn't maintaining the correct coordinates when run in a background thread.
    * Several breaking API changes - see API changelog.
    * Numerous fixes to documentation relating to release, export, and QA APIs - expecially places where some of the doc was missing from the 
        generated enunciate API doc.

* 2020/02/18 - 7.22
    * Fix a bug in the internal enum impl of RestSeverity
    * Fix a bug where the RestReleaseJobResult.exception field was typed as a RestException, instead of a string.

* 2020/02/03 - 7.21
    * Fixed a bug with approximate total calculation on search

* 2020/01/22 - 7.20
    * Adding release, qa APIs.  See API changes.
    * Changed the return types of the classifier APIs.  See API changes.
    * Internal changes to prevent incremental classification, improve memory recovery.

* 2019/12/17 - 7.19
    * upstream fixes for logging issues
    * improved performance on tree calculations
    * Fixed issues with the coordinates being passed for construction of /taxonomy/version, so that consistent coordinates are used 
        for all nodes of the tree returned.

* 2019/12/13 - 7.18
    * minor bug fixes, upstream updates
    * better handling of lucene parse errors

* 2019/11/22 - 7.17
    * Tweaked the handling of text query searches in the SearchAPIs, so that text that is passed that results in a lucene ParseException no longer 
        gets logged and handled as an unexpected internal error - rather, it is returned to the caller as a RestException with the details of the parse failure.

* 2019/11/07 - 7.16
    * Fix a bug in the createSemantics write API, where it wasn't handling the duplicate prevention and state change logic properly, when
        the semantic was defined as a dynamic semantic, rather than a static member semantic.  It now works properly for both styles of refsets.
    * Show a Queued status for classifications, rather than just running, for multiple parallel classifications

* 2019/10/30 - 7.15
    * Fix bug that prevented terminologyTypes from being populated on parent concept versions of a concept chronology.
    * Fix the ranking of description lookup, so that descriptions for concepts (such as those returned on logic graph nodes) will prefer 
        active descriptions, over inactive discriptions, when multiple description match the coordinates in use.
    * Fix the ranking of the text returned with lucene search API calls, when mergeOnConcept is true - such that the text returned now comes 
        from the highest scoring search result, rather than an arbitrary one that matched from the concept.  Furthermore, when multiple results
        have the same score, active descriptions are prioritized over inactive ones.
     * Upstream and local changes to fix issues with new refset concepts not being indexed properly when they live outside the metadata tree.

* 2019/10/18 - 7.14
    * Only run classifier over active content, instead of all state content.
    * Change the conceptWrite implementation, so that is uses active-only stamps for calculating semantic tags, term types, and description types.
    * Change the validation findFQN method so it also uses active stamps for calculating semantic tags, for its check.

* 2019/10/16 - 7.13
    * API enhancments per the API log.

* 2019/09/27 - 7.12
    * Fixed a bug where versionsAll wasn't sorting Concept versions correctly.
    * "No desc for nid" messages should no longer come through the API, in general.  If they do, we may need to examine each instance as a bug - but in the meantime, 
        the wording has been changed to "[Description not available for <the UUID>]"

* 2019/09/18 - 7.11
    * More forgiving behavior on Semantic Write APIs, see API changelog.  More unit tests for issues that cropped up (and fixes for those issues)
    * Cleanup old abandoned classifier runs, so that don't show as running, if they were actually aborted due to shutdown.
    * Prevent multiple executions of the classifier in parallel - now they queue.

* 2019/09/11 - 7.10
    * Fix a bug with not accepting null for referenced component restrictions on semantic assemblage definition.

* 2019/09/10 - 7.09
    * Fix the semantic definition list API so it doesn't break on an inactive concept, and make it not fail entirely if a single definition can't be read.

* 2019/09/10 - 7.08
    * Fix exception serialization, so that if the user asked for an XML response, if an error occurs, the error is serialized in xml (instead of json)
    * Send the correct error, if a caller tries to add a member to a concept that isn't configured as a semantic assemblage.

* 2019/09/09 - 7.07
    * Fix a null pointer I accidently introduced when it executes in docker.

* 2019/09/09 - 7.06
    * Fix a bug that disallowed comments from being returned per the API doc with the getMappingSets call.
    * Improved data checking for semantic write / update calls to validate data columns / types against spec
    * Better support for static semantics
    * New API calls for semantic type listing
    * improved behavior of membership semantic write APIs in dealing with duplicates

* 2019/08/19 - 7.05
    * Fixing a number of timing bugs with writes on mapsets, associations, and other issues with rapid writes
    * Fixed the bug that prevented the classifier from running
    * API enhancements, per the API changelog

* 2019/07/29 - 7.04
    * Fixing a bug with token handling when passing the system token

* 2019/07/18 - 7.03
    * Cleaning up some warnings and errors in the logs
    * Fix more issues with logic graph versions and timestamps.  Add special handling for metadata refs, update documentation to describe behavior.
    * API enhancments per the API log.

* 2019/07/16 - 7.02
    * Fixing bugs with specific version reads of term types and logic graphs
    * Added a missing description field to RestFeatureNode per the api changelog
    * Fixing more bugs with version reads of logic graphs - adding special behavior for metadata concepts so they aren't excluded when they are newer
        than the content being returned in the logic graph.

* 2019/07/15 - 7.01
    * Added support for passing through systemTokens to remoteAuth.
    * Fixed a bug that prevented ssoToken cacheing from working properly, leading to too many requests to the auth server.

* 2019/07/09 - 7.00
    * Corrected documentation for expand params on /logicGraph/chronology calls.
    * Added validation / error checking to expand parameter values, so it will now flag ones that are unknown and/or unsupported by a method.
    * Corrected / enhanced documentation on versionAll expansion options.  Fixed implemenation issues with versionAll, so that nested or referenced
        components are populated with a version that corresponds to the version of the requested item being returned.
    * First release that requires Java 11 or newer.

* 2019/06/07 - 6.27
    * Fixed a bug where it wasn't rejecting /write/ requests that were missing an edit token with the correct error message.
    * Fixed a bug (upstream) where the index wasn't flagging metadata properly, leading to prefixSearchs with a metadata restriction to not work properly.
        Existing databases must be re-indexed with the latest code, to start working properly.  .../rest/write/1/system/rebuildIndex?editToken=.....
    * Fix a bug that prevented altIDs from being populated for the referenced component in certain semantic calls.

* 2019/04/19 - 6.26
    * API enhancements per the API changelog
    * Fixed a bug with paged result counts when filtering off path results

* 2019/03/28 - 6.25
    * Lots of internal changes to migrate auth over to the new uts-auth-api code.
    * Used a more standard approach for validating authentication
    * Cleanup of property / config file naming.  The file to configure this server is now uts-rest-api.properties.  src/test/resources contains a 
        documented example.
    * Allowed anonymous read to the system/systemImfo call even when anonymous read is disabled elsewhere - this may be changed again in the future.
        But before that can happen, we need to have keys for a AUTOMATED user populated into the web-editor.

* 2019/02/06 - 6.24
    * added an api for storing user prefs, etc

* 2019/01/25 - 6.23
    * fixing the classifier return results
    * better debug output on classification runs

* 2019/01/24 - 6.22
    * updates to classifier results handling

* 2019/01/22 - 6.21
    * Fixed bug with terminology type lookup (in core)
    * Fixed bugs with DB build process that was leading to runtime errors and bad hierarchy on certain concepts
    * API tweaks to createConcept, to make it more permissive during semantic tag creation with snomed concepts that co-exist in metadata.
    * Fix a bug with mapset update where we mis-handled the optional displayField and failed to process an update if it wasn't provided.
    * Handling for more ID types, new options for returning alternate IDs.

* 2019/01/10 - 6.20
    * Fix a regression with the handling of name, inverse name and description on mapsets, related to external description types.
    * Fix a bug with update of mapping items where null's in the data arraylist lead to a null pointer.

* 2018/12/31 - 6.19
    * Reworking the storage of classifier results, to improve performance.
    * API additions for management of stored classifier data

* 2018/12/28 - 6.18
    * API updates to the classifier API per the API log.

* 2018/12/28 - 6.17
    * API additions per the API change log
    * Bug fixes for testing with local DEFAULT user.

* 2018/12/14 - 6.16
    * API tweaks in the LogicGraph returns, per the API changelog
    * Fix a bug with parents / parent counts in search results.

* 2018/11/20 - 6.15
    * improve description reading for RestIdentifiedObjects, so it follows the coord prefs better
    * small API changes to hierarchy under RestLogicNode.  See API changelog.
    * API enhancements to support logic graph editing.

* 2018/10/30 - 6.14
    * Performance improvements from a new version of core.
    * Switch over to new non-tree APIs for most hierarchy calls.
    * Add a request ID to the logs for easier tracking
    * Add a slow query log

* 2018/10/29 - 6.13
    * Fixed a regression with coordinates on the initial edit
    * Update tests to run tests for the beer ontology using the correct module
    * Add concept create validations for UTS-154
    * Bugfixes from a new version of core

* 2018/10/23 - 6.12
    * Added support to change the return type to xml or json by appending a file extension (.json or .xml) to the request
    * Fixed a bug where several SystemAPI methods were not honoring (or allowing) individual coordinate parameters, such as 'stated'
    * fix a bug with returning XML for RestIdentifiedObject
    * Fix a bug (in core) with handling external description types related to getting duplicate external types.

* 2018/10/15 - 6.11
    * non-breaking API enhancements per the API change log - 1.17.2 release
    * removed unused and inoperative 'expand' type of 'descriptions'
    * bugfixes from new relase of core

* 2018/10/05 - 6.10
    * Include bug fixes from core

* 2018/10/02 - 6.09
    * Mostly changes from upstream - there were a number of changes to how the metadata was structured, and changes to how the 
       yaml constants are generated.  

* 2018/08/30 - 6.08
    * Fix naming conventions / metadata reading to align with upstream changes.

* 2018/08/24 - 6.07
    * Just an internal build

* 2018/08/23 - 6.06
    * Fix startup sequence bug related to git config

* 2018/08/22 - 6.05
    * Build for testing jenkins pipelines, update core code.

* 2018/08/01 - 6.04
    * Support encrypted passwords in the prisme.properties file.  Improve internal handling of prisme.properties, restore ability to download
        a DB at startup time.
    * Fix bug with finding the metadata to log during startup
    
* 2018/07/23 - 6.03
    * Just an internal build

* 2018/07/23 - 6.02
    * Fix bugs with dialect parsing, so it handles more than just 'en' and 'us'.
    * All other changes in the API change log, since the last major update.

* 2018/??/?? - 6.01
    * An unofficial release with changes for moving over to the oshera isaac.