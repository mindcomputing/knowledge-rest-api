/*
 * Copyright 2018 VetsEZ Inc, Sagebits LLC
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
 * 
 * Contributions from 2015-2017 where performed either by US government
 * employees, or under US Veterans Health Administration contracts.
 *
 * US Veterans Health Administration contributions by government employees
 * are work of the U.S. Government and are not subject to copyright
 * protection in the United States. Portions contributed by government
 * employees are USGovWork (17USC §105). Not subject to copyright.
 * 
 * Contribution by contractors to the US Veterans Health Administration
 * during this period are contractually contributed under the
 * Apache License, Version 2.0.
 *
 * See: https://www.usa.gov/government-works
 */

package net.sagebits.tmp.isaac.rest.api1.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.sagebits.tmp.isaac.rest.ExpandUtil;
import net.sagebits.tmp.isaac.rest.Util;
import net.sagebits.tmp.isaac.rest.api.data.PaginationUtils;
import net.sagebits.tmp.isaac.rest.api.exceptions.RestException;
import net.sagebits.tmp.isaac.rest.api1.RestPaths;
import net.sagebits.tmp.isaac.rest.api1.data.enumerations.RestSemanticType;
import net.sagebits.tmp.isaac.rest.api1.data.enumerations.SemanticStyle;
import net.sagebits.tmp.isaac.rest.api1.data.semantic.RestDynamicSemanticDefinition;
import net.sagebits.tmp.isaac.rest.api1.data.semantic.RestDynamicSemanticDefinitionPage;
import net.sagebits.tmp.isaac.rest.api1.data.semantic.RestSemanticChronology;
import net.sagebits.tmp.isaac.rest.api1.data.semantic.RestSemanticVersion;
import net.sagebits.tmp.isaac.rest.api1.data.semantic.RestSemanticVersionPage;
import net.sagebits.tmp.isaac.rest.session.RequestInfo;
import net.sagebits.tmp.isaac.rest.session.RequestInfoUtils;
import net.sagebits.tmp.isaac.rest.session.RequestParameters;
import net.sagebits.uts.auth.data.UserRole.SystemRoleConstants;
import sh.isaac.api.AssemblageService;
import sh.isaac.api.Get;
import sh.isaac.api.Status;
import sh.isaac.api.bootstrap.TermAux;
import sh.isaac.api.chronicle.LatestVersion;
import sh.isaac.api.chronicle.Version;
import sh.isaac.api.chronicle.VersionType;
import sh.isaac.api.collections.NidSet;
import sh.isaac.api.component.concept.ConceptChronology;
import sh.isaac.api.component.concept.ConceptVersion;
import sh.isaac.api.component.semantic.SemanticChronology;
import sh.isaac.api.component.semantic.version.ComponentNidVersion;
import sh.isaac.api.component.semantic.version.SemanticVersion;
import sh.isaac.api.constants.DynamicConstants;
import sh.isaac.api.coordinate.StampCoordinate;
import sh.isaac.api.util.NumericUtils;
import sh.isaac.api.util.UUIDUtil;
import sh.isaac.mapping.constants.IsaacMappingConstants;
import sh.isaac.misc.associations.AssociationUtilities;
import sh.isaac.model.semantic.DynamicUsageDescriptionImpl;
import sh.isaac.utility.Frills;

/**
 * {@link SemanticAPIs}
 *
 * @author <a href="mailto:daniel.armbrust.list@sagebits.net">Dan Armbrust</a>
 */
@Path(RestPaths.semanticAPIsPathComponent)
@RolesAllowed({ SystemRoleConstants.AUTOMATED, SystemRoleConstants.ADMINISTRATOR, SystemRoleConstants.SYSTEM_MANAGER, SystemRoleConstants.CONTENT_MANAGER,
	SystemRoleConstants.EDITOR, SystemRoleConstants.READ })
public class SemanticAPIs
{
	private static Logger log = LogManager.getLogger(SemanticAPIs.class);
	
	//For performance reasons - cache postive or negative calculations.
	//TODO there may be other cases where this cache needs to be invalidated, such as if we import data, or process changesets
	private static final Cache<Integer, SemanticStyle> SEMANTIC_STYLE_CACHE = Caffeine.newBuilder().maximumSize(5000).build();

	@Context
	private SecurityContext securityContext;

	/**
	 * Return the RestSemanticType of the semantic corresponding to the passed id
	 * 
	 * @param id The id for which to determine RestSemanticType
	 *            If an int then assumed to be a semantic NID
	 *            If a String then parsed and handled as a semantic UUID
	 * @param coordToken specifies an explicit serialized CoordinatesToken string specifying all coordinate parameters. A
	 *            CoordinatesToken may be obtained by a separate (prior) call to getCoordinatesToken().
	 * 
	 * @return RestSemanticType of the semantic corresponding to the passed id. if no corresponding semantic found a RestException is thrown.
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.semanticTypeComponent + "{" + RequestParameters.id + "}")
	public RestSemanticType getVersionType(@PathParam(RequestParameters.id) String id, @QueryParam(RequestParameters.coordToken) String coordToken)
			throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.id,
				RequestParameters.COORDINATE_PARAM_NAMES);

		OptionalInt intId = NumericUtils.getInt(id);
		if (intId.isPresent())
		{
			if (Get.assemblageService().hasSemantic(intId.getAsInt()))
			{
				return new RestSemanticType(Get.assemblageService().getSemanticChronology(intId.getAsInt()).getVersionType());
			}
			else
			{
				throw new RestException(RequestParameters.id, id, "Specified semantic int id NID does not correspond to"
						+ " an existing semantic chronology. Must pass a UUID or integer NID that corresponds to an existing semantic chronology.");
			}
		}
		else
		{
			Optional<UUID> uuidId = UUIDUtil.getUUID(id);
			if (uuidId.isPresent())
			{
				// id is uuid
				Integer semanticNid = null;
				if (Get.identifierService().hasUuid(uuidId.get()) && (semanticNid = Get.identifierService().getNidForUuids(uuidId.get())) != 0
						&& Get.assemblageService().hasSemantic(semanticNid))
				{
					return new RestSemanticType(Get.assemblageService().getSemanticChronology(semanticNid).getVersionType());
				}
				else
				{
					throw new RestException(RequestParameters.id, id,
							"Specified semantic UUID does not correspond to an existing semantic chronology. Must pass a "
									+ "UUID or integer NID that corresponds to an existing semantic chronology.");
				}
			}
			else
			{
				throw new RestException(RequestParameters.id, id,
						"Specified semantic string id is not a valid UUID identifier.  Must be a UUID, or integer NID");
			}
		}
	}

	/**
	 * Returns the chronology of a semantic.
	 * 
	 * @param id - A UUID or nid of a semantic
	 * @param expand - A comma separated list of fields to expand. Supports 
	 *       <br> - 'versionsAll' - <p>returns all versions of the semantic.  Note that, this only includes all versions for the top level semantic chronology.
	 *         For nested semantics or referencedDetails, the most appropriate version is returned, relative to the version of the semantic being returned.  
	 *         In other words, the STAMP of the semantic version being returned is used to calculate the appropriate stamp for the referenced component 
	 *         versions, when they are looked up.  
	 *     <br>
	 *         This can lead to a version of a referenced component being unavailable at a calculated stamp, especially in cases where the concept version 
	 *         is older than the earliest version of the referenced component - which can happen depending on which content is loaded (or in what order).  
	 *         Callers should handle null version fields for nested components.
	 *     <br>
	 *         Because the stamp of the concept version being returned might be older than the stamps of the available referenced components, we always return 
	 *         two copies of the newest version when versionsAll is specified.  The first - position 0 in the return - will be calculated with the stamp supplied 
	 *         in the request for all components.  The second - position 1 - will contain the same top level version, but any referenced components will have 
	 *         been rendered with a stamp from the concept version.  Beyond the first two positions, all additional versions are sorted newest to oldest.
	 *         </p>
	 *       <br> - 'versionsLatestOnly' - If latest only is specified in combination with versionsAll, it is ignored (all versions are returned)
	 *       <br> - 'nestedSemantics' - include any other nested semantics on this semantic.
	 *       <br> - 'referencedDetails' - causes it to include the type for the referencedComponent, and, if it is a concept or a description semantic,
	 *            the description of that concept - or the description value.
	 * @param coordToken specifies an explicit serialized CoordinatesToken string specifying all coordinate parameters. A CoordinatesToken may be
	 *            obtained
	 *            by a separate (prior) call to getCoordinatesToken().
	 * @param altId - (optional) the altId type(s) to populate in any returned RestIdentifiedObject structures.  By default, no alternate IDs are 
	 *     returned.  This can be set to one or more names or ids from the /1/id/types or the value 'ANY'.  Requesting IDs that are unneeded will harm 
	 *     performance. 
	 * 
	 * @return the semantic chronology object
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.chronologyComponent + "{" + RequestParameters.id + "}")
	public RestSemanticChronology getSemanticChronology(@PathParam(RequestParameters.id) String id, @QueryParam(RequestParameters.expand) String expand,
			@QueryParam(RequestParameters.coordToken) String coordToken, @QueryParam(RequestParameters.altId) String altId) throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.id, RequestParameters.expand,
				RequestParameters.COORDINATE_PARAM_NAMES, RequestParameters.altId);
		
		RequestInfo.get().validateMethodExpansions(ExpandUtil.versionsAllExpandable, ExpandUtil.versionsLatestOnlyExpandable, ExpandUtil.nestedSemanticsExpandable);

		RestSemanticChronology chronology = new RestSemanticChronology(findSemanticChronology(id),
				RequestInfo.get().shouldExpand(ExpandUtil.versionsAllExpandable), RequestInfo.get().shouldExpand(ExpandUtil.versionsLatestOnlyExpandable),
				RequestInfo.get().shouldExpand(ExpandUtil.nestedSemanticsExpandable), RequestInfo.get().shouldExpand(ExpandUtil.referencedDetails));

		return chronology;
	}

	/**
	 * Returns a single version of a semantic.
	 * If no version parameter is specified, returns the latest version.
	 * 
	 * @param id - A UUID or nid of a semantic 
	 * @param expand - comma separated list of fields to expand. Supports 'chronology', 'nestedSemantics', 'referencedDetails'
	 *            When referencedDetails is passed, nids will include type information, and certain nids will also include their descriptions,
	 *            if they represent a concept or a description semantic.
	 * @param coordToken specifies an explicit serialized CoordinatesToken string specifying all coordinate parameters. A CoordinatesToken may
	 *            be obtained by a separate (prior) call to getCoordinatesToken().
	 * @param altId - (optional) the altId type(s) to populate in any returned RestIdentifiedObject structures.  By default, no alternate IDs are 
	 *     returned.  This can be set to one or more names or ids from the /1/id/types or the value 'ANY'.  Requesting IDs that are unneeded will harm 
	 *     performance. 
	 * @return the semantic version object. Note that the returned type here - RestSemanticVersion is actually an abstract base class,
	 *         the actual return type will be either a RestDynamicSemanticVersion or a RestSemanticDescriptionVersion.
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.versionComponent + "{" + RequestParameters.id + "}")
	public RestSemanticVersion getSemanticVersion(@PathParam(RequestParameters.id) String id, @QueryParam(RequestParameters.expand) String expand,
			@QueryParam(RequestParameters.coordToken) String coordToken, @QueryParam(RequestParameters.altId) String altId) throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.id, RequestParameters.expand,
				RequestParameters.COORDINATE_PARAM_NAMES, RequestParameters.altId);
		
		RequestInfo.get().validateMethodExpansions(ExpandUtil.chronologyExpandable, ExpandUtil.nestedSemanticsExpandable, ExpandUtil.referencedDetails);

		SemanticChronology sc = findSemanticChronology(id);
		LatestVersion<SemanticVersion> sv = sc.getLatestVersion(RequestInfo.get().getStampCoordinate());
		Util.logContradictions(log, sv);
		if (sv.isPresent())
		{
			// TODO handle contradictions
			return RestSemanticVersion.buildRestSemanticVersion(sv.get(), RequestInfo.get().shouldExpand(ExpandUtil.chronologyExpandable),
					RequestInfo.get().shouldExpand(ExpandUtil.nestedSemanticsExpandable), RequestInfo.get().shouldExpand(ExpandUtil.referencedDetails),
					true);
		}
		else
		{
			throw new RestException(RequestParameters.id, id, "No semantic was found");
		}
	}

	public static SemanticChronology findSemanticChronology(String id) throws RestException
	{
		AssemblageService semanticService = Get.assemblageService();

		Optional<UUID> uuidId = UUIDUtil.getUUID(id);
		OptionalInt intId = OptionalInt.empty();
		if (uuidId.isPresent())
		{
			if (Get.identifierService().hasUuid(uuidId.get()))
			{
				intId = OptionalInt.of(Get.identifierService().getNidForUuids(uuidId.get()));
			}
			else
			{
				throw new RestException("id", id, "Is not known by the system");
			}
		}
		else
		{
			intId = NumericUtils.getInt(id);
		}

		if (intId.isPresent())
		{
			Optional<? extends SemanticChronology> sc = semanticService.getOptionalSemanticChronology(intId.getAsInt());
			if (sc.isPresent())
			{
				return sc.get();
			}
			else
			{
				throw new RestException("id", id, "No Semantic was located with the given identifier");
			}
		}
		throw new RestException("id", id, "Is not a semantic identifier.  Must be a UUID or an integer");
	}

	/**
	 * Returns all semantic instances with the given assemblage
	 * If no version parameter is specified, returns the latest version.
	 * If includeAllVersions is specified, returns all versions of each semantic.
	 * 
	 * @param id - A UUID or nid of an assemblage concept
	 * @param pageNum The pagination page number >= 1 to return
	 * @param maxPageSize The maximum number of results to return per page, must be greater than 0
	 * @param expand - comma separated list of fields to expand. Supports 'chronology', 'nestedSemantics', 'referencedDetails'
	 *            When referencedDetails is passed, nids will include type information, and certain nids will also include their descriptions,
	 *            if they represent a concept or a description semantic.
	 * @param includeAllVersions - when true, will return all existing versions of the semantic instances, ignoring the coordinates.
	 * @param coordToken specifies an explicit serialized CoordinatesToken string specifying all coordinate parameters. A CoordinatesToken
	 *            may be obtained by a separate (prior) call to getCoordinatesToken().
	 * @param altId - (optional) the altId type(s) to populate in any returned RestIdentifiedObject structures.  By default, no alternate IDs are 
	 *     returned.  This can be set to one or more names or ids from the /1/id/types or the value 'ANY'.  Requesting IDs that are unneeded will harm 
	 *     performance. 
	 * 
	 * @return the semantic version objects. Note that the returned type here - RestSemanticVersion is actually an abstract base class,
	 *         the actual return type will be either a RestDynamicSemanticVersion or a RestSemanticDescriptionVersion.
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.forAssemblageComponent + "{" + RequestParameters.id + "}")
	public RestSemanticVersionPage getForAssemblage(@PathParam(RequestParameters.id) String id,
			@QueryParam(RequestParameters.pageNum) @DefaultValue(RequestParameters.pageNumDefault) int pageNum,
			@QueryParam(RequestParameters.maxPageSize) @DefaultValue(RequestParameters.maxPageSizeDefault) int maxPageSize,
			@QueryParam(RequestParameters.expand) String expand, 
			@QueryParam(RequestParameters.includeAllVersions) @DefaultValue("false") String includeAllVersions,
			@QueryParam(RequestParameters.coordToken) String coordToken,
			@QueryParam(RequestParameters.altId) String altId) throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.id, RequestParameters.expand,
				RequestParameters.includeAllVersions, RequestParameters.PAGINATION_PARAM_NAMES, RequestParameters.COORDINATE_PARAM_NAMES, RequestParameters.altId);
		
		RequestInfo.get().validateMethodExpansions(ExpandUtil.chronologyExpandable, ExpandUtil.nestedSemanticsExpandable, ExpandUtil.referencedDetails);

		HashSet<Integer> singleAllowedAssemblage = new HashSet<>();
		singleAllowedAssemblage.add(RequestInfoUtils.getConceptNidFromParameter(RequestParameters.id, id));

		// we don't have a referenced component - our id is assemblage
		SemanticVersions versions = get(null, singleAllowedAssemblage, pageNum, maxPageSize, true, Boolean.parseBoolean(includeAllVersions.trim()), null);

		List<RestSemanticVersion> restSemanticVersions = new ArrayList<>();
		for (SemanticVersion sv : versions.getValues())
		{
			restSemanticVersions.add(RestSemanticVersion.buildRestSemanticVersion(sv, RequestInfo.get().shouldExpand(ExpandUtil.chronologyExpandable),
					RequestInfo.get().shouldExpand(ExpandUtil.nestedSemanticsExpandable), RequestInfo.get().shouldExpand(ExpandUtil.referencedDetails),
					false));
		}
		RestSemanticVersionPage results = new RestSemanticVersionPage(pageNum, maxPageSize, versions.getTotal(), true,
				versions.getTotal() > (pageNum * maxPageSize), RestPaths.semanticByAssemblageAppPathComponent + id,
				restSemanticVersions.toArray(new RestSemanticVersion[restSemanticVersions.size()]));

		return results;
	}

	/**
	 * Returns all semantic instances attached to the specified referenced component
	 * If no version parameter is specified, returns the latest version.
	 * 
	 * @param id - A UUID or nid of a component. Note that this could be a concept or a semantic reference.
	 * @param assemblage - An optional assemblage UUID or nid of a concept to restrict the type of semantics returned. If ommitted, assemblages
	 *            of all types will be returned. May be specified multiple times to allow multiple assemblages
	 * @param skipAssemblage - An optional assemblage UUID or nid of a concept to restrict the type of semantics returned. If ommitted, assemblages
	 *            of all types will be returned. May be specified multiple times to skip multiple assemblages.
	 * @param includeDescriptions - an optional flag to request that description type semantics are returned. By default, description type
	 *            semantics are not returned, as these are typically retrieved via a getDescriptions call on the Concept APIs.
	 * @param includeAssociations - an optional flag to request that semantics that represent associations are returned. By default, semantics that
	 *            represent associations are not returned, as these are typically retrieved via a getSourceAssociations call on the Association APIs.
	 * @param includeMappings - an optional flag to request that semantics that represent mappings are returned. By default, semantics that represent
	 *            mappings are not returned, as these are typically retrieved via a the Mapping APIs.
	 * @param includeAllVersions - when true, will return all existing versions of the semantic instances, ignoring the coordinates.
	 * @param expand - comma separated list of fields to expand. Supports 'chronology', 'nestedSemantics', 'referencedDetails'
	 *            When referencedDetails is passed, nids will include type information, and certain nids will also include their descriptions,
	 *            if they represent a concept or a description semantic.
	 * @param coordToken specifies an explicit serialized CoordinatesToken string specifying all coordinate parameters. A CoordinatesToken may be
	 *            obtained by a separate (prior) call to getCoordinatesToken().
	 * @param altId - (optional) the altId type(s) to populate in any returned RestIdentifiedObject structures.  By default, no alternate IDs are 
	 *     returned.  This can be set to one or more names or ids from the /1/id/types or the value 'ANY'.  Requesting IDs that are unneeded will harm 
	 *     performance. 
	 *     
	 * @return the semantic version objects. Note that the returned type here - RestSemanticVersion is actually an abstract base class,
	 *         the actual return type will be either a RestDynamicSemanticVersion or a RestSemanticDescriptionVersion.
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.forReferencedComponentComponent + "{" + RequestParameters.id + "}")
	public RestSemanticVersion[] getForReferencedComponent(@PathParam(RequestParameters.id) String id,
			@QueryParam(RequestParameters.assemblage) Set<String> assemblage,
			@QueryParam(RequestParameters.skipAssemblage) Set<String> skipAssemblage,
			@QueryParam(RequestParameters.includeDescriptions) @DefaultValue("false") String includeDescriptions,
			@QueryParam(RequestParameters.includeAssociations) @DefaultValue("false") String includeAssociations,
			@QueryParam(RequestParameters.includeMappings) @DefaultValue("false") String includeMappings,
			@QueryParam(RequestParameters.includeAllVersions) @DefaultValue("false") String includeAllVersions,
			@QueryParam(RequestParameters.expand) String expand,
			@QueryParam(RequestParameters.coordToken) String coordToken,
			@QueryParam(RequestParameters.altId) String altId) throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.id, RequestParameters.assemblage,
				RequestParameters.includeDescriptions, RequestParameters.includeAssociations, RequestParameters.includeMappings, RequestParameters.includeAllVersions,
				RequestParameters.expand, RequestParameters.COORDINATE_PARAM_NAMES, RequestParameters.altId);
		
		RequestInfo.get().validateMethodExpansions(ExpandUtil.chronologyExpandable, ExpandUtil.nestedSemanticsExpandable, ExpandUtil.referencedDetails);

		HashSet<Integer> allowedAssemblages = new HashSet<>();
		for (String a : assemblage)
		{
			allowedAssemblages.add(RequestInfoUtils.getConceptNidFromParameter(RequestParameters.assemblage, a));
		}
		
		HashSet<Integer> skipAssemblages = new HashSet<>();
		for (String a : skipAssemblage)
		{
			skipAssemblages.add(RequestInfoUtils.getConceptNidFromParameter(RequestParameters.skipAssemblage, a));
		}

		return get(id, allowedAssemblages, skipAssemblages,
				RequestInfo.get().shouldExpand(ExpandUtil.chronologyExpandable), RequestInfo.get().shouldExpand(ExpandUtil.nestedSemanticsExpandable),
				RequestInfo.get().shouldExpand(ExpandUtil.referencedDetails), 
				Boolean.parseBoolean(includeAllVersions.trim()),
				Boolean.parseBoolean(includeDescriptions.trim()),
				Boolean.parseBoolean(includeAssociations.trim()), Boolean.parseBoolean(includeMappings.trim()));
	}

	/**
	 * Return the full description of a particular semantic - including its intended use, the types of any data columns that will be attached, etc.
	 * 
	 * @param id - The UUID or nid of the concept that represents the semantic assemblage.
	 * @param coordToken specifies an explicit serialized CoordinatesToken string specifying all coordinate parameters. A CoordinatesToken may be
	 *            obtained by a separate (prior) call to getCoordinatesToken().
	 * 
	 * @return - the full description
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.semanticDefinitionComponent + "{" + RequestParameters.id + "}")
	public RestDynamicSemanticDefinition getSemanticDefinition(@PathParam(RequestParameters.id) String id,
			@QueryParam(RequestParameters.coordToken) String coordToken) throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.id,
				RequestParameters.COORDINATE_PARAM_NAMES);

		int conceptNid = RequestInfoUtils.getConceptNidFromParameter(RequestParameters.id, id);
		if (DynamicUsageDescriptionImpl.isDynamicSemanticFullRead(conceptNid))
		{
			return new RestDynamicSemanticDefinition(DynamicUsageDescriptionImpl.read(conceptNid));
		}
		try
		{
			return new RestDynamicSemanticDefinition(DynamicUsageDescriptionImpl.mockOrRead(conceptNid));
		}
		catch (Exception e)
		{
			throw new RestException("The specified concept identifier is not configured as a dynamic semantic, and it is not used as a static semantic, (and "
					+ "it doesn't have proper static metadata defined)");
		}
	}
	
	//TODO write the Unit tests for this method
	/**
	 * Return the full description of all of the defined semantics in the system - each result includes its intended use, the types of any data columns that 
	 * will be attached (if any), etc.
	 * @param coordToken specifies an explicit serialized CoordinatesToken string specifying all coordinate parameters. A CoordinatesToken may
	 *            be obtained by a separate (prior) call to getCoordinatesToken().
	 * @param restrictTo Optional comma separated list that will restrict the resulting semantics that meets the specified criteria. Currently, this can be set to:
	 *            <br> "association" - to only return concepts that define association types, which are semantics with a specific structure
	 *            <br> "mapset" - to only return concepts that define mapsets, which are semantics with a specific structure
	 *            <br> "refset" - to only return concepts that define refsets, which are membership-only semantics (0 data columns)
	 *            <br> "property" - to only return concepts that define properties, which are semantics that also have data column(s).  This will also 
	 *                include association and mapset semantics, as those have data columns.
	 *            Not specifying a value resulting in returning all semantic definitions.
	 * @param altId - (optional) the altId type(s) to populate in any returned RestIdentifiedObject structures.  By default, no alternate IDs are 
	 *     returned.  This can be set to one or more names or ids from the /1/id/types or the value 'ANY'.  Requesting IDs that are unneeded will harm 
	 *     performance. 
	 * @param sortFull - (optional) When set to true, calculate the entire result set, and sort it properly prior to paging.  This can significantly 
	 *     increase the time it takes to calculate the results.  When false (the default if not specified) each page of results that is returned is sorted,
	 *     but only within that page.
	 * @param pageNum The optional pagination page number >= 1 to return - defaults to 1
	 * @param maxPageSize The maximum number of results to return per page, must be greater than 0
	 * @return the latest version of each unique semantic definition found in the system on the specified coordinates.
	 * 
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.semanticDefinitionsComponent)
	public RestDynamicSemanticDefinitionPage getSemanticDefinitions(
			@QueryParam(RequestParameters.coordToken) String coordToken, 
			@QueryParam(RequestParameters.restrictTo) String restrictTo,
			@QueryParam(RequestParameters.altId) String altId,
			@QueryParam(RequestParameters.sortFull) @DefaultValue("false") String sortFull,
			@QueryParam(RequestParameters.pageNum) @DefaultValue(RequestParameters.pageNumDefault) int pageNum,
			@QueryParam(RequestParameters.maxPageSize) @DefaultValue(RequestParameters.maxPageSizeDefault) int maxPageSize) throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.restrictTo,
				RequestParameters.COORDINATE_PARAM_NAMES, RequestParameters.maxPageSize, RequestParameters.pageNum, RequestParameters.altId, RequestParameters.sortFull);
		PaginationUtils.validateParameters(pageNum, maxPageSize);
		
		boolean sortFullBoolean = Boolean.parseBoolean(sortFull.trim());
		ArrayList<String> nonFatalExceptionMessages = new ArrayList<>();
		
		HashSet<String> uniqueRestrictions = new HashSet<>();
		List<String> splitRestrictions = RequestInfoUtils.expandCommaDelimitedElements(restrictTo); 
		
		TreeSet<Integer> semanticDefinitionConcepts = new TreeSet<>();
		
		if (splitRestrictions != null && splitRestrictions.size() > 0)
		{
			
			for (String restriction : splitRestrictions)
			{
				if (restriction.equals("association") || restriction.equals("mapset") || restriction.equals("refset") || restriction.equals("property"))
				{
					uniqueRestrictions.add(restriction);
				}
				else
				{
					throw new RestException("Invalid restrictTo option - supported values: 'association, mapset, refset, property'");
				}
			}
			
			if (uniqueRestrictions.contains("property") && uniqueRestrictions.contains("refset"))
			{
				//this is the same as asking for all.
				uniqueRestrictions.clear();
			}
			if (uniqueRestrictions.contains("property"))
			{
				//these will come along for free when we process property, no need to process again.
				uniqueRestrictions.remove("mapset");
				uniqueRestrictions.remove("association");
			}
		}
		
		int neededResults = maxPageSize * pageNum;
		boolean totalIsExact = true;
		
		if (uniqueRestrictions.size() > 0)
		{
			for (String restriction : uniqueRestrictions)
			{
				if (!sortFullBoolean && semanticDefinitionConcepts.size() > neededResults)
				{
					totalIsExact = false;
					break;
				}
				if (restriction.equals("association"))
				{
					addSemanticsOfType(semanticDefinitionConcepts, DynamicConstants.get().DYNAMIC_ASSOCIATION.getNid(), nonFatalExceptionMessages, null);
				}
				else if (restriction.equals("mapset"))
				{
					if (!sortFullBoolean && semanticDefinitionConcepts.size() > neededResults)
					{
						totalIsExact = false;
						break;
					}
					addSemanticsOfType(semanticDefinitionConcepts, IsaacMappingConstants.get().DYNAMIC_SEMANTIC_MAPPING_SEMANTIC_TYPE.getNid(), nonFatalExceptionMessages,null);
				}
				else if (restriction.equals("property"))
				{
					if (!sortFullBoolean && semanticDefinitionConcepts.size() > neededResults)
					{
						totalIsExact = false;
						break;
					}
					//All semantics with >0 data columns
					addSemanticsOfType(semanticDefinitionConcepts, DynamicConstants.get().DYNAMIC_DEFINITION_DESCRIPTION.getNid(), nonFatalExceptionMessages,
							semanticC -> 
					{
						return DynamicUsageDescriptionImpl.read(Frills.getNearestConcept(semanticC.getNid()).get()).getColumnInfo().length > 0;
					});
					
					if (!sortFullBoolean && semanticDefinitionConcepts.size() > neededResults)
					{
						totalIsExact = false;
						break;
					}
					
					//Our mockOrRead hack currently just supports these types
					addSemanticsOfType(semanticDefinitionConcepts,TermAux.SEMANTIC_TYPE.getNid(), nonFatalExceptionMessages, semanticC -> 
					{
						//These should be component semantics, where the data tells us the type.  Only want ones with data columns
						//Ignoring some types here, which, in practice, we never use, as use use dynamic semantics....  mockOrRead
						//doesn't support all of the others, at least when only defined by the semantic concept.
						if (semanticC.getVersionType() == VersionType.COMPONENT_NID)
						{
							LatestVersion<ComponentNidVersion> sv = semanticC.getLatestVersion(RequestInfo.get().getStampCoordinate()
									.makeCoordinateAnalog(Status.ANY_STATUS_SET));
							if (sv.isPresent())
							{
								return sv.get().getComponentNid() == TermAux.STRING_SEMANTIC.getNid() ||
										sv.get().getComponentNid() == TermAux.COMPONENT_SEMANTIC.getNid() ||
										sv.get().getComponentNid() == TermAux.DESCRIPTION_SEMANTIC.getNid() ||
										sv.get().getComponentNid() == TermAux.LOGICAL_EXPRESSION_SEMANTIC.getNid() ||
										sv.get().getComponentNid() == TermAux.INTEGER_SEMANTIC.getNid();
							}
							else
							{
								return false;
							}
						}
						else
						{
							log.error("Misconfigured metadata! for {}", semanticC);
							return false;
						}
					});
				}
				else if (restriction.equals("refset"))
				{
					if (!sortFullBoolean && semanticDefinitionConcepts.size() > neededResults)
					{
						totalIsExact = false;
						break;
					}
					//semantics with 0 data columns
					addSemanticsOfType(semanticDefinitionConcepts, DynamicConstants.get().DYNAMIC_DEFINITION_DESCRIPTION.getNid(), nonFatalExceptionMessages,
							semanticC -> 
					{
						return DynamicUsageDescriptionImpl.read(Frills.getNearestConcept(semanticC.getNid()).get()).getColumnInfo().length == 0;
					});
					if (!sortFullBoolean && semanticDefinitionConcepts.size() > neededResults)
					{
						totalIsExact = false;
						break;
					}
					addSemanticsOfType(semanticDefinitionConcepts,TermAux.SEMANTIC_TYPE.getNid(), nonFatalExceptionMessages, semanticC -> 
					{
						//These should be component semantics, where the data tells us the type.  Only want membership semantic ones
						if (semanticC.getVersionType() == VersionType.COMPONENT_NID)
						{
							LatestVersion<ComponentNidVersion> sv = semanticC.getLatestVersion(RequestInfo.get().getStampCoordinate()
									.makeCoordinateAnalog(Status.ANY_STATUS_SET));
							if (sv.isPresent())
							{
								return sv.get().getComponentNid() == TermAux.MEMBERSHIP_SEMANTIC.getNid();
							}
							else
							{
								return false;
							}
						}
						else
						{
							log.error("Misconfigured metadata! for {}", semanticC);
							return false;
						}
					});
				}
			}
		}
		else
		{
			totalIsExact = false;
			//just find all dynamic semantics, then add in some static ones.
			addSemanticsOfType(semanticDefinitionConcepts, DynamicConstants.get().DYNAMIC_DEFINITION_DESCRIPTION.getNid(), nonFatalExceptionMessages, null);
			if (sortFullBoolean || semanticDefinitionConcepts.size() < neededResults)
			{
				//Anything with a SemanticFieldsAssemblage should qualify...
				addSemanticsOfType(semanticDefinitionConcepts,TermAux.ASSEMBLAGE_SEMANTIC_FIELDS.getNid(), nonFatalExceptionMessages, null);

				//Note, this will still miss static semantics which aren't properly annotated...
				if (sortFullBoolean || semanticDefinitionConcepts.size() < neededResults)
				{
					addSemanticsOfType(semanticDefinitionConcepts,TermAux.SEMANTIC_TYPE.getNid(), nonFatalExceptionMessages, null);
					totalIsExact = true;
				}
			}
		}
		

		ArrayList<RestDynamicSemanticDefinition> resultPage = new ArrayList<>(maxPageSize);
		
		if (sortFullBoolean)
		{
			//We have to read them all, so we can sort them properly, before subsetting.
			ArrayList<RestDynamicSemanticDefinition> all = new ArrayList<>(semanticDefinitionConcepts.size());
			for (int semanticConceptId : semanticDefinitionConcepts)
			{
				try
				{
					all.add(new RestDynamicSemanticDefinition(DynamicUsageDescriptionImpl.mockOrRead(semanticConceptId)));
				}
				catch (Exception e)
				{
					log.error("Misconfigured metadata! for {}", semanticConceptId, e);
				}
			}
			Collections.sort(all);
			resultPage.addAll(PaginationUtils.getResults(all, pageNum, maxPageSize));
		}
		else
		{
			//otherwise, subset on just the nids, then sort after making a page
			ArrayList<Integer> temp = new ArrayList<>(semanticDefinitionConcepts.size());
			temp.addAll(semanticDefinitionConcepts);
			
			for (int semanticConceptId : PaginationUtils.getResults(temp, pageNum, maxPageSize))
			{
				try
				{
					resultPage.add(new RestDynamicSemanticDefinition(DynamicUsageDescriptionImpl.mockOrRead(semanticConceptId)));
				}
				catch (Exception e)
				{
					log.error("Misconfigured metadata! for {}", semanticConceptId, e);
				}
			}
			Collections.sort(resultPage);
		}
		
		return new RestDynamicSemanticDefinitionPage(pageNum, maxPageSize, semanticDefinitionConcepts.size(), totalIsExact, 
				(!totalIsExact || semanticDefinitionConcepts.size() > neededResults), RestPaths.semanticDefinitionsComponent, resultPage, nonFatalExceptionMessages);
	}
	
	private void addSemanticsOfType(Set<Integer> results, int typeNid, ArrayList<String> nonFatalExceptionMessages, Function<SemanticChronology, Boolean> filterFunction)
	{
		Get.assemblageService().getSemanticChronologyStream(typeNid).forEach(semanticC -> {
			try
			{
				//Dynamic semantics are nested... need to walk up two, to get to the concept...
				Optional<Integer> nearestConcept = Frills.getNearestConcept(semanticC.getNid());
				
				if (nearestConcept.isPresent())
				{
					ConceptChronology cc = Get.conceptService().getConceptChronology(nearestConcept.get());
					LatestVersion<ConceptVersion> cv = cc.getLatestVersion(RequestInfo.get().getStampCoordinate());
					Util.logContradictions(log, cv);
					if (cv.isPresent())
					{
						if (filterFunction == null || filterFunction.apply(semanticC))
						{
							results.add(cc.getNid());
						}
					}
				}
				else
				{
					nonFatalExceptionMessages.add("Failed to find an expected concept");
					log.error("No concept found from semantic??? {}", semanticC);
				}
			}
			catch (Exception e)
			{
				nonFatalExceptionMessages.add("Error processing a concept that appears to be a semantic");
				log.error("Error processing semantic!", e);
			}
		});
	}
	

	public static Stream<SemanticChronology> getSemanticChronologyStreamForComponentFromAssemblagesFilteredByVersionType(int componentNid,
			Set<Integer> allowedAssemblageNids, Set<VersionType> typesToExclude)
	{
		NidSet semanticNids = Get.assemblageService().getSemanticNidsForComponentFromAssemblages(componentNid, allowedAssemblageNids);
		if (typesToExclude == null || typesToExclude.size() == 0)
		{
			return semanticNids.stream().mapToObj((int semanticNid) -> Get.assemblageService().getSemanticChronology(semanticNid));
		}
		else
		{
			final ArrayList<SemanticChronology> filteredList = new ArrayList<>();
			for (PrimitiveIterator.OfInt it = semanticNids.getIntIterator(); it.hasNext();)
			{
				SemanticChronology chronology = Get.assemblageService().getSemanticChronology(it.nextInt());
				boolean exclude = false;
				for (VersionType type : typesToExclude)
				{
					if (chronology.getVersionType() == type)
					{
						exclude = true;
						break;
					}
				}

				if (!exclude)
				{
					filteredList.add(chronology);
				}
			}

			return filteredList.stream();
		}
	}

	public static class SemanticVersions
	{
		private final List<SemanticVersion> values;
		private final int approximateTotal;

		public SemanticVersions(List<SemanticVersion> values, int approximateTotal)
		{
			this.values = values;
			this.approximateTotal = approximateTotal;
		}

		public SemanticVersion[] getValues()
		{
			return values.toArray(new SemanticVersion[values.size()]);
		}

		public int getTotal()
		{
			return approximateTotal;
		}
	}

	/**
	 * @param referencedComponent - optional - if provided - takes precedence
	 * @param allowedAssemblages - optional - if provided, either limits the referencedComponent search by this type, or, if
	 *            referencedComponent is not provided - focuses the search on just this assemblage
	 * @param pageNum 
	 * @param maxPageSize 
	 * @param allowDescriptions true to include description type semantics, false to skip
	 * @param includeAllVersions - true for all versions, ignoring stamp, false for latest only on given stamp
	 * @param stamp - optional - when includeAllVersions is false, use this stamp for populating the version to return.  If not provided, 
	 *     the stamp is read from the RequestInfo.  
	 * @return the semantic versions wrapped for paging
	 * @throws RestException
	 */
	public static SemanticVersions get(String referencedComponent, Set<Integer> allowedAssemblages, final int pageNum, final int maxPageSize,
			boolean allowDescriptions, boolean includeAllVersions, StampCoordinate stamp) throws RestException
	{
		PaginationUtils.validateParameters(pageNum, maxPageSize);

		Set<VersionType> excludedVersionTypes = new HashSet<>();
		excludedVersionTypes.add(VersionType.LOGIC_GRAPH);
		if (!allowDescriptions)
		{
			excludedVersionTypes.add(VersionType.DESCRIPTION);
		}
		
		if (includeAllVersions && maxPageSize < Integer.MAX_VALUE)
		{
			throw new RestException("Paging not currently supported in combination with includeAllVersions");
		}
		
		StampCoordinate stampToUse = null;
		if (!includeAllVersions)
		{
			stampToUse = stamp == null ? RequestInfo.get().getStampCoordinate() : stamp;
		}

		final List<SemanticVersion> ochreResults = new ArrayList<>();

		if (StringUtils.isNotBlank(referencedComponent))
		{
			Optional<UUID> uuidId = UUIDUtil.getUUID(referencedComponent);
			OptionalInt refCompNid = OptionalInt.empty();
			if (uuidId.isPresent())
			{
				if (Get.identifierService().hasUuid(uuidId.get()))
				{
					refCompNid = OptionalInt.of(Get.identifierService().getNidForUuids(uuidId.get()));
				}
				else
				{
					throw new RestException("referencedComponent", referencedComponent, "Is not known by the system");
				}
			}
			else
			{
				refCompNid = NumericUtils.getInt(referencedComponent);
			}

			if (refCompNid.isPresent() && refCompNid.getAsInt() < 0)
			{
				Stream<SemanticChronology> semantics = getSemanticChronologyStreamForComponentFromAssemblagesFilteredByVersionType(refCompNid.getAsInt(),
						allowedAssemblages, excludedVersionTypes);

				int approximateTotal = 0;
				for (Iterator<SemanticChronology> it = semantics.iterator(); it.hasNext();)
				{
					if (ochreResults.size() >= (pageNum * maxPageSize))
					{
						it.next();
						continue;
					}
					else
					{
						SemanticChronology chronology = it.next();
						if (includeAllVersions)
						{
							for (Version v : chronology.getVersionList())
							{
								ochreResults.add((SemanticVersion)v);
							}
						}
						else
						{
							LatestVersion<SemanticVersion> sv = chronology.getLatestVersion(stampToUse);
							Util.logContradictions(log, sv);
							if (sv.isPresent())
							{
								// TODO handle contradictions
								ochreResults.add(sv.get());
							}
						}
					}

					approximateTotal++;
				}

				return new SemanticVersions(PaginationUtils.getResults(PaginationUtils.getResults(ochreResults, pageNum, maxPageSize), pageNum, maxPageSize),
						approximateTotal);
			}
			else
			{
				throw new RestException("referencedComponent", referencedComponent, "Must be a NID or a UUID");
			}
		}
		else
		{
			if (allowedAssemblages == null || allowedAssemblages.size() == 0)
			{
				throw new RestException("If a referenced component is not provided, then an allowedAssemblage must be provided");
			}

			NidSet allSemanticNids = new NidSet();
			for (int assemblageId : allowedAssemblages)
			{
				allSemanticNids.addAll(Get.assemblageService().getSemanticNidsFromAssemblage(assemblageId).stream());
			}

			for (PrimitiveIterator.OfInt it = allSemanticNids.getIntIterator(); it.hasNext();)
			{
				if (ochreResults.size() >= (pageNum * maxPageSize))
				{
					break;
				}
				else
				{
					SemanticChronology chronology = Get.assemblageService().getSemanticChronology(it.nextInt());
					if (includeAllVersions)
					{
						for (Version v : chronology.getVersionList())
						{
							ochreResults.add((SemanticVersion)v);
						}
					}
					else
					{
						LatestVersion<SemanticVersion> sv = ((SemanticChronology) chronology).getLatestVersion(stampToUse);
						Util.logContradictions(log, sv);
						if (sv.isPresent())
						{
							ochreResults.add(sv.get());
						}
					}
				}
			}

			return new SemanticVersions(PaginationUtils.getResults(ochreResults, pageNum, maxPageSize), allSemanticNids.size());
		}
	}

