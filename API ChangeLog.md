Knowledge Rest API Changelog 

Any time a code change is made that impacts the API returned to callers, increment the value in API ChangeLog.md (here), and in RestSystemInfo

During development, we can increment this, so long as our client code (knowledge-web-editor) is aware of the changes.

After we gain outside customers on the API, any change should be done by bumping the major version - and creating new rest paths (/rest/2/, /rest/write/2/)
If reverse compatibility is required to be maintained, then the rest/1 or rest/write/1 code must remain.

Until that happens, however - we follow the procedure of bumping the release version if it is an API change that won't break KOMET code - such as adding a new 
parameter.  However, any change that will break KOMET code - such as changing the operation of an existing method (which KOMET uses) - then the minor revision 
(and/or the major revision) should be bumped, to force a KOMET runtime breakage due to the incompatibility. 

Bug fixes should not be documented here, rather, than should be documented in the changelog file.

*** Don't forget to update the value in the class RestSystemInfo ***

* 2020/04/21 - 1.24.5
	* Added releaseName to RestReleaseJobResult object.

* 2020/04/08 - 1.24.4
    * Added 'inUse' flag to id/ids call.  Useful for populating dropdowns like a search dialog.

* 2020/04/03 - 1.24.3
    * All reasons for an auth fail should now come back with a status code of 401 (unauthorized) instead
        of bad request errors
    * Add write/1/system/clearTokens to allow clearing of cached user tokens

