/**
 * Copyright Notice
 *
 * This is a work of the U.S. Government and is not subject to copyright
 * protection in the United States. Foreign copyrights may apply.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sagebits.tmp.isaac.rest.api1.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.queryparser.classic.ParseException;
import net.sagebits.tmp.isaac.rest.ExpandUtil;
import net.sagebits.tmp.isaac.rest.Util;
import net.sagebits.tmp.isaac.rest.api.exceptions.RestException;
import net.sagebits.tmp.isaac.rest.api1.RestPaths;
import net.sagebits.tmp.isaac.rest.api1.data.enumerations.SemanticStyle;
import net.sagebits.tmp.isaac.rest.api1.data.search.RestSearchResult;
import net.sagebits.tmp.isaac.rest.api1.data.search.RestSearchResultPage;
import net.sagebits.tmp.isaac.rest.api1.semantic.SemanticAPIs;
import net.sagebits.tmp.isaac.rest.session.RequestInfo;
import net.sagebits.tmp.isaac.rest.session.RequestInfoUtils;
import net.sagebits.tmp.isaac.rest.session.RequestParameters;
import net.sagebits.uts.auth.data.UserRole.SystemRoleConstants;
import sh.isaac.MetaData;
import sh.isaac.api.AssemblageService;
import sh.isaac.api.Get;
import sh.isaac.api.IdentifiedObjectService;
import sh.isaac.api.LookupService;
import sh.isaac.api.Status;
import sh.isaac.api.chronicle.Chronology;
import sh.isaac.api.chronicle.LatestVersion;
import sh.isaac.api.chronicle.Version;
import sh.isaac.api.component.concept.ConceptChronology;
import sh.isaac.api.component.concept.ConceptService;
import sh.isaac.api.component.semantic.SemanticChronology;
import sh.isaac.api.component.semantic.version.DescriptionVersion;
import sh.isaac.api.component.semantic.version.DynamicVersion;
import sh.isaac.api.component.semantic.version.LongVersion;
import sh.isaac.api.component.semantic.version.StringVersion;
import sh.isaac.api.coordinate.StampCoordinate;
import sh.isaac.api.externalizable.IsaacObjectType;
import sh.isaac.api.index.AuthorModulePathRestriction;
import sh.isaac.api.index.ComponentSearchResult;
import sh.isaac.api.index.ConceptSearchResult;
import sh.isaac.api.index.IndexSemanticQueryService;
import sh.isaac.api.index.SearchResult;
import sh.isaac.api.util.Interval;
import sh.isaac.api.util.NumericUtils;
import sh.isaac.api.util.UUIDUtil;
import sh.isaac.model.semantic.types.DynamicStringImpl;
import sh.isaac.provider.query.lucene.indexers.DescriptionIndexer;
import sh.isaac.utility.Frills;
import sh.isaac.utility.NumericUtilsDynamic;

/**
 * {@link SearchAPIs}
 *
 * @author <a href="mailto:daniel.armbrust.list@sagebits.net">Dan Armbrust</a>
 */
@Path(RestPaths.searchAPIsPathComponent)
@RolesAllowed({ SystemRoleConstants.AUTOMATED, SystemRoleConstants.ADMINISTRATOR, SystemRoleConstants.SYSTEM_MANAGER, SystemRoleConstants.CONTENT_MANAGER,
	SystemRoleConstants.EDITOR, SystemRoleConstants.READ })
public class SearchAPIs
{
	private static Logger log = LogManager.getLogger();

	@Context
	private SecurityContext securityContext;

	private RestSearchResultPage getRestSearchResultsFromOchreSearchResults(List<SearchResult> ochreSearchResults, int pageNum, int maxPageSize,
			String restPath, String query) throws RestException
	{
		List<RestSearchResult> restSearchResults = new ArrayList<>();
		for (SearchResult ochreSearchResult : ochreSearchResults)
		{
			Optional<RestSearchResult> restSearchResultOptional = createRestSearchResult(ochreSearchResult, query);
			if (restSearchResultOptional.isPresent())
			{
				restSearchResults.add(restSearchResultOptional.get());
			}
			else
			{
				//This shouldn't happen, because all of the same filtering critera should happen at query time - we should 
				//no longer be post-filtering anything here.
				log.warn("Conversion to RestSearchResult was unsuccessful for SearchResult {}", ochreSearchResult.getNid());
			}
		}
		boolean hasMoreData = ochreSearchResults.size() == maxPageSize; 

		return new RestSearchResultPage(pageNum, maxPageSize, 
				(((pageNum - 1) * maxPageSize) + ochreSearchResults.size() + (hasMoreData ? 1 : 0)), 
				false,  //Our size calcuation doesn't account for what gets removed above 
				hasMoreData, restPath, restSearchResults);
	}

	/**
	 * A simple search interface which is evaluated across all indexed descriptions in the terminology.  This variation is for a GET, and the 
	 * query is passed as a parameter.
	 * 
	 * @param query The query to be evaluated. Will be parsed by the Lucene Query Parser:
	 *            https://lucene.apache.org/core/7_0_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package.description
	 *            This also supports regular expressions, however, the lucene syntax requires regular expressions to be surrounded by forward slashes - 
	 *            /dat[^a].*./ - and many of the characters (/, [, ], ^) are illegal in a URI.  It is recommended that you encode your search string, 
	 *            using x-www-form-urlencoded encoding for the parameter.  Take care to use the proper encoding scheme, spaces in query parameters 
	 *            are represented by +, not %20, and + in the query should be encoded as %2B.
	 *            The example above, would become %2fdat%5b%5ea%5d.*.%2f
	 *            
	 *            See the POST variation of this method, for a mechanism to avoid having to URI encode the query.
	 *
	 * @param descriptionTypes - optional - can be specified as 'fqn', 'regular', or 'definition' to restrict to a particular
	 *            description type. This also supports legacy names - 'fsn' is the same as 'fqn', 'synonym' and 'regular*' is the same as 'regular'. 
	 *            You may also specify UUIDs or NIDS of a description type concept.
	 * @param extendedDescriptionTypes - optional - This would typically be one or more concept identifiers of concepts that are a LEAF child of the
	 *            concept 'description type in source terminology (ISAAC)'
	 * @param applyStampToConcept if true (the default) then the supplied STAMP restrictions will also apply to the concept most closely related to the 
	 *            description match.  For example, if searching for active only if a query matches an active description, but the concept that the 
	 *            description is attached to is inactive, then the concept and all related search hits will be omitted from the final results.
	 * @param pageNum The pagination page number >= 1 to return
	 * @param maxPageSize The maximum number of results to return per page, must be greater than 0
	 * @param expand Optional Comma separated list of fields to expand or include directly in the results. Supports:
	 *            <br> 'uuid' (return the UUID of the matched semantic, rather than just the nid)
	 *            <br> 'referencedConcept' (return the conceptChronology of the nearest concept found by following the referencedComponent references
	 *            of the matched semantic. In most cases, this concept will be the concept that directly contains the semantic - but in some cases,
	 *            semantics may be nested under other semantics causing this to walk up until it finds a concept)
	 *            <br> 'versionsLatestOnly' if 'referencedConcept' is included in the expand list, you may also include 'versionsLatestOnly' to return
	 *            the latest version of the referenced concept chronology.
	 *            <br> 'versionsAll' if 'referencedConcept is included in the expand list, you may also include 'versionsAll' to return all versions of
	 *            the referencedConcept.  For expansions within the referenced concept, such as parents, the stamp is calculated relative to the version
	 *            of the concept being returned.  Callers should handle expansions being null, if the data is unavailable for the timestamp.   Because of 
	 *            this, we always return two copies of the newest version when versionsAll is specified. The first - position 0 in the return - will be 
	 *            calculated with the stamp supplied in the request for all components. The second - position 1 - will contain the same top level version, 
	 *            but any referenced components will have been rendered with a stamp from the version being rendered. Beyond the first two positions, all 
	 *            additional versions are sorted newest to oldest.  
	 *            <br>'countParents' - may only be specified in combination with 'referencedConcept' and ('versionsLatestOnly' or 'versionsAll') - will 
	 *            cause the expanded version to also have the parent count populated.
	 *            <br> 'includeParents' - may only be specified in combination with 'referencedConcept' and ('versionsLatestOnly' or 'versionsAll') - 
	 *            will cause the expanded version to also have the first-level parent list populated.
	 *            <br> 'terminologyType' - may only be specified in combination with 'referencedConcept'.   When specified, the concept nids of the 
	 *            terminologies that this concept is part of on any stamp is returned. This is determined by whether or not there is version of this concept 
	 *            present with a module that extends from one of the children of the {@link MetaData#MODULE____SOLOR} concepts. This is returned as a 
	 *            set, as a concept may exist in multiple terminologies at the same time.  Note, this is populated on the referencedConcept chronology, 
	 *            not on the versions returned, if any.
	 * @param coordToken specifies an explicit serialized CoordinatesToken string specifying all coordinate parameters. A CoordinatesToken may be
	 *            obtained by a separate (prior) call to getCoordinatesToken().
	 * <br>
	 * <br>       The search specifically takes into account the 'modules' and 'path' components of the coordToken (or of individual 'modules' or
	 *            'path' parameters to restrict the search to matching items. 'modules' are also evaluated recursively, so you can pass the identifier
	 *            for the VHAT_MODULES module (which also happens to be a /system/terminologyTypes value - all "terminologyType" constants work here)
	 *            to restrict a search to a particular terminology or even a particular version of a terminology.
	 *            
	 * @param altId - (optional) the altId type(s) to populate in any returned RestIdentifiedObject structures.  By default, no alternate IDs are 
	 *     returned.  This can be set to one or more names or ids from the /1/id/types or the value 'ANY'.  Requesting IDs that are unneeded will harm 
	 *     performance. 
	 * 
	 * @return the list of descriptions that matched, along with their score. Note that the textual value may _NOT_ be included,
	 *         if the description that matched is not active on the default path.
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.descriptionsComponent)
	public RestSearchResultPage descriptionSearch(@QueryParam(RequestParameters.query) String query,
			@QueryParam(RequestParameters.descriptionTypes) Set<String> descriptionTypes,
			@QueryParam(RequestParameters.extendedDescriptionTypes) Set<String> extendedDescriptionTypes,
			@QueryParam(RequestParameters.applyStampToConcept) @DefaultValue("true") String applyStampToConcept,
			@QueryParam(RequestParameters.pageNum) @DefaultValue(RequestParameters.pageNumDefault) int pageNum,
			@QueryParam(RequestParameters.maxPageSize) @DefaultValue(RequestParameters.maxPageSizeDefault) int maxPageSize,
			@QueryParam(RequestParameters.expand) String expand, 
			@QueryParam(RequestParameters.coordToken) String coordToken,
			@QueryParam(RequestParameters.altId) String altId) throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.query,
				RequestParameters.descriptionTypes, RequestParameters.extendedDescriptionTypes, RequestParameters.applyStampToConcept, RequestParameters.PAGINATION_PARAM_NAMES,
				RequestParameters.expand, RequestParameters.COORDINATE_PARAM_NAMES, RequestParameters.altId);
		
		RequestInfo.get().validateMethodExpansions(ExpandUtil.uuid, ExpandUtil.referencedConcept, ExpandUtil.versionsLatestOnlyExpandable, 
				ExpandUtil.versionsAllExpandable, ExpandUtil.countParents, ExpandUtil.includeParents, ExpandUtil.terminologyType);

		if (StringUtils.isBlank(query))
		{
			throw new RestException("The parameter 'query' must contain at least one character");
		}
		int[] parsedDescriptionTypes = parseDescriptionTypes(descriptionTypes);
		int[] parsedExtendedDescriptionTypes = parseExtendedDescriptionTypes(extendedDescriptionTypes);

		final String restPath = RestPaths.searchAppPathComponent + RestPaths.descriptionsComponent + "?" + RequestParameters.query + "=" + query;

		log.debug("Performing description search for '" + query + "'");
		try {
			List<SearchResult> ochreSearchResults = LookupService.get().getService(DescriptionIndexer.class).query(query, false, null, 
					createStatusTimeFilter(
							StringUtils.isBlank(applyStampToConcept) ? true : RequestInfoUtils.parseBooleanParameter(RequestParameters.applyStampToConcept, applyStampToConcept),
							RequestInfo.get().getStampCoordinate()),
					AuthorModulePathRestriction.restrict(RequestInfo.get().getStampCoordinate()), false, parsedDescriptionTypes, parsedExtendedDescriptionTypes, pageNum,
					maxPageSize, Long.MAX_VALUE);
			return getRestSearchResultsFromOchreSearchResults(ochreSearchResults, pageNum, maxPageSize, restPath, query);
		}
		catch (Exception e)
		{
			if (e.getCause() != null && e.getCause() instanceof ParseException)
			{
				throw new RestException(query, "Query Syntax Error: " + ((ParseException)e.getCause()).getMessage());
			}
			else
			{
				throw e;
			}
		}
	}
	
	/**
	 * A simple search interface which is evaluated across all indexed descriptions in the terminology.   This variation is for a POST, and the 
	 * query is posted as a string.
	 * 
	 * @param query The query to be evaluated. Will be parsed by the Lucene Query Parser:
	 *            https://lucene.apache.org/core/7_0_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package.description
	 *            This also supports regular expressions, however, the lucene syntax requires regular expressions to be surrounded by forward slashes - 
	 *            /dat[^a].*./ 
	 *            
	 *            This method variation is for POST support, rather than GET, so you do not need to encode all of the characters that are illegal in a URI.
	 *
	 * @param descriptionTypes - optional - can be specified as 'fqn', 'regular', or 'definition' to restrict to a particular
	 *            description type. This also supports legacy names - 'fsn' is the same as 'fqn', 'synonym' and 'regular*' is the same as 'regular'. 
	 *            You may also specify UUIDs or NIDS of a description type concept.
	 * @param extendedDescriptionTypes - optional - This would typically be one or more concept identifiers of concepts that are a LEAF child of the
	 *            concept 'description type in source terminology (ISAAC)'
	 * @param applyStampToConcept if true (the default) then the supplied STAMP restrictions will also apply to the concept most closely related to the 
	 *            description match.  For example, if searching for active only if a query matches an active description, but the concept that the 
	 *            description is attached to is inactive, then the concept and all related search hits will be omitted from the final results.
	 * @param pageNum The pagination page number >= 1 to return
	 * @param maxPageSize The maximum number of results to return per page, must be greater than 0
	 * @param expand Optional Comma separated list of fields to expand or include directly in the results. Supports:
	 *            <br> 'uuid' (return the UUID of the matched semantic, rather than just the nid)
	 *            <br> 'referencedConcept' (return the conceptChronology of the nearest concept found by following the referencedComponent references
	 *            of the matched semantic. In most cases, this concept will be the concept that directly contains the semantic - but in some cases,
	 *            semantics may be nested under other semantics causing this to walk up until it finds a concept)
	 *            <br> 'versionsLatestOnly' if 'referencedConcept' is included in the expand list, you may also include 'versionsLatestOnly' to return
	 *            the latest version of the referenced concept chronology.
	 *            <br> 'versionsAll' if 'referencedConcept is included in the expand list, you may also include 'versionsAll' to return all versions of
	 *            the referencedConcept.  For expansions within the referenced concept, such as parents, the stamp is calculated relative to the version
	 *            of the concept being returned.  Callers should handle expansions being null, if the data is unavailable for the timestamp.   Because of 
	 *            this, we always return two copies of the newest version when versionsAll is specified. The first - position 0 in the return - will be 
	 *            calculated with the stamp supplied in the request for all components. The second - position 1 - will contain the same top level version, 
	 *            but any referenced components will have been rendered with a stamp from the version being rendered. Beyond the first two positions, all 
	 *            additional versions are sorted newest to oldest.  
	 *            <br>'countParents' - may only be specified in combination with 'referencedConcept' and ('versionsLatestOnly' or 'versionsAll') - will 
	 *            cause the expanded version to also have the parent count populated.
	 *            <br> 'includeParents' - may only be specified in combination with 'referencedConcept' and ('versionsLatestOnly' or 'versionsAll') - 
	 *            will cause the expanded version to also have the first-level parent list populated.
	 *            <br> 'terminologyType' - may only be specified in combination with 'referencedConcept'.   When specified, the concept nids of the 
	 *            terminologies that this concept is part of on any stamp is returned. This is determined by whether or not there is version of this concept 
	 *            present with a module that extends from one of the children of the {@link MetaData#MODULE____SOLOR} concepts. This is returned as a 
	 *            set, as a concept may exist in multiple terminologies at the same time.  Note, this is populated on the referencedConcept chronology, 
	 *            not on the versions returned, if any.
	 * @param coordToken specifies an explicit serialized CoordinatesToken string specifying all coordinate parameters. A CoordinatesToken may be
	 *            obtained by a separate (prior) call to getCoordinatesToken().
	 * <br>
	 * <br>       The search specifically takes into account the 'modules' and 'path' components of the coordToken (or of individual 'modules' or
	 *            'path' parameters to restrict the search to matching items. 'modules' are also evaluated recursively, so you can pass the identifier
	 *            for the VHAT_MODULES module (which also happens to be a /system/terminologyTypes value - all "terminologyType" constants work here)
	 *            to restrict a search to a particular terminology or even a particular version of a terminology.
	 *            
	 * @param altId - (optional) the altId type(s) to populate in any returned RestIdentifiedObject structures.  By default, no alternate IDs are 
	 *     returned.  This can be set to one or more names or ids from the /1/id/types or the value 'ANY'.  Requesting IDs that are unneeded will harm 
	 *     performance. 
	 * 
	 * @return the list of descriptions that matched, along with their score. Note that the textual value may _NOT_ be included,
	 *         if the description that matched is not active on the default path.
	 * @throws RestException
	 */
	@POST
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.descriptionsComponent)
	public RestSearchResultPage descriptionSearchPost(String query,
			@QueryParam(RequestParameters.descriptionTypes) Set<String> descriptionTypes,
			@QueryParam(RequestParameters.extendedDescriptionTypes) Set<String> extendedDescriptionTypes,
			@QueryParam(RequestParameters.applyStampToConcept) @DefaultValue("true") String applyStampToConcept,
			@QueryParam(RequestParameters.pageNum) @DefaultValue(RequestParameters.pageNumDefault) int pageNum,
			@QueryParam(RequestParameters.maxPageSize) @DefaultValue(RequestParameters.maxPageSizeDefault) int maxPageSize,
			@QueryParam(RequestParameters.expand) String expand, @QueryParam(RequestParameters.coordToken) String coordToken,
			@QueryParam(RequestParameters.altId) String altId) throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(),
				RequestParameters.descriptionTypes, RequestParameters.extendedDescriptionTypes, RequestParameters.PAGINATION_PARAM_NAMES,
				RequestParameters.applyStampToConcept, RequestParameters.expand, RequestParameters.COORDINATE_PARAM_NAMES, RequestParameters.altId);
		
		RequestInfo.get().validateMethodExpansions(ExpandUtil.uuid, ExpandUtil.referencedConcept, ExpandUtil.versionsLatestOnlyExpandable, 
				ExpandUtil.versionsAllExpandable, ExpandUtil.countParents, ExpandUtil.includeParents, ExpandUtil.terminologyType);

		if (StringUtils.isBlank(query))
		{
			throw new RestException("The parameter 'query' must contain at least one character");
		}
		int[] parsedDescriptionTypes = parseDescriptionTypes(descriptionTypes);
		int[] parsedExtendedDescriptionTypes = parseExtendedDescriptionTypes(extendedDescriptionTypes);

		final String restPath = RestPaths.searchAppPathComponent + RestPaths.descriptionsComponent + "?" + RequestParameters.query + "=" + query;

		log.debug("Performing description search for '" + query + "'");
		try
		{
			List<SearchResult> ochreSearchResults = LookupService.get().getService(DescriptionIndexer.class).query(query, false, null, 
					createStatusTimeFilter(
							StringUtils.isBlank(applyStampToConcept) ? true : RequestInfoUtils.parseBooleanParameter(RequestParameters.applyStampToConcept, applyStampToConcept),
							RequestInfo.get().getStampCoordinate()),
					AuthorModulePathRestriction.restrict(RequestInfo.get().getStampCoordinate()), false, parsedDescriptionTypes, parsedExtendedDescriptionTypes, pageNum,
					maxPageSize, Long.MAX_VALUE);
			return getRestSearchResultsFromOchreSearchResults(ochreSearchResults, pageNum, maxPageSize, restPath, query);
		}
		catch (Exception e)
		{
			if (e.getCause() != null && e.getCause() instanceof ParseException)
			{
				throw new RestException(query, "Query Syntax Error: " + ((ParseException)e.getCause()).getMessage());
			}
			else
			{
				throw e;
			}
		}
	}

	/**
	 * A search interface that is optimized for prefix searching, such as the searching
	 * that would be done to implement a type-ahead style search. Does not use the Lucene Query parser.
	 * Every term (or token) that is part of the query string will be required to be found in the result.
	 * 
	 * Note, it is useful to NOT trim the text of the query before it is sent in - if the last word of the query has a
	 * space character following it, that word will be required as a complete term. If the last word of the query does not
	 * have a space character following it, that word will be required as a prefix match only.
	 * 
	 * It is recommended that you use the proper encoding scheme for spaces - x-www-form-urlencoded encoding for the parameter.  
	 * spaces in query parameters are represented by +, not %20
	 * 
	 * For example:
	 * The query "family test" will be evaluated as if it were "family test*" - returning results that contain 'Family Testudinidae'
	 * The query "family test " will not match on 'Testudinidae', as test is considered a complete token, and no * is appended.
	 * 
	 * @param query The query to be evaluated.
	 * @param applyStampToConcept if true (the default) then the supplied STAMP restrictions will also apply to the concept most closely related to the 
	 *            description match.  For example, if searching for active only if a query matches an active description, but the concept that the 
	 *            description is attached to is inactive, then the concept and all related search hits will be omitted from the final results.
	 * @param pageNum The pagination page number >= 1 to return
	 * @param maxPageSize The maximum number of results to return per page, must be greater than 0
	 * @param restrictTo Optional a feature that will restrict the results descriptions attached to a concept that meet one of the specified
	 *            criteria. Currently, this can be set to
	 *            <br> "association" - to only return concepts that define association types, which are semantics with a specific structure
	 *            <br> "mapset" - to only return concepts that define mapsets, which are semantics with a specific structure
	 *            <br> "refset" - to only return concepts that define refsets, which are membership-only semantics.
	 *            <br> "property" - to only return concepts that define properties, which are semantics that also have data column(s)
	 *            <br> "semantic" - to only return concepts that define semantics (includes 'refset' and 'property' semantics.
	 *            <br> "metadata" - to only return concepts that are defined in the metadata hierarchy.
	 *            <br>This option can only be set to a single value per call - no combinations are allowed.
	 * @param mergeOnConcept - Optional - if set to true - only one result will be returned per concept - even if that concept had 2 or more
	 *            descriptions that matched the query. When false, you will get a search result for EACH matching description. When true, you will
	 *            only get one search result, which is the search result with the best score for that concept (compared to the other search results
	 *            for that concept)
	 * @param expand Optional Comma separated list of fields to expand or include directly in the results. Supports:
	 *            <br> 'uuid' (return the UUID of the matched semantic, rather than just the nid)
	 *            <br> 'referencedConcept' (return the conceptChronology of the nearest concept found by following the referencedComponent references
	 *            of the matched semantic. In most cases, this concept will be the concept that directly contains the semantic - but in some cases,
	 *            semantics may be nested under other semantics causing this to walk up until it finds a concept)
	 *            <br> 'versionsLatestOnly' if 'referencedConcept' is included in the expand list, you may also include 'versionsLatestOnly' to return
	 *            the latest version of the referenced concept chronology.
	 *            <br> 'versionsAll' if 'referencedConcept is included in the expand list, you may also include 'versionsAll' to return all versions of
	 *            the referencedConcept.  For expansions within the referenced concept, such as parents, the stamp is calculated relative to the version
	 *            of the concept being returned.  Callers should handle expansions being null, if the data is unavailable for the timestamp.   Because of 
	 *            this, we always return two copies of the newest version when versionsAll is specified. The first - position 0 in the return - will be 
	 *            calculated with the stamp supplied in the request for all components. The second - position 1 - will contain the same top level version, 
	 *            but any referenced components will have been rendered with a stamp from the version being rendered. Beyond the first two positions, all 
	 *            additional versions are sorted newest to oldest.  
	 *            <br>'countParents' - may only be specified in combination with 'referencedConcept' and ('versionsLatestOnly' or 'versionsAll') - will 
	 *            cause the expanded version to also have the parent count populated.
	 *            <br> 'includeParents' - may only be specified in combination with 'referencedConcept' and ('versionsLatestOnly' or 'versionsAll') - 
	 *            will cause the expanded version to also have the first-level parent list populated.
	 *            <br> 'terminologyType' - may only be specified in combination with 'referencedConcept'.   When specified, the concept nids of the 
	 *            terminologies that this concept is part of on any stamp is returned. This is determined by whether or not there is version of this concept 
	 *            present with a module that extends from one of the children of the {@link MetaData#MODULE____SOLOR} concepts. This is returned as a 
	 *            set, as a concept may exist in multiple terminologies at the same time.  Note, this is populated on the referencedConcept chronology, 
	 *            not on the versions returned, if any.
	 * @param coordToken specifies an explicit serialized CoordinatesToken string specifying all coordinate parameters. A CoordinatesToken may be
	 *            obtained by a separate (prior) call to getCoordinatesToken().
	 * <br>
	 *            <br>The search specifically takes into account the 'modules' and 'path' components of the coordToken (or of individual 'modules' or
	 *            'path' parameters to restrict the search to matching items. 'modules' are also evaluated recursively, so you can pass the identifier
	 *            for the VHAT_MODULES module (which also happens to be a /system/terminologyTypes value - all "terminologyType" constants work here)
	 *            to restrict a search to a particular terminology or even a particular version of a terminology.
	 *            
	 * @param altId - (optional) the altId type(s) to populate in any returned RestIdentifiedObject structures.  By default, no alternate IDs are 
	 *     returned.  This can be set to one or more names or ids from the /1/id/types or the value 'ANY'.  Requesting IDs that are unneeded will harm 
	 *     performance. 
	 *
	 * @return the list of descriptions that matched, along with their score. Note that the textual value may _NOT_ be included,
	 *         if the description that matched is not active on the default path.
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.prefixComponent)
	public RestSearchResultPage prefixSearch(@QueryParam(RequestParameters.query) String query,
			@QueryParam(RequestParameters.applyStampToConcept) @DefaultValue("true") String applyStampToConcept,
			@QueryParam(RequestParameters.pageNum) @DefaultValue(RequestParameters.pageNumDefault) int pageNum,
			@QueryParam(RequestParameters.maxPageSize) @DefaultValue(RequestParameters.maxPageSizeDefault) int maxPageSize,
			@QueryParam(RequestParameters.restrictTo) String restrictTo, @QueryParam(RequestParameters.mergeOnConcept) String mergeOnConcept,
			@QueryParam(RequestParameters.expand) String expand, @QueryParam(RequestParameters.coordToken) String coordToken,
			@QueryParam(RequestParameters.altId) String altId) throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.query,
				RequestParameters.PAGINATION_PARAM_NAMES, RequestParameters.restrictTo, RequestParameters.mergeOnConcept, RequestParameters.expand,
				RequestParameters.applyStampToConcept, RequestParameters.COORDINATE_PARAM_NAMES, RequestParameters.altId);
		
		RequestInfo.get().validateMethodExpansions(ExpandUtil.uuid, ExpandUtil.referencedConcept, ExpandUtil.versionsLatestOnlyExpandable, 
				ExpandUtil.versionsAllExpandable, ExpandUtil.countParents, ExpandUtil.includeParents, ExpandUtil.terminologyType);

		if (StringUtils.isBlank(query))
		{
			throw new RestException("The parameter 'query' must contain at least one character");
		}
		log.debug("Performing prefix search for '" + query + "'");

		boolean mergeOnConcepts = StringUtils.isBlank(mergeOnConcept) ? false
				: RequestInfoUtils.parseBooleanParameter(RequestParameters.mergeOnConcept, mergeOnConcept);
		boolean metadataRestrict = false;

		Predicate<Integer> filter = null;
		if (StringUtils.isNotBlank(restrictTo))
		{
			String temp = restrictTo.toLowerCase(Locale.ENGLISH).trim();
			metadataRestrict = true;
			switch (temp)
			{
				case "association":
					filter = (nid -> {
						Optional<Integer> conNid = Frills.getNearestConcept(nid);
						if (conNid.isPresent())
						{
							return Frills.definesAssociation(conNid.get());
						}
						return false;
					});
					break;
				case "mapset":
					filter = (nid -> {
						Optional<Integer> conNid = Frills.getNearestConcept(nid);
						if (conNid.isPresent())
						{
							return Frills.definesMapping(conNid.get());
						}
						return false;
					});
					break;
				case "sememe":
				case "semantic":
					filter = (nid -> {
						Optional<Integer> conNid = Frills.getNearestConcept(nid);
						if (conNid.isPresent())
						{
							SemanticStyle ss = SemanticAPIs.getSemanticStyle(conNid.get());
							if (ss != null && ss != SemanticStyle.NONE)
							{
								return true;
							}
							return false;
						}
						return false;
					});
					break;
				case "refset":
					filter = (nid -> {
						Optional<Integer> conNid = Frills.getNearestConcept(nid);
						if (conNid.isPresent())
						{
							SemanticStyle ss = SemanticAPIs.getSemanticStyle(conNid.get());
							if (ss != null && ss == SemanticStyle.REFSET)
							{
								return true;
							}
							return false;
						}
						return false;
					});
					break;
				case "property":
					filter = (nid -> {
						Optional<Integer> conNid = Frills.getNearestConcept(nid);
						if (conNid.isPresent())
						{
							SemanticStyle ss = SemanticAPIs.getSemanticStyle(conNid.get());
							if (ss != null && (ss == SemanticStyle.ASSOCIATION || ss == SemanticStyle.MAPSET || ss == SemanticStyle.PROPERTY))
							{
								return true;
							}
							return false;
						}
						return false;
					});
					break;
				case "metadata":
					// metadata restrict is now part of the query construction.
					filter = null;
					break;
				default :
					throw new RestException("restrictTo", "Invalid restriction.  Must be 'association', 'mapset', 'refset', 'property', 'semantic' or 'metadata'");
			}
		}
		
		Predicate<Integer> compositeFilter = null;
		if (filter == null)
		{
			compositeFilter = createStatusTimeFilter(StringUtils.isBlank(applyStampToConcept) ? true : RequestInfoUtils.parseBooleanParameter(RequestParameters.applyStampToConcept, applyStampToConcept),
					RequestInfo.get().getStampCoordinate());
		}
		else
		{
			final Predicate<Integer> methodFilterFinal = filter;
			compositeFilter = new Predicate<Integer>()
			{
				Predicate<Integer> parentFilter = createStatusTimeFilter(StringUtils.isBlank(applyStampToConcept) ? true : RequestInfoUtils.parseBooleanParameter(RequestParameters.applyStampToConcept, applyStampToConcept),
						RequestInfo.get().getStampCoordinate());
				@Override
				public boolean test(Integer nid)
				{
					return (methodFilterFinal.test(nid) && parentFilter.test(nid));
				}
			};
		}

		DescriptionIndexer indexer = LookupService.get().getService(DescriptionIndexer.class);

		List<SearchResult> ochreSearchResults;
		try
		{
			ochreSearchResults = indexer.query(query, true, null, compositeFilter, AuthorModulePathRestriction.restrict(RequestInfo.get().getStampCoordinate()),
					metadataRestrict, (int[])null, (int[])null, pageNum, maxPageSize, Long.MAX_VALUE);
		}
		catch (Exception e)
		{
			if (e.getCause() != null && e.getCause() instanceof ParseException)
			{
				throw new RestException(query, "Query Syntax Error: " + ((ParseException)e.getCause()).getMessage());
			}
			else
			{
				throw e;
			}
		}

		if (mergeOnConcepts)
		{
			List<ConceptSearchResult> temp = indexer.mergeResultsOnConcept(ochreSearchResults, RequestInfo.get().getStampCoordinate());
			ochreSearchResults = new ArrayList<>(temp.size());
			for (ConceptSearchResult csr : temp)
			{
				ochreSearchResults.add((SearchResult) csr);
			}
		}

		String restPath = RestPaths.searchAppPathComponent + RestPaths.prefixComponent + "?" + RequestParameters.query + "=" + query;
		return getRestSearchResultsFromOchreSearchResults(ochreSearchResults, pageNum, maxPageSize, restPath, query);
	}

	private Optional<RestSearchResult> createRestSearchResult(SearchResult sr, String query)
	{
		switch (Get.identifierService().getObjectTypeForComponent(sr.getNid()))
		{
			case CONCEPT:
			{
				ConceptChronology cc = Get.conceptService().getConceptChronology(sr.getNid());
				LatestVersion<Version> concept = cc.getLatestVersion(RequestInfo.get().getStampCoordinate());
				Util.logContradictions(log, concept);
				if (concept.isPresent())
				{
					return Optional.of(new RestSearchResult(sr.getNid(), query, sr.getScore(), concept.get().getStatus(), cc.getNid()));
				}
				break;
			}
			case SEMANTIC:
			{
				SemanticChronology sc = Get.assemblageService().getSemanticChronology(sr.getNid());
				Integer conceptNid = null;
				if (sr instanceof ConceptSearchResult)
				{
					conceptNid = ((ConceptSearchResult) sr).getConceptNid();
				}

				switch (sc.getVersionType())
				{
					case DESCRIPTION:
						LatestVersion<DescriptionVersion> text = sc.getLatestVersion(RequestInfo.get().getStampCoordinate());
						Util.logContradictions(log, text);
						if (text.isPresent())
						{
							// TODO handle contradictions
							return Optional.of(new RestSearchResult(sr.getNid(), text.get().getText(), sr.getScore(), text.get().getStatus(), conceptNid));
						}
						break;
					case LONG:
						LatestVersion<LongVersion> longSemantic = sc.getLatestVersion(RequestInfo.get().getStampCoordinate());
						Util.logContradictions(log, longSemantic);
						if (longSemantic.isPresent())
						{
							// TODO handle contradictions
							return Optional.of(new RestSearchResult(sr.getNid(), longSemantic.get().getLongValue() + "", sr.getScore(),
									longSemantic.get().getStatus(), conceptNid));
						}
						break;
					case STRING:
						LatestVersion<StringVersion> stringSemantic = sc.getLatestVersion(RequestInfo.get().getStampCoordinate());
						Util.logContradictions(log, stringSemantic);
						if (stringSemantic.isPresent())
						{
							return Optional.of(new RestSearchResult(sr.getNid(), stringSemantic.get().getString(), sr.getScore(), stringSemantic.get().getStatus(),
									conceptNid));
						}
						break;
					case DYNAMIC:
						LatestVersion<DynamicVersion> ds = sc.getLatestVersion(RequestInfo.get().getStampCoordinate());
						Util.logContradictions(log, ds);
						if (ds.isPresent())
						{
							return Optional
									.of(new RestSearchResult(sr.getNid(), ds.get().dataToString(), sr.getScore(), ds.get().getStatus(), conceptNid));
						}
						break;
					// No point in reading back details on these, they will be exactly what was searched for
					case COMPONENT_NID:
					case LOGIC_GRAPH:
						// Should never match on these, just let them fall through
					case UNKNOWN:
					case MEMBER:
					case CONCEPT:
						// Might need to improve support for these, if they stick around long term...
					case Int1_Int2_Str3_Str4_Str5_Nid6_Nid7:
					case Nid1_Int2:
					case Nid1_Int2_Str3_Str4_Nid5_Nid6:
					case Nid1_Nid2:
					case Nid1_Nid2_Int3:
					case Nid1_Nid2_Str3:
					case Nid1_Str2:
					case RF2_RELATIONSHIP:
					case Str1_Nid2_Nid3_Nid4:
					case Str1_Str2:
					case Str1_Str2_Nid3_Nid4:
					case Str1_Str2_Nid3_Nid4_Nid5:
					case Str1_Str2_Str3_Str4_Str5_Str6_Str7:
					default :
						LatestVersion<Version> sv = sc.getLatestVersion(RequestInfo.get().getStampCoordinate());
						Util.logContradictions(log, sv);
						if (sv.isPresent())
						{
							return Optional.of(new RestSearchResult(sr.getNid(), query.trim(), sr.getScore(), sv.get().getStatus(), conceptNid));
						}
						break;
				}
				break;
			}
			case UNKNOWN:
			default :
				log.error("Unexpected case of unknown nid type in search result handling! nid: " + sr.getNid());
				break;

		}
		return Optional.empty();
	}
	
	/**
	 * Perform a search of semantic content - the data in the columns of semantics attached to concepts.  This variation is for GET support. 
	 * Queries with characters that are illegal in a URI need to be encoded.
	 * 
	 * @param query The query to be evaluated. If the query is numeric (int, float, long, double) , it will be treated as a numeric search.
	 *            <br>If the query is a mathematical interval - [4,6] or (5,10] or [4,] it will be handled as a numeric interval.
	 *            <br>If the query is not numeric, and is not a valid interval, it will be treated as a string and parsed by the Lucene Query Parser:
	 *            http://lucene.apache.org/core/5_3_1/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#Overview
	 *            <br>This also supports regular expressions, however, the lucene syntax requires regular expressions to be surrounded by forward slashes - 
	 *            <br>/dat[^a].*./ - and many of the characters (/, [, ], ^) are illegal in a URI.  It is recommended that you encode your search string, 
	 *            using x-www-form-urlencoded encoding for the parameter.  Take care to use the proper encoding scheme, spaces in query parameters 
	 *            are represented by +, not %20, and + in the query should be encoded as %2B.
	 *            <br>The example above, would become %2fdat%5b%5ea%5d.*.%2f
	 *
	 * @param treatAsString Treat the query as a string search, even if it is parseable as a number. This is useful because
	 *            'id' type semantics in the data model are always represented as a string, even if they are numeric.
	 * @param semanticAssemblageId (optional) restrict the search to only match on members of the provided semantic assemblage identifier(s).
	 *            This should be the identifier of a concept that defines a semantic. This parameter can be passed multiple times to pass
	 *            multiple semantic assemblage identifiers. This accepts UUIDs or nids.
	 * @param dynamicSemanticColumns (optional) limit the search to the specified columns of attached data. May ONLY be provided if
	 *            ONE and only one semanticAssemblageNid is provided. May not be provided if 0 or more than 1 semanticAssemblageNid values
	 *            are provided.
	 *            <br>This parameter can be passed multiple times to pass multiple column references. This should be a 0 indexed column number - such as
	 *            0 or 4. Information about the columns for a particular semantic (and their index numbers) can be found via the
	 *            semantic/semantic/semanticDefinition/{id} call. It only makes sense to pass this parameter when searching within a specific semantic
	 *            that has multiple columns of data.
	 * @param applyStampToConcept if true (the default) then the supplied STAMP restrictions will also apply to the concept most closely related to the 
	 *            semantic match.  For example, if searching for active only if a query matches an active semantic, but the concept that the 
	 *            semantic is attached to is inactive, then the concept and all related search hits will be omitted from the final results.
	 * @param pageNum The pagination page number >= 1 to return
	 * @param maxPageSize The maximum number of results to return per page, must be greater than 0
	 * @param expand Optional Comma separated list of fields to expand or include directly in the results. Supports:
	 *            <br> 'uuid' (return the UUID of the matched semantic, rather than just the nid)
	 *            <br> 'referencedConcept' (return the conceptChronology of the nearest concept found by following the referencedComponent references
	 *            of the matched semantic. In most cases, this concept will be the concept that directly contains the semantic - but in some cases,
	 *            semantics may be nested under other semantics causing this to walk up until it finds a concept)
	 *            <br> 'versionsLatestOnly' if 'referencedConcept' is included in the expand list, you may also include 'versionsLatestOnly' to return
	 *            the latest version of the referenced concept chronology.
	 *            <br> 'versionsAll' if 'referencedConcept is included in the expand list, you may also include 'versionsAll' to return all versions of
	 *            the referencedConcept.  For expansions within the referenced concept, such as parents, the stamp is calculated relative to the version
	 *            of the concept being returned.  Callers should handle expansions being null, if the data is unavailable for the timestamp.   Because of 
	 *            this, we always return two copies of the newest version when versionsAll is specified. The first - position 0 in the return - will be 
	 *            calculated with the stamp supplied in the request for all components. The second - position 1 - will contain the same top level version, 
	 *            but any referenced components will have been rendered with a stamp from the version being rendered. Beyond the first two positions, all 
	 *            additional versions are sorted newest to oldest.  
	 *            <br>'countParents' - may only be specified in combination with 'referencedConcept' and ('versionsLatestOnly' or 'versionsAll') - will 
	 *            cause the expanded version to also have the parent count populated.
	 *            <br> 'includeParents' - may only be specified in combination with 'referencedConcept' and ('versionsLatestOnly' or 'versionsAll') - 
	 *            will cause the expanded version to also have the first-level parent list populated.
	 *            <br> 'terminologyType' - may only be specified in combination with 'referencedConcept'.   When specified, the concept nids of the 
	 *            terminologies that this concept is part of on any stamp is returned. This is determined by whether or not there is version of this concept 
	 *            present with a module that extends from one of the children of the {@link MetaData#MODULE____SOLOR} concepts. This is returned as a 
	 *            set, as a concept may exist in multiple terminologies at the same time.  Note, this is populated on the referencedConcept chronology, 
	 *            not on the versions returned, if any.
	 * @param coordToken specifies an explicit serialized CoordinatesToken string specifying all coordinate parameters. A CoordinatesToken may be
	 *            obtained by a separate (prior) call to getCoordinatesToken().
	 *<br>
	 *            <br>The search specifically takes into account the 'modules' and 'path' components of the coordToken (or of individual 'modules' or
	 *            'path' parameters to restrict the search to matching items. 'modules' are also evaluated recursively, so you can pass the identifier 
	 *            for the VHAT_MODULES module (which also happens to be a /system/terminologyTypes value - all "terminologyType" constants work here) to 
	 *            restrict a search to a particular terminology or even a particular version of a terminology.
	 *            
	 * @param altId - (optional) the altId type(s) to populate in any returned RestIdentifiedObject structures.  By default, no alternate IDs are 
	 *     returned.  This can be set to one or more names or ids from the /1/id/types or the value 'ANY'.  Requesting IDs that are unneeded will harm 
	 *     performance. 
	 *
	 * @return the list of semantics that matched, along with their score. Note that the textual value may _NOT_ be included,
	 *         if the semantic that matched is not active on the default path.
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.semanticsComponent)
	public RestSearchResultPage semanticSearch(@QueryParam(RequestParameters.query) String query,
			@QueryParam(RequestParameters.treatAsString) Boolean treatAsString,
			@QueryParam(RequestParameters.semanticAssemblageId) Set<String> semanticAssemblageId,
			@QueryParam(RequestParameters.dynamicSemanticColumns) Set<Integer> dynamicSemanticColumns,
			@QueryParam(RequestParameters.applyStampToConcept) @DefaultValue("true") String applyStampToConcept,
			@QueryParam(RequestParameters.pageNum) @DefaultValue(RequestParameters.pageNumDefault) int pageNum,
			@QueryParam(RequestParameters.maxPageSize) @DefaultValue(RequestParameters.maxPageSizeDefault) int maxPageSize,
			@QueryParam(RequestParameters.expand) String expand, @QueryParam(RequestParameters.coordToken) String coordToken,
			@QueryParam(RequestParameters.altId) String altId) throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.query,
				RequestParameters.treatAsString, RequestParameters.semanticAssemblageId, RequestParameters.dynamicSemanticColumns, RequestParameters.applyStampToConcept, 
				RequestParameters.PAGINATION_PARAM_NAMES, RequestParameters.expand, RequestParameters.COORDINATE_PARAM_NAMES, RequestParameters.altId);
		
		RequestInfo.get().validateMethodExpansions(ExpandUtil.uuid, ExpandUtil.referencedConcept, ExpandUtil.versionsLatestOnlyExpandable, 
				ExpandUtil.versionsAllExpandable, ExpandUtil.countParents, ExpandUtil.includeParents, ExpandUtil.terminologyType);
		
		return semanticSearchInternal(query, treatAsString, semanticAssemblageId, dynamicSemanticColumns, pageNum, maxPageSize, expand, coordToken, altId, 
				StringUtils.isBlank(applyStampToConcept) ? true : RequestInfoUtils.parseBooleanParameter(RequestParameters.applyStampToConcept, applyStampToConcept));
	}
	
	/**
	 * Perform a search of semantic content - the data in the columns of semantics attached to concepts.  This variation is for POST support, 
	 * to remove the need for URI encoding the query.
	 * 
	 * @param query The query to be evaluated. If the query is numeric (int, float, long, double) , it will be treated as a numeric search.
	 *            <br>If the query is a mathematical interval - [4,6] or (5,10] or [4,] it will be handled as a numeric interval.
	 *            <br>If the query is not numeric, and is not a valid interval, it will be treated as a string and parsed by the Lucene Query Parser:
	 *            http://lucene.apache.org/core/5_3_1/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#Overview
	 *            <br>This also supports regular expressions, however, the lucene syntax requires regular expressions to be surrounded by forward slashes - 
	 *            <br>/dat[^a].*./.
	 *            
	 *            In this variation, the query must be posted as a string.
	 *
	 * @param treatAsString Treat the query as a string search, even if it is parseable as a number. This is useful because
	 *            'id' type semantics in the data model are always represented as a string, even if they are numeric.
	 * @param semanticAssemblageId (optional) restrict the search to only match on members of the provided semantic assemblage identifier(s).
	 *            This should be the identifier of a concept that defines a semantic. This parameter can be passed multiple times to pass
	 *            multiple semantic assemblage identifiers. This accepts UUIDs or nids.
	 * @param dynamicSemanticColumns (optional) limit the search to the specified columns of attached data. May ONLY be provided if
	 *            ONE and only one semanticAssemblageNid is provided. May not be provided if 0 or more than 1 semanticAssemblageNid values
	 *            are provided.
	 *            <br>This parameter can be passed multiple times to pass multiple column references. This should be a 0 indexed column number - such as
	 *            0 or 4. Information about the columns for a particular semantic (and their index numbers) can be found via the
	 *            semantic/semantic/semanticDefinition/{id} call. It only makes sense to pass this parameter when searching within a specific semantic
	 *            that has multiple columns of data.
	 * @param applyStampToConcept if true (the default) then the supplied STAMP restrictions will also apply to the concept most closely related to the 
	 *            semantic match.  For example, if searching for active only if a query matches an active semantic, but the concept that the 
	 *            semantic is attached to is inactive, then the concept and all related search hits will be omitted from the final results.
	 * @param pageNum The pagination page number >= 1 to return
	 * @param maxPageSize The maximum number of results to return per page, must be greater than 0
	 * @param expand Optional Comma separated list of fields to expand or include directly in the results. Supports:
	 *            <br> 'uuid' (return the UUID of the matched semantic, rather than just the nid)
	 *            <br> 'referencedConcept' (return the conceptChronology of the nearest concept found by following the referencedComponent references
	 *            of the matched semantic. In most cases, this concept will be the concept that directly contains the semantic - but in some cases,
	 *            semantics may be nested under other semantics causing this to walk up until it finds a concept)
	 *            <br> 'versionsLatestOnly' if 'referencedConcept' is included in the expand list, you may also include 'versionsLatestOnly' to return
	 *            the latest version of the referenced concept chronology.
	 *            <br> 'versionsAll' if 'referencedConcept is included in the expand list, you may also include 'versionsAll' to return all versions of
	 *            the referencedConcept.  For expansions within the referenced concept, such as parents, the stamp is calculated relative to the version
	 *            of the concept being returned.  Callers should handle expansions being null, if the data is unavailable for the timestamp.   Because of 
	 *            this, we always return two copies of the newest version when versionsAll is specified. The first - position 0 in the return - will be 
	 *            calculated with the stamp supplied in the request for all components. The second - position 1 - will contain the same top level version, 
	 *            but any referenced components will have been rendered with a stamp from the version being rendered. Beyond the first two positions, all 
	 *            additional versions are sorted newest to oldest.  
	 *            <br>'countParents' - may only be specified in combination with 'referencedConcept' and ('versionsLatestOnly' or 'versionsAll') - will 
	 *            cause the expanded version to also have the parent count populated.
	 *            <br> 'includeParents' - may only be specified in combination with 'referencedConcept' and ('versionsLatestOnly' or 'versionsAll') - 
	 *            will cause the expanded version to also have the first-level parent list populated.
	 *            <br> 'terminologyType' - may only be specified in combination with 'referencedConcept'.   When specified, the concept nids of the 
	 *            terminologies that this concept is part of on any stamp is returned. This is determined by whether or not there is version of this concept 
	 *            present with a module that extends from one of the children of the {@link MetaData#MODULE____SOLOR} concepts. This is returned as a 
	 *            set, as a concept may exist in multiple terminologies at the same time.  Note, this is populated on the referencedConcept chronology, 
	 *            not on the versions returned, if any.
	 * @param coordToken specifies an explicit serialized CoordinatesToken string specifying all coordinate parameters. A CoordinatesToken may be
	 *            obtained by a separate (prior) call to getCoordinatesToken().
	 *<br>
	 *            <br>The search specifically takes into account the 'modules' and 'path' components of the coordToken (or of individual 'modules' or
	 *            'path' parameters to restrict the search to matching items. 'modules' are also evaluated recursively, so you can pass the identifier 
	 *            for the VHAT_MODULES module (which also happens to be a /system/terminologyTypes value - all "terminologyType" constants work here) to 
	 *            restrict a search to a particular terminology or even a particular version of a terminology.
	 *            
	 * @param altId - (optional) the altId type(s) to populate in any returned RestIdentifiedObject structures.  By default, no alternate IDs are 
	 *     returned.  This can be set to one or more names or ids from the /1/id/types or the value 'ANY'.  Requesting IDs that are unneeded will harm 
	 *     performance. 
	 *
	 * @return the list of semantics that matched, along with their score. Note that the textual value may _NOT_ be included,
	 *         if the semantic that matched is not active on the default path.
	 * @throws RestException
	 */
	@POST
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.semanticsComponent)
	public RestSearchResultPage semanticSearchPost(String query,
			@QueryParam(RequestParameters.treatAsString) Boolean treatAsString,
			@QueryParam(RequestParameters.semanticAssemblageId) Set<String> semanticAssemblageId,
			@QueryParam(RequestParameters.dynamicSemanticColumns) Set<Integer> dynamicSemanticColumns,
			@QueryParam(RequestParameters.applyStampToConcept) @DefaultValue("true") String applyStampToConcept,
			@QueryParam(RequestParameters.pageNum) @DefaultValue(RequestParameters.pageNumDefault) int pageNum,
			@QueryParam(RequestParameters.maxPageSize) @DefaultValue(RequestParameters.maxPageSizeDefault) int maxPageSize,
			@QueryParam(RequestParameters.expand) String expand, 
			@QueryParam(RequestParameters.coordToken) String coordToken,
			@QueryParam(RequestParameters.altId) String altId) throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(),
				RequestParameters.treatAsString, RequestParameters.semanticAssemblageId, RequestParameters.dynamicSemanticColumns, RequestParameters.applyStampToConcept, 
				RequestParameters.PAGINATION_PARAM_NAMES, RequestParameters.expand, RequestParameters.COORDINATE_PARAM_NAMES, RequestParameters.altId);
		
		RequestInfo.get().validateMethodExpansions(ExpandUtil.uuid, ExpandUtil.referencedConcept, ExpandUtil.versionsLatestOnlyExpandable, 
				ExpandUtil.versionsAllExpandable, ExpandUtil.countParents, ExpandUtil.includeParents, ExpandUtil.terminologyType);
		
		return semanticSearchInternal(query, treatAsString, semanticAssemblageId, dynamicSemanticColumns, pageNum, maxPageSize, expand, coordToken, altId, 
				StringUtils.isBlank(applyStampToConcept) ? true : RequestInfoUtils.parseBooleanParameter(RequestParameters.applyStampToConcept, applyStampToConcept));

	}

	private RestSearchResultPage semanticSearchInternal(@QueryParam(RequestParameters.query) String query, Boolean treatAsString, Set<String> semanticAssemblageId,
			Set<Integer> dynamicSemanticColumns, int pageNum, int maxPageSize, String expand, String coordToken, String altId, boolean applyStampToConcept) throws RestException
	{
		String restPath = RestPaths.searchAppPathComponent + RestPaths.semanticsComponent + "?" + RequestParameters.query + "=" + query + "&"
				+ RequestParameters.treatAsString + "=" + treatAsString;
		if (semanticAssemblageId != null)
		{
			for (String id : semanticAssemblageId)
			{
				restPath += "&" + RequestParameters.semanticAssemblageId + "=" + id;
			}
		}
		if (dynamicSemanticColumns != null)
		{
			for (int col : dynamicSemanticColumns)
			{
				restPath += "&" + RequestParameters.dynamicSemanticColumns + "=" + col;
			}
		}
		restPath += (!StringUtils.isBlank(expand) ? ("&" + RequestParameters.expand + "=" + expand) : "");

		String searchString = query != null ? query.trim() : null;
		if (StringUtils.isBlank(searchString))
		{
			throw new RestException("The query must contain at least one character");
		}

		if (treatAsString != null && treatAsString.booleanValue())
		{
			// We want to send in this query text as a string, even if it is parseable as a number, because
			// all "IDs" are stored as string semantics for consistency.
			log.debug("Performing semantic search for '" + query + "' - treating it as a string");

			List<SearchResult> ochreSearchResults = LookupService.get().getService(IndexSemanticQueryService.class).queryData(
					new DynamicStringImpl(searchString), false, processAssemblageRestrictions(semanticAssemblageId), toArray(dynamicSemanticColumns), 
					createStatusTimeFilter(applyStampToConcept, RequestInfo.get().getStampCoordinate()),
					AuthorModulePathRestriction.restrict(RequestInfo.get().getStampCoordinate()), pageNum, maxPageSize, Long.MAX_VALUE);
			return getRestSearchResultsFromOchreSearchResults(ochreSearchResults, pageNum, maxPageSize, restPath, query);
		}
		else
		{
			// Try to determine the most sensible way to search.
			// Is it a number?
			boolean wasNumber = true;
			boolean wasInterval = true;
			try
			{
				List<SearchResult> ochreSearchResults = LookupService.get().getService(IndexSemanticQueryService.class).queryData(
						NumericUtilsDynamic.wrapIntoRefexHolder(NumericUtilsDynamic.parseUnknown(query)), false, processAssemblageRestrictions(semanticAssemblageId),
						toArray(dynamicSemanticColumns), 
						createStatusTimeFilter(applyStampToConcept, RequestInfo.get().getStampCoordinate()),
						AuthorModulePathRestriction.restrict(RequestInfo.get().getStampCoordinate()), pageNum, maxPageSize,
						Long.MAX_VALUE);
				return getRestSearchResultsFromOchreSearchResults(ochreSearchResults, pageNum, maxPageSize, restPath, query);
			}
			catch (NumberFormatException e)
			{
				wasNumber = false;
				// Not a number. Is it an interval?
				try
				{
					Interval interval = new Interval(searchString);
					List<SearchResult> ochreSearchResults = LookupService.get().getService(IndexSemanticQueryService.class).queryNumericRange(
							interval.getLeft(), interval.isLeftInclusive(), interval.getRight(), interval.isRightInclusive(),
							processAssemblageRestrictions(semanticAssemblageId), toArray(dynamicSemanticColumns), 
							createStatusTimeFilter(applyStampToConcept, RequestInfo.get().getStampCoordinate()),
							AuthorModulePathRestriction.restrict(RequestInfo.get().getStampCoordinate()), pageNum, maxPageSize, Long.MAX_VALUE);
					return getRestSearchResultsFromOchreSearchResults(ochreSearchResults, pageNum, maxPageSize, restPath, query);
				}
				catch (NumberFormatException e1)
				{
					wasInterval = false;
					// nope Run it as a string search.
					List<SearchResult> ochreSearchResults = LookupService.get().getService(IndexSemanticQueryService.class).queryData(
							new DynamicStringImpl(searchString), false, processAssemblageRestrictions(semanticAssemblageId), toArray(dynamicSemanticColumns),
							createStatusTimeFilter(applyStampToConcept, RequestInfo.get().getStampCoordinate()),
							AuthorModulePathRestriction.restrict(RequestInfo.get().getStampCoordinate()), pageNum, maxPageSize, Long.MAX_VALUE);
					return getRestSearchResultsFromOchreSearchResults(ochreSearchResults, pageNum, maxPageSize, restPath, query);
				}
			}
			finally
			{
				if (wasNumber)
				{
					log.debug("Performed semantic search for '" + query + "' - treating it as a number");
				}
				else if (wasInterval)
				{
					log.debug("Performed semantic search for '" + query + "' - treating it as an interval");
				}
				else
				{
					log.debug("Performed semantic search for '" + query + "' - treating it as a string");
				}
			}
		}
	}

	/**
	 * Search inside semantic for data columns that contain references to other concepts.  
	 * 
	 * @param nid The nid to search for. Note that this does NOT locate semantics that reference a component as part of the standard
	 *            semantic triplet - (semanticID / Assemblage ID / Referenced Component Id) - those lookups are handled by the
	 *            semantic/byReferencedComponent/{id} API or semantic/byAssemblage/{id} API. This search locates semantic instances that have a DATA
	 *            COLUMN that make reference to a semantic, such as a ComponentNidSemantic, or a Logic Graph.
	 *            <br>An example usage of this API would be to locate the concept that contains a graph that references another concept. The input value
	 *            must be a nid, UUIDs are not supported for this operation.
	 * @param semanticAssemblageId (optional) restrict the search to only match on members of the provided semantic assemblage identifier(s).
	 *            This should be the identifier of a concept that defines a semantic. This parameter can be passed multiple times to pass
	 *            multiple semantic assemblage identifiers. This accepts UUIDs or nids. An example usage would be to restrict the
	 *            search to static logic graphs, as opposed to inferred logic graphs. To restrict to stated, you would pass the id for the
	 *            'EL++ stated form assemblage (ISAAC)' concept
	 * @param dynamicSemanticColumns (optional) limit the search to the specified columns of attached data. May ONLY be provided if
	 *            ONE and only one semanticAssemblageNid is provided. May not be provided if 0 or more than 1 semanticAssemblageNid values
	 *            are provided.
	 *            <br>This parameter can be passed multiple times to pass multiple column references. This should be a 0 indexed column number - such as
	 *            0 or 4. Information about the columns for a particular semantic (and their index numbers) can be found via the
	 *            semantic/semantic/semanticDefinition/{id} call. It only makes sense to pass this parameter when searching within a specific semantic
	 *            that has multiple columns of data.
	 * @param applyStampToConcept if true (the default) then the supplied STAMP restrictions will also apply to the concept most closely related to the 
	 *            semantic match.  For example, if searching for active only if a query matches an active semantic, but the concept that the 
	 *            semantic is attached to is inactive, then the concept and all related search hits will be omitted from the final results.
	 * @param pageNum The pagination page number >= 1 to return
	 * @param maxPageSize The maximum number of results to return per page, must be greater than 0
	 * @param expand Optional Comma separated list of fields to expand or include directly in the results. Supports:
	 *            <br> 'uuid' (return the UUID of the matched semantic, rather than just the nid)
	 *            <br> 'referencedConcept' (return the conceptChronology of the nearest concept found by following the referencedComponent references
	 *            of the matched semantic. In most cases, this concept will be the concept that directly contains the semantic - but in some cases,
	 *            semantics may be nested under other semantics causing this to walk up until it finds a concept)
	 *            <br> 'versionsLatestOnly' if 'referencedConcept' is included in the expand list, you may also include 'versionsLatestOnly' to return
	 *            the latest version of the referenced concept chronology.
	 *            <br> 'versionsAll' if 'referencedConcept is included in the expand list, you may also include 'versionsAll' to return all versions of
	 *            the referencedConcept.  For expansions within the referenced concept, such as parents, the stamp is calculated relative to the version
	 *            of the concept being returned.  Callers should handle expansions being null, if the data is unavailable for the timestamp.   Because of 
	 *            this, we always return two copies of the newest version when versionsAll is specified. The first - position 0 in the return - will be 
	 *            calculated with the stamp supplied in the request for all components. The second - position 1 - will contain the same top level version, 
	 *            but any referenced components will have been rendered with a stamp from the version being rendered. Beyond the first two positions, all 
	 *            additional versions are sorted newest to oldest.  
	 *            <br>'countParents' - may only be specified in combination with 'referencedConcept' and ('versionsLatestOnly' or 'versionsAll') - will 
	 *            cause the expanded version to also have the parent count populated.
	 *            <br> 'includeParents' - may only be specified in combination with 'referencedConcept' and ('versionsLatestOnly' or 'versionsAll') - 
	 *            will cause the expanded version to also have the first-level parent list populated.
	 *            <br> 'terminologyType' - may only be specified in combination with 'referencedConcept'.   When specified, the concept nids of the 
	 *            terminologies that this concept is part of on any stamp is returned. This is determined by whether or not there is version of this concept 
	 *            present with a module that extends from one of the children of the {@link MetaData#MODULE____SOLOR} concepts. This is returned as a 
	 *            set, as a concept may exist in multiple terminologies at the same time.  Note, this is populated on the referencedConcept chronology, 
	 *            not on the versions returned, if any.
	 * @param coordToken specifies an explicit serialized CoordinatesToken string specifying all coordinate parameters. A CoordinatesToken may be
	 *            obtained by a separate (prior) call to getCoordinatesToken().
	 * <br>
	 *            <br>The search specifically takes into account the 'modules' and 'path' components of the coordToken (or of individual 'modules' or
	 *            'path' parameters to restrict the search to matching items. 'modules' are also evaluated recursively, so you can pass the identifier
	 *            for the VHAT_MODULES module (which also happens to be a /system/terminologyTypes value - all "terminologyType" constants work here)
	 *            to restrict a search to a particular terminology or even a particular version of a terminology.
	 *            
	 * @param altId - (optional) the altId type(s) to populate in any returned RestIdentifiedObject structures.  By default, no alternate IDs are 
	 *     returned.  This can be set to one or more names or ids from the /1/id/types or the value 'ANY'.  Requesting IDs that are unneeded will harm 
	 *     performance. 
	 * 
	 * @return the list of semantics that matched, along with their score. Note that the textual value may _NOT_ be included,
	 *         if the semantic that matched is not active on the default path.
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.forReferencedComponentComponent)
	public RestSearchResultPage nidReferences(@QueryParam(RequestParameters.nid) int nid,
			@QueryParam(RequestParameters.semanticAssemblageId) Set<String> semanticAssemblageId,
			@QueryParam(RequestParameters.dynamicSemanticColumns) Set<Integer> dynamicSemanticColumns,
			@QueryParam(RequestParameters.applyStampToConcept) @DefaultValue("true") String applyStampToConcept,
			@QueryParam(RequestParameters.pageNum) @DefaultValue(RequestParameters.pageNumDefault) int pageNum,
			@QueryParam(RequestParameters.maxPageSize) @DefaultValue(RequestParameters.maxPageSizeDefault) int maxPageSize,
			@QueryParam(RequestParameters.expand) String expand, @QueryParam(RequestParameters.coordToken) String coordToken,
			@QueryParam(RequestParameters.altId) String altId) throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.nid,
				RequestParameters.semanticAssemblageId, RequestParameters.dynamicSemanticColumns, RequestParameters.PAGINATION_PARAM_NAMES,
				RequestParameters.applyStampToConcept, RequestParameters.expand, RequestParameters.COORDINATE_PARAM_NAMES, RequestParameters.altId);
		
		RequestInfo.get().validateMethodExpansions(ExpandUtil.uuid, ExpandUtil.referencedConcept, ExpandUtil.versionsLatestOnlyExpandable, 
				ExpandUtil.versionsAllExpandable, ExpandUtil.countParents, ExpandUtil.includeParents, ExpandUtil.terminologyType);

		String restPath = RestPaths.searchAppPathComponent + RestPaths.forReferencedComponentComponent + "?" + RequestParameters.nid + "=" + nid;
		if (semanticAssemblageId != null)
		{
			for (String id : semanticAssemblageId)
			{
				restPath += "&" + RequestParameters.semanticAssemblageId + "=" + id;
			}
		}
		if (dynamicSemanticColumns != null)
		{
			for (int col : dynamicSemanticColumns)
			{
				restPath += "&" + RequestParameters.dynamicSemanticColumns + "=" + col;
			}
		}
		restPath += (!StringUtils.isBlank(expand) ? ("&" + RequestParameters.expand + "=" + expand) : "");

		List<SearchResult> ochreSearchResults = LookupService.get().getService(IndexSemanticQueryService.class).queryNidReference(nid,
				processAssemblageRestrictions(semanticAssemblageId), toArray(dynamicSemanticColumns), 
				createStatusTimeFilter(
						StringUtils.isBlank(applyStampToConcept) ? true : RequestInfoUtils.parseBooleanParameter(RequestParameters.applyStampToConcept, applyStampToConcept),
						RequestInfo.get().getStampCoordinate()),
				AuthorModulePathRestriction.restrict(RequestInfo.get().getStampCoordinate()), pageNum, maxPageSize, Long.MAX_VALUE);
		return getRestSearchResultsFromOchreSearchResults(ochreSearchResults, pageNum, maxPageSize, restPath, nid + "");
	}

	/**
	 * Do a lookup, essentially, of a component by an internal identifier. This supports UUIDs, and NIDs.
	 * Note that, despite the name of the method, this should not be used to search by external identifiers such as VUIDs or SCTIDs - for those, use
	 * the semanticSearch api call.
	 * 
	 * @param query The identifier to look for. Expected to be parseable as a UUID, or an integer.
	 * @param applyStampToConcept if true (the default) then the supplied STAMP restrictions will also apply to the concept most closely related to the 
	 *            id match.  For example, if searching for active only if a query matches an active id, but the concept that the 
	 *            id is attached to is inactive, then the concept and all related search hits will be omitted from the final results.
	 * @param pageNum The pagination page number >= 1 to return
	 * @param maxPageSize The maximum number of results to return per page, must be greater than 0
	 * @param expand Optional Comma separated list of fields to expand or include directly in the results. Supports:
	 *            <br> 'uuid' (return the UUID of the matched semantic, rather than just the nid)
	 *            <br> 'referencedConcept' (return the conceptChronology of the nearest concept found by following the referencedComponent references
	 *            of the matched semantic. In most cases, this concept will be the concept that directly contains the semantic - but in some cases,
	 *            semantics may be nested under other semantics causing this to walk up until it finds a concept)
	 *            <br> 'versionsLatestOnly' if 'referencedConcept' is included in the expand list, you may also include 'versionsLatestOnly' to return
	 *            the latest version of the referenced concept chronology.
	 *            <br> 'versionsAll' if 'referencedConcept is included in the expand list, you may also include 'versionsAll' to return all versions of
	 *            the referencedConcept.  For expansions within the referenced concept, such as parents, the stamp is calculated relative to the version
	 *            of the concept being returned.  Callers should handle expansions being null, if the data is unavailable for the timestamp.   Because of 
	 *            this, we always return two copies of the newest version when versionsAll is specified. The first - position 0 in the return - will be 
	 *            calculated with the stamp supplied in the request for all components. The second - position 1 - will contain the same top level version, 
	 *            but any referenced components will have been rendered with a stamp from the version being rendered. Beyond the first two positions, all 
	 *            additional versions are sorted newest to oldest.  
	 *            <br>'countParents' - may only be specified in combination with 'referencedConcept' and ('versionsLatestOnly' or 'versionsAll') - will 
	 *            cause the expanded version to also have the parent count populated.
	 *            <br> 'includeParents' - may only be specified in combination with 'referencedConcept' and ('versionsLatestOnly' or 'versionsAll') - 
	 *            will cause the expanded version to also have the first-level parent list populated.
	 *            <br> 'terminologyType' - may only be specified in combination with 'referencedConcept'.   When specified, the concept nids of the 
	 *            terminologies that this concept is part of on any stamp is returned. This is determined by whether or not there is version of this concept 
	 *            present with a module that extends from one of the children of the {@link MetaData#MODULE____SOLOR} concepts. This is returned as a 
	 *            set, as a concept may exist in multiple terminologies at the same time.  Note, this is populated on the referencedConcept chronology, 
	 *            not on the versions returned, if any.
	 * @param coordToken specifies an explicit serialized CoordinatesToken string specifying all coordinate parameters. A CoordinatesToken may be
	 *            obtained by a separate (prior) call to getCoordinatesToken().
	 *            
	 * @param altId - (optional) the altId type(s) to populate in any returned RestIdentifiedObject structures.  By default, no alternate IDs are 
	 *     returned.  This can be set to one or more names or ids from the /1/id/types or the value 'ANY'.  Requesting IDs that are unneeded will harm 
	 *     performance. 
	 * 
	 * @return - the list of items that were found that matched - note that if the passed in UUID matched on a semantic - the returned top level
	 *         object will be the concept that references the semantic with the hit. Scores are irrelevant with this call, you will either have an
	 *         exact match, or no result. Typically, there will only be one result .
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.idComponent)
	public RestSearchResultPage idSearch(@QueryParam(RequestParameters.query) String query,
			@QueryParam(RequestParameters.applyStampToConcept) @DefaultValue("true") String applyStampToConcept,
			@QueryParam(RequestParameters.pageNum) @DefaultValue(RequestParameters.pageNumDefault) int pageNum,
			@QueryParam(RequestParameters.maxPageSize) @DefaultValue(RequestParameters.maxPageSizeDefault) int maxPageSize,
			@QueryParam(RequestParameters.expand) String expand, @QueryParam(RequestParameters.coordToken) String coordToken,
			@QueryParam(RequestParameters.altId) String altId) throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.query, RequestParameters.applyStampToConcept, 
				RequestParameters.PAGINATION_PARAM_NAMES, RequestParameters.expand, RequestParameters.COORDINATE_PARAM_NAMES, RequestParameters.altId);
		
		RequestInfo.get().validateMethodExpansions(ExpandUtil.uuid, ExpandUtil.referencedConcept, ExpandUtil.versionsLatestOnlyExpandable, 
				ExpandUtil.versionsAllExpandable, ExpandUtil.countParents, ExpandUtil.includeParents, ExpandUtil.terminologyType);

		List<SearchResult> results = new ArrayList<>();
		final String restPath = RestPaths.searchAppPathComponent + RestPaths.idComponent + "?" + RequestParameters.query + "=" + query;
		boolean applyStampToConceptB = StringUtils.isBlank(applyStampToConcept) ? true : 
			RequestInfoUtils.parseBooleanParameter(RequestParameters.applyStampToConcept, applyStampToConcept);

		if (StringUtils.isBlank(query))
		{
			throw new RestException("The parameter 'query' must be a UUID or an integer for an id query");
		}
		String temp = query.trim();
		Optional<UUID> uuid = UUIDUtil.getUUID(temp);
		IdentifiedObjectService ios = Get.identifiedObjectService();
		if (uuid.isPresent())
		{
			if (Get.identifierService().hasUuid(uuid.get()))
			{
				int foundNid = Get.identifierService().getNidForUuids(uuid.get());
				ios.getChronology(foundNid).ifPresent(chronology -> 
				{
					if (chronology.getLatestVersion(RequestInfo.get().getStampCoordinate()).isPresent())
					{
						results.add(new ComponentSearchResult(foundNid, 1));
					}
				});
			}
		}
		else
		{
			OptionalInt intValue = NumericUtils.getInt(temp);
			if (intValue.isPresent())
			{
				if (intValue.getAsInt() < 0 && Get.identifierService().getObjectTypeForComponent(intValue.getAsInt()) != IsaacObjectType.UNKNOWN)
				{
					ios.getChronology(intValue.getAsInt()).ifPresent(chronology -> 
					{
						if (chronology.getLatestVersion(RequestInfo.get().getStampCoordinate()).isPresent())
						{
							results.add(new ComponentSearchResult(chronology.getNid(), 1));
						}
					});
				}
			}
		}
		
		List<SearchResult> ochreSearchResults = new ArrayList<>();
		for (ConceptSearchResult csr : LookupService.get().getService(DescriptionIndexer.class).mergeResultsOnConcept(results, RequestInfo.get().getStampCoordinate()))
		{
			if (applyStampToConceptB == false || ios.getChronology(csr.getConceptNid()).get().getLatestVersion(RequestInfo.get().getStampCoordinate()).isPresent())
			{
				ochreSearchResults.add((SearchResult) csr);
			}
		}
		return getRestSearchResultsFromOchreSearchResults(ochreSearchResults, pageNum, maxPageSize, restPath, temp);
	}

	private int[] processAssemblageRestrictions(Set<String> semanticAssemblageIds) throws RestException
	{
		int[] nids = new int[semanticAssemblageIds.size()];
		int i = 0;
		for (String id : semanticAssemblageIds)
		{
			nids[i++] = RequestInfoUtils.getConceptNidFromParameter(RequestParameters.semanticAssemblageId, id);
		}

		return nids;
	}

	private int[] toArray(Set<Integer> ints)
	{
		if (ints == null || ints.size() == 0)
		{
			return null;
		}
		int[] temp = new int[ints.size()];
		int i = 0;
		for (Integer in : ints)
		{
			temp[i++] = in;
		}
		return temp;
	}

	private int[] parseDescriptionTypes(Set<String> descriptionTypes) throws RestException
	{
		if (descriptionTypes == null || descriptionTypes.size() == 0)
		{
			return null;
		}
		int[] descriptionNids = new int[descriptionTypes.size()];
		int i = 0;
		for (String s : descriptionTypes)
		{
			String sTrimmed = s.trim().toLowerCase();
			if (NumericUtilsDynamic.isNID(s) || UUIDUtil.isUUID(sTrimmed))
			{
				descriptionNids[i++] = RequestInfoUtils.getConceptNidFromParameter(RequestParameters.descriptionTypes, s);
			}
			else if (sTrimmed.equals("fqn") || sTrimmed.equals("fsn"))
			{
				descriptionNids[i++] = MetaData.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE____SOLOR.getNid();
			}
			else if (sTrimmed.startsWith("regular") || sTrimmed.equals("synonym"))
			{
				descriptionNids[i++] = MetaData.REGULAR_NAME_DESCRIPTION_TYPE____SOLOR.getNid();
			}
			else if (sTrimmed.equals("definition"))
			{
				descriptionNids[i++] = MetaData.DEFINITION_DESCRIPTION_TYPE____SOLOR.getNid();
			}
			else
			{
				throw new RestException(
						"If 'descriptionTypes' is specified, it must be a valid concept identifier, or 'fqn', 'regular' or 'definition'");
			}
		}
		return descriptionNids;
	}

	private int[] parseExtendedDescriptionTypes(Set<String> extendedDescriptionTypes) throws RestException
	{
		if (extendedDescriptionTypes == null || extendedDescriptionTypes.size() == 0)
		{
			return null;
		}
		int[] extendedNids = new int[extendedDescriptionTypes.size()];
		int i = 0;
		for (String s : extendedDescriptionTypes)
		{
			String sTrimmed = s.trim().toLowerCase();
			if (NumericUtilsDynamic.isNID(s) || UUIDUtil.isUUID(sTrimmed))
			{
				extendedNids[i++] = RequestInfoUtils.getConceptNidFromParameter(RequestParameters.extendedDescriptionTypes, s);
			}
			else
			{
				throw new RestException("If 'extendedDescriptionTypes' is specified, it must be a valid concept identifier.");
			}
		}
		return extendedNids;
	}
	
	/**
	 * WARNING this filter only expects to be passed in nids of semantics!
	 * Author / Module / Path are already part of the query, so we really only need to validate time and status.
	 * If time is latest, and status is any, we don't need to do anything - so return null in that case.
	 */
	private Predicate<Integer> createStatusTimeFilter(boolean applyStampToConcept, StampCoordinate sc)
	{
		if (sc.getStampPosition().getTime() == Long.MAX_VALUE && sc.getAllowedStates().size() == Status.ANY_STATUS_SET.size())
		{
			log.debug("No status / time filter required");
			return null;
		}
		
		return new Predicate<Integer>()
		{
			{
				log.debug("status time filter enabled");
			}
			AssemblageService as = Get.assemblageService();
			ConceptService cs = Get.conceptService();
			IdentifiedObjectService ios = Get.identifiedObjectService();
			ConcurrentHashMap<Integer, Boolean> resultCache = new ConcurrentHashMap<>();
			
			@Override
			public boolean test(Integer searchNid)
			{
				SemanticChronology semChron = as.getSemanticChronology(searchNid);
				//This will be passed nids of semantic search results
				boolean semanticIsPresent = resultCache.computeIfAbsent(searchNid, nidAgain -> 
				{
					return semChron.getLatestVersion(sc).isPresent();
				});
				
				if (!semanticIsPresent)
				{
					return false;
				}
				
				else if (applyStampToConcept)
				{
					//Use a Cache here, as hits on one description will usually result in hits on each version of that description, and/or hits on 
					//multiple descriptions for a single concept.  This helps avoid several concept and semantic lookups
					boolean conceptIsPresent = resultCache.computeIfAbsent(semChron.getReferencedComponentNid(), refCompNidAgain ->
					{
						Optional<? extends Chronology> possibleConcept = ios.getChronology(refCompNidAgain);
						
						if (possibleConcept.isPresent())
						{
							if (possibleConcept.get().getIsaacObjectType() != IsaacObjectType.CONCEPT)
							{
								//Let frills recurse - this should be rare.
								int possibleConceptNid = Frills.getNearestConcept(refCompNidAgain).orElse(0);
								if (possibleConceptNid != 0 && cs.getConceptChronology(possibleConceptNid).getLatestVersion(sc).isPresent())
								{
									return true;
								}
								else
								{
									return false;
								}
								
							}
							//The typical path
							return possibleConcept.get().getLatestVersion(sc).isPresent();
						}
						return false;
					});
					
					if (!conceptIsPresent)
					{
						return false;
					}
				}
				return true;
			}
		};
	}
}