	/**
	 * @param referencedComponent - optional - if provided - takes precedence
	 * @param allowedAssemblages - optional - if provided, either limits the referencedComponent search by this type, or, if
	 *            referencedComponent is not provided - focuses the search on just this assemblage
	 * @param skipAssemblages - optional - if provided, any assemblage listed here will not be part of the return. This takes priority over the
	 *            allowedAssemblages.  Takes priority over allowedAssemblages
	 * @param expandChronology
	 * @param expandNested
	 * @param expandReferenced
	 * @param includeAllVersions - false, to return the latest version (for the specified stamp), true to include all versions of each semantic, 
	 *     ignoring the stamp.
	 * @param allowDescriptions true to include description type semantics, false to skip
	 * @param allowAssociations true to include semantics that represent associations, false to skip
	 * @param allowMappings true to include semantics that represent mappings, false to skip
	 * @return the semantics
	 * @throws RestException
	 */
	public static RestSemanticVersion[] get(String referencedComponent, Set<Integer> allowedAssemblages, Set<Integer> skipAssemblages, boolean expandChronology,
			boolean expandNested, boolean expandReferenced, boolean includeAllVersions, boolean allowDescriptions, boolean allowAssociations, boolean allowMappings)
					throws RestException
	{
		final ArrayList<RestSemanticVersion> results = new ArrayList<>();
		Consumer<SemanticChronology> consumer = new Consumer<SemanticChronology>()
		{
			@Override
			public void accept(SemanticChronology sc)
			{
				if (sc.getVersionType() != VersionType.LOGIC_GRAPH && (allowDescriptions || sc.getVersionType() != VersionType.DESCRIPTION))
				{
					if (!allowAssociations && AssociationUtilities.isAssociation(sc))
					{
						return;
					}
					if (!allowMappings && Frills.isMapping(sc))
					{
						return;
					}
					if (skipAssemblages != null && skipAssemblages.contains(sc.getAssemblageNid()))
					{
						return;
					}
					if (includeAllVersions)
					{
						for (Version sv : sc.getVersionList())
						{
							try
							{
								results.add(RestSemanticVersion.buildRestSemanticVersion((SemanticVersion)sv, expandChronology, expandNested, expandReferenced, 
										false));
							}
							catch (RestException e)
							{
								throw new RuntimeException("Unexpected error", e);
							}
						}
					}
					else
					{
						LatestVersion<SemanticVersion> sv = sc.getLatestVersion(RequestInfo.get().getStampCoordinate());
						Util.logContradictions(log, sv);
						if (sv.isPresent())
						{
							try
							{
								// TODO handle contradictions
								results.add(RestSemanticVersion.buildRestSemanticVersion(sv.get(), expandChronology, expandNested, expandReferenced, true));
							}
							catch (RestException e)
							{
								throw new RuntimeException("Unexpected error", e);
							}
						}
					}
				}
			}
		};

		if (StringUtils.isNotBlank(referencedComponent))
		{
			Optional<UUID> uuidId = UUIDUtil.getUUID(referencedComponent);
			OptionalInt refCompNid = OptionalInt.empty();
			if (uuidId.isPresent())
			{
				if (Get.identifierService().hasUuid(uuidId.get()))
				{
					refCompNid = OptionalInt.of(Get.identifierService().getNidForUuids(uuidId.get()));
				}
				else
				{
					throw new RestException("referencedComponent", referencedComponent, "Is not known by the system");
				}
			}
			else
			{
				refCompNid = NumericUtils.getInt(referencedComponent);
			}

			if (refCompNid.isPresent() && refCompNid.getAsInt() < 0)
			{
				Stream<SemanticChronology> semantics = Get.assemblageService().getSemanticChronologyStreamForComponentFromAssemblages(refCompNid.getAsInt(),
						allowedAssemblages);
				semantics.forEach(consumer);
			}
			else
			{
				throw new RestException("referencedComponent", referencedComponent, "Must be a NID or a UUID");
			}
		}
		else
		{
			if (allowedAssemblages == null || allowedAssemblages.size() == 0)
			{
				throw new RestException("If a referenced component is not provided, then an allowedAssemblage must be provided");
			}
			for (int assemblageId : allowedAssemblages)
			{
				Get.assemblageService().getSemanticChronologyStream(assemblageId).forEach(consumer);
			}
		}
		return results.toArray(new RestSemanticVersion[results.size()]);
	}
	
	public static void uncacheSemanticStyle(int nid)
	{
		SemanticAPIs.SEMANTIC_STYLE_CACHE.invalidate(nid);
	}
	
	/**
	 * Returns cached answers - semantic assemblage or not.  Must uncache, if a concept is annotated as a semantic
	 * @param nid
	 * @return
	 */
	public static SemanticStyle getSemanticStyle(int nid)
	{
		return SEMANTIC_STYLE_CACHE.get(nid, nidAgain ->
		{
			if (Frills.definesSemantic(nid))
			{
				if (DynamicUsageDescriptionImpl.mockOrRead(nid).getColumnInfo().length > 0)
				{
					if (Frills.definesAssociation(nid))
					{
						return SemanticStyle.ASSOCIATION;
					}
					else if (Frills.definesMapping(nid))
					{
						return SemanticStyle.MAPSET;
					}
					return SemanticStyle.PROPERTY;
				}
				else
				{
					return SemanticStyle.REFSET;
				}
			}
			return SemanticStyle.NONE;
		});
		
	}
}