* 2020/03/25 - 1.24.2
    * 'time' components of the read coordinate can now (also) be passed as an ISO-8601 string (to any call that takes 'time'
    * Added 1/system/sctTerminologyTypes to return the terminology types that should follow SNOMED content rules

* 2020/03/17 - 1.24.1
    * Fix issues with read-only access to user store APIs

* 2020/03/13 - 1.24.0
    * Corrected the following API calls from GET to POST:
            write/1/release/genSCTIDs
            write/1/release/promote
            write/1/release/releaseJob
    * Changed RestEditCoordinate variable naming so they don't conflict with ruby reserved words.


* 2020/03/06 - 1.23.2
    * Added write/1/system/defaultEditModule to allow changing the default edit module at runtime
    * Made the id parameter optional in /coordinate/editModule, to allow for getting back the default edit coordinate module
    * Added coordinate/editCoordinate to allow for parsing an edit token

* 2020/02/28 - 1.23.0
    * Added a qa flag to write/1/release/releaseJob
    * Added namespace and releaseFileAvailable to the RestReleaseJobResult object
    * Added skipAssemblage parameter to /semantic/forReferencedComponent
    * made releaseDate optional on /export/rf2 and write/1/release/releaseJob
    * Added defaultCoordinates flag to write/1/classifier/classify.  Change the documentation / default behavior for coordinates WRT classification
    * Added specific KEEP and NOW options to the commitTime variable for write/1/release/promote and write/1/release/releaseJob
    * Clarified, and in some places changed the behavior of how the edit coordinate is used for various aspects of release / promote.
    ** BREAKING ** RestMappingItemVersion.qualifierConcept was renamed to equivalenceTypeConcept
    ** BREAKING ** RestMappingItemVersionUpdate.qualifierConcept was renamed to equivalenceTypeConcept
    ** BREAKING ** removed parentModule from write/1/release/promote and write/1/release/releaseJob

* 2020/02/18 - 1.22.2
    * Added componentConcept to RestQAInfo to carry back info about the nearest concept
    * Made the failureContext variable of RestQAInfo required.
    * Allow altId to be passed as a parameter to qa/run/{id} and qa/runs
    * Added skipResults parameter to classifier/classifications
    * Added largeResults parameter to qa/run/{id} and qa/runs
    * Added skipResults parameter to qa/runs
    * Added qaFailureCount to RestQAResult (populated even when skipResults is true)
    * Added stampCoordinate, logicCoordinate and writeStamp to RestClassifierResults 
    * Finished write/1/release/genSCTIDs 

* 2020/02/03 - 1.22.1
    * Fix bugs in the Query API and the Taxonomy API where we returned paged results - approximate result count wasn't behaving properly in all cases, 
        and the flag for approximate is exact was wrong in several cases.
    * Added a missing sort to the system API for returning modules.
    * Added write/1/system/clearReleaseJobData
    * Finished export/artifact
    * Finished write/1/release/promote
    * Finished write/1/release/releaseJob
    * Finished release/run/{id}
    * Finished release/runs

* 2020/01/22 - 1.22.0
    * Adding new APIs for content QA, export, and promotion.
    * Added export/rf2 (in progress)
    * Added export/artifact (in progress)
    * Added qa/run/{id}
    * Added qa/runs
    * Added write/1/qa/run
    * Added write/1/release/genSCTIDs (in progress)
    * Added write/1/release/promote (in progress)
    * Added write/1/release/releaseJob (in progress)
    * Added write/1/system/clearQAData
    * Added release/run/{id} (in progress)
    * Added release/runs (in progress)
    * Changed RestClassifierCycle.conceptWithCycle, RestClassifierCyclePath.cyclePath, RestClassifierEquivalentSet.equivalenConcepts, 
        RestClassifierResult.affectedConcepts, RestClassifierResult.orphanedConcepts from RestIdentifiedObject to RestConceptChronology.

     API calls denoted with (in progress) don't work yet...

    
* 2019/10/18 - 1.21.2
    * Change the concept Write implementation, such that if a semantic tag is provided, in combination with the calculate semantic tags flag, it uses the provided
        tag - even in cases where it couldn't calculate a tag (and previously would have failed)

* 2019/10/16 - 1.21.1
    * Change RestConceptCreateData so that the parentConceptIds field is now optional, and a new optional field is allowed, semanticLogicGraph.  Concept 
        create now requires either the parentConceptIds, or the semanticLogicGraph.  One or the other, not both.
    * Description searches now have better handling for queries that contain [bracketed] text, {braced} text, or : or ^ characters.
    * Search APIs now support the option applyStampToConcept (which is enabled by default) so when searching active only, it won't match on active descriptions
        that are attached to inactive concepts
    * Search APIs now support an expand option of 'terminologyTypes' - breakage warning - terminologyTypes expansion will now be off by default, unless you 
        request this expand parameter.
    * Added support to /concept/chronology for the parameters includeParents, countParents, includeChildren, countChildren, and semanticMembership - all of which
        are only applicable when versions are being returned along with the chronology.

* 2019/09/27 - 1.21.0
    * Updated validation/findFQNs so that it ignores the semantic tag (SOLOR) when computing a semantic tag from parents, if there is more than one 
        semantic tag, to align with the behavior of the concept create API.
    * Changes the behavior of the 'versionAll' flag on all APIs that take it - so that an extra version is now returned in position 0 of the list, which 
        is rendered with all nested or referenced components using the latest stamp (or the stamp specified in the call, if any).  This makes the position 0
        item in the version list the same, as if you had requested 'versionsLatestOnly'.  The version in position 1 will be the same version as position 0, 
        for the primary item being returned, but all nested or referenced components that are returned will be rendered with a stamp relative to the stamp of
        the version being returned.  All addition versions behave the same way (which is the way it previously behaved).  
        This change impacts ConceptChronology read api, logic graph read api, semantic chronlogy read api, all search APIs, and the system API for reading
        objects.
    * The following fields may now be null, if no description is available on the stamp (previously, these fields got a "no desc for nid" message):
        RestConceptNode.conceptDescription
        RestFeatureNode.measureDescription
        RestSemanticLogicGraphVersion.referencedConceptDescription
        RestTypedConnectorNode.connectorTypeDescription
     * concept/descriptions now sorts the descriptions and dialects per the new documentation on the method

* 2019/09/18 - 1.20.10
    * Make semantic write APIs more forgiving to minor column type mismatches.  If a column that should be a boolean gets deserialized from rest as a string, 
        it will automatically be corrected - for example.  This also applies to most numeric types.  Additional, if a column is specified as optional, but an 
        empty string is passed in, it will automatically change it to null internally - as this can help eliminate issues with validators that don't allow 
        empty strings (but do allow optional columns) 

* 2019/09/10 - 1.20.9
    * Added exceptionMessages to RestDynamicSemanticDefinitionPage to carry back any errors that happened while building the set of SemanticDefinitions
        which aren't fatal, but affect overall results.
    
* 2019/09/10 - 1.20.8
    * Added existingConceptId to RestDynamicSemanticTypeCreate to allow the configuration of an existing concept as a semantic assemblage.
    * Added semanticStyle to RestConceptChronology to provide the general semantic style, iff a concept defines a semantic assemblage.
    * Added semanticStyle to RestDynamicSemanticDefinition
    
* 2019/09/09 - 1.20.7
    * Added semantic/semanticDefinitions to support listing semantic types in the system.  Allows filtering to mapsets, refsets, property or association types.
    * Added assemblageConceptVersion field to the RestDynamicSemanticDefinition object
    * Changed the behavior of the createSemantics batch write method, so that when creating refset members, if a duplicate refset member would be created, 
        the duplicate will be ignored.  If a duplicate would be created with a different state, the entry will be treated as an update, instead of a create.
        This only applies for member refset semantics.
    * Changed the behavior of the createSemantics write method, so that when creating refset members, if a duplicate refset member would be created, 
        A 419 (Conflict) error will be thrown.  If a duplicate would be created with a different state, the entry will be treated as an update, instead of a create.
        This only applies for member refset semantics.

* 2019/08/19 - 1.20.6
    * Added write/semantic/semantics/create/ to support creation of semantic instance items in bulk.
    * Added write/association/items/create/ to support creation of association instance items in bulk.
    * Added write/mapping/mappingItems/create/ to support creation of semantic instance items in bulk.
    * Added additionalUUIDs to the RestWriteResponse object, to carry UUIDs of additional items created during a write, if more than one 
        is created.
    * Added POST variations for /search/descriptions and /search/semantics which can be used instead of GET, to enable queries
        with characters that are illegal in a URI, without requiring encoding.

* 2019/07/18 - 1.20.5
    * Added measureDescription to the RestFeatureNode, to provide a convenient way to get the description for the measure concept.

* 2019/07/15 - 1.20.4
    * Removed deprecated / non-functional Workflow parameters
    * Added more system config info into to the systemInfo call.
	
* 2019/06/07 - 1.20.3
    * Added support for 'refset' and 'property' as "restrictTo" criteria on /search/prefix
    * Added concept/versions/{id} which returns an array of all unique stamps on the concept and any nested semantics.
    * Added an optional boolean parameter of includeAllVersions to /concept/descriptions/{id}, /semantic/forAssemblage/{id}, 
        /semantic/forReferencedComponent/{id}
    * Added support for FHIR Terminology API calls.  FHIR calls are under http://<server>:<port>/fhir/r4/....  See 
        http://hl7.org/fhir/R4/terminology-module.html.

* 2019/04/19 - 1.20.2
    * Added REST API support for query/flowr.  XML formatted FLOWR queries can be submitted, and returned as XML results. 
    * Added write/1/intake/ibdf for posting / uploading an IBDF file, or a zipped IBDF file.
    * Added write/1/intake/ibdfArtifact for telling the server to go fetch an IBDF file from the artifact server, and import it.
    * Added sortFull option to 1/taxonomy/version call, which when set to true, will sort the entire result prior to breaking the result
        into pages.  This allows the sorting to be consistent from one page to the next.

* 2019/03/28 - 1.20.1
    * Moved the codebase over to depending on the uts-auth-api.  No longer works against prisme.
    * Removed warId from the RestSystemInfo
    * Removed some role attributes from the Workflow APIs (but those aren't functional right now as it is)
    * Required edit permissions, or higher, in order to request an edit token

* 2019/02/06 - 1.19.8
    * Added a user data read/write APIs for clients to use to persist arbitrary data - intended to be used for storing json formatted user prefs, 
        etc, the sorts of things a GUI needs to be able to store to persist user options. 

* 2019/01/25 - 1.19.7
    * Reworked the cycle path and equivalent set part of the classifier returns, because the generated enunciate code couldn't handle arrays 
        nested in lists.

* 2019/01/24 - 1.19.6
    * Added processedConceptCount to the RestClassifierResult, and changed the data that is put into affectedConcepts and affectedConceptCount.

* 2019/01/22 - 1.19.5
    * Tweak the concept create API so that the automated semantic tag creation doesn't fail if there are two semantic tags, and one 
          of them is the metadata tag "SOLOR".  The metadata tag will be ignored, using the more specific tag.
    * Added the optional parameter altId - to most get methods that return RestIdentifiedObject structures (directly or nested).  
        When specified (with one or more values) from /1/id/types or the value 'ANY' then those ids will also be popualted in each of the 
        RestIdentifiedObjects returned.  This option is now available on most calls under /association/, /classifier/, /concept/, /logicGraph/,
        /mapping/, /search/, /semantic/, /system/, /taxonomy/, /validation/.  The specific calls that accept the parameter have the parameter
        documented. 
    * /id/* methods were enhanced to handle any type of system-flagged ID - all of the children of IDENTIFIER_SOURCE____SOLOR.

* 2018/12/31 - 1.19.4
    * Add the ability to clear the stored classifier run data via a rest call.

* 2018/12/28 - 1.19.3
    * Change the RestClassifierResult structure to add count variables for all of the fields that are arrays or lists.
    * Add a largeResults parameter to classifier readback APIs.  By default, arrays and lists are now trimmed to 100 results, unless
        largeResults=true is passed.

* 2018/12/28 - 1.19.2
    * Added system/descriptionTypes to fetch all description types, sorted and organized under the core types across all terminologies.
    * Added write/1/classifier/classify API for triggering a classification
    * Added /1/classifier/classifications and /1/classifier/classification/{id} for reading back in progress and completed classifications.
    * Added cycles and orphan information into classification result APIs (and added those checks into the classification process itself)

* 2018/12/14 - 1.19.1
    * Put back 'connectorTypeDescription' in the RestTypedConnectorNode, as the comment below in 2.18.1 was wrong - RestIdentifiedObject doesn't 
        reliably contain a description. 
    * Added an expand option of 'countParents' to the LogicGraph read API, which in combination with the existing 'version' expand parameter
        will cause a RestConceptVersion object to be returned inside of RestConceptNode and RestTypedConnectorNode, which has the parent
        count populated.
    * Removed isConceptDefined from both RestConceptNode and RestTypedConnectorNode, as this information is already included in the nested
        RestConceptVersion object, so long as expand='version' is passed in.
    * Updated the result of taxonomy/version so that if a parent count greater than 0 is requested, and children are requested, the immediate level 
        of parents for each child will be returned as well.
    * Added an expand option of  'includeParents' to the logicGraph/Version call.
    * Added an expand option of 'terminologyType' to the logicGraph/Version call.
    * Added expand options of 'countParents' and 'includeParents' to all of the search API calls.
    * Added more validation to prevent more invalid logic graph construction.

* 2018/11/20 - 1.18.1
    * Adding measureSemanticConcept to RestFeatureNode to align with internal API
    * Renaming RestUntypedConnectorNode to RestConnectorNode
    * Moving 'children' from RestLogicNode down into RestConnectorNode, so that it properly aligns with the API, and allows / doesn't 
      allow children in the right parts of the model.  Literals and concept nodes will no longer have an API that shows the possibility of children.
      Only RestConnectorNode and RestTypedConnectorNode objects may have children.
    * Removed 'connectorTypeConceptDescription' from RestTypedConnectorNode, and it only provided duplicate information already available in 
      the RestIdentifiedObject 'connectorTypeConcept'.
    * Added 1/write/logicGraph/create for updating and creating logic graphs.
    * Added 1/validation/logicGraph/ for testing the validity of a logic graph, without storing the passed logic graph.
    * Added 'isConceptDefined' to RestTypedConnectorNode

* 2018/10/23 - 1.17.2
    * Adding nextPriorityLanguageCoordinate and modulePreferences to the RestLanguageCoordinate object.
    * Adding 1/validation/findFQN to the API for checking to see if a description would lead to a duplicate FQN prior to concept create
    * Adding system/descriptionStyle/{id} which allows you to determine at runtime, whether a terminology was loaded with the native
      (snomed style) descriptions, extended description style - which is how we histocially loaded external terminologies, or external,
      where we use description types directly from the external terminology.
    * Adding system/externalDescriptionTypes which gives you a list of the description types applicable to a terminology that uses the 
      external description type style, in a hierarchical form.
    * Adding "externalDescriptionTypeConcept" to RestConceptCreateData, which allows you to specify the description type for concept creation
      when working with a terminology that uses the external description style.  Updated documentation throughout the class to document
      the behavior when this parameter is utilized.
    * Added "externalDescriptionSemantic" to RestWriteResponseConceptCreate to handle the return when "externalDescriptionTypeConcept" is 
      used during create.
    * Added "descriptionStyle" to RestTerminologyConcept which is the return type of 1/system/terminologyTypes
    * Added 1/coordinate/editModule/{id} to return the appropriate (default) edit module for a particular terminology

* 2018/07/23 - 1.17.1
    * The coordinate APIs now allow the special keyword 'recurse' as a dialect, to properly support nested dialects like 
      en -> en-us -> en-nursing.  Other bugs and documentation issues around dialects were corrected.

* 2018/01/31 - 1.17.0
    * Moving over to the new OSEHRA ISAAC.  A few API updates to correspond.
    * Things that were previously called "sememe" are now called "semantic.
    * Sequence IDs are no longer part of the data model, and are not returned in any object.
    * Any API that took in a string identifier that accepted sequence IDs now only accepts nids (and/or UUIDs, as applicable)
    * RestTaxonomyCoordinate turned into RestManifoldCoordinate - this includes paths like restTaxonomyCoordinate/ becoming 
      restManifoldCoordinate/
    * RestSememeType becomes RestSemanticType
    * The parameter "descriptionType" became "descriptionTypes", and now accepts more than one descriptionType.  It also now accepts
      nids and uuids, in addition to the string constants it previously supported.
    * The parameter "extendedDescriptionTypeId" became extendedDescriptionTypes, and now accepts more than one type.  
    * The parameter value "sememe" for a search restriction type was changed to "semantic"
    * The parameter 'dynamicSememeColumns' was changed to 'dynamicSemanticColumns'
    * The parameter 'sememeMembership' was changed to 'semanticMembership' (both in query and return objects)
    * The workflow API has been disabled for now
    * The parameter 'nestedSememes' turned into 'nestedSemantics'
    * RestIdentifiedObject no longer carries a sequence id
    * All "Dynamic" transport objects had their naming changed from "Sememe" to "Semantic"
    * RestLiteralNodeInstant definition has been fixed, to now it passes a long and an int, rather than trying to serialize an Instant.
    * in RestWriteResponseConceptCreate fsnDescriptionSememe, preferredDescriptionSememe, extendedDescriptionTypeSememe,
      hasParentAssociationSememe, dialectSememes all changed to end with "Semantic" instead
    * in RestAssociationItemVersion, sourceSememe and targetSememe changed to end with "Semantic"
    * The path restSememeType/ changed to restSemanticType/
    * RestLiteralNodeFloat changed to RestLiteralNodeDouble
    * All parameters and variables named 'fsn' became 'fqn'.  Parameters will still accept the old values, but classes with variables such as 
      RestConceptCreateData, RestWriteResponseConceptCreate have variables that changed names to match.