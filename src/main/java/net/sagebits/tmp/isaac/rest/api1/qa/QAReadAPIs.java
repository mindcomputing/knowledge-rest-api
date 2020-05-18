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
package net.sagebits.tmp.isaac.rest.api1.qa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import net.sagebits.tmp.isaac.rest.api.exceptions.RestException;
import net.sagebits.tmp.isaac.rest.api1.RestPaths;
import net.sagebits.tmp.isaac.rest.api1.data.qa.RestQAResult;
import net.sagebits.tmp.isaac.rest.session.RequestInfo;
import net.sagebits.tmp.isaac.rest.session.RequestParameters;
import net.sagebits.uts.auth.data.UserRole.SystemRoleConstants;
import sh.isaac.api.Get;
import sh.isaac.api.util.UUIDUtil;
import sh.isaac.provider.qa.QAResult;
import sh.isaac.provider.qa.QARunStorage;

/**
 * {@link QAReadAPIs}
 *
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a>
 */
@Path(RestPaths.qaAPIsPathComponent)
@RolesAllowed({ SystemRoleConstants.AUTOMATED, SystemRoleConstants.ADMINISTRATOR, SystemRoleConstants.SYSTEM_MANAGER, SystemRoleConstants.CONTENT_MANAGER,
		SystemRoleConstants.EDITOR, SystemRoleConstants.READ })
public class QAReadAPIs
{
	@Context
	private SecurityContext securityContext;

	/**
	 * Get the current information on the specified classifier run
	 * @param id - The ID of a classifier run.  These UUIDs are returned via the call that launches the classifier.
	 * @param altId - (optional) the altId type(s) to populate in any returned RestIdentifiedObject structures.  By default, no alternate IDs are 
	 *     returned.  This can be set to one or more names or ids from the <code>/1/id/types</code> or the value 'ANY'.  Requesting IDs that are unneeded will harm 
	 *     performance. 
     * @param largeResults - If false, or unspecified, the individual QA failures will be limited to 100.  
	 *     To include all failures, set this to true.
	 * @return The details on the QA run.
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.run + "{" + RequestParameters.id + "}")
	public RestQAResult read(@PathParam(RequestParameters.id) String id, @QueryParam(RequestParameters.altId) String altId,
			@QueryParam(RequestParameters.largeResults) @DefaultValue("false") String largeResults) throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.id, RequestParameters.altId, 
				RequestParameters.largeResults);
		
		if (StringUtils.isBlank(id))
		{
			throw new RestException("id", "The id for the qa run is required");
		}
		
		boolean limitResults = !Boolean.parseBoolean(largeResults.trim());
		
		Optional<UUID> qaKey = UUIDUtil.getUUID(id.trim());
		if (!qaKey.isPresent())
		{
			throw new RestException("id", id, "The id for the qa run must be a valid UUID");
		}
		
		QAResult qar = Get.service(QARunStorage.class).getQAResults(qaKey.get());
		if (qar == null)
		{
			throw new RestException("id", id, "No data was located for the specified qa run");
		}
		return new RestQAResult(qar, limitResults ? 100 : Integer.MAX_VALUE);
	}
	
	/**
	 * Get the current information on all known QA runs, ordered from most recently run to oldest run
	 * @param altId - (optional) the altId type(s) to populate in any returned RestIdentifiedObject structures.  By default, no alternate IDs are 
	 *     returned.  This can be set to one or more names or ids from the <code>/1/id/types</code> or the value 'ANY'.  Requesting IDs that are unneeded will harm 
	 *     performance. 
     * @param largeResults - If false, or unspecified, the individual QA failures will be limited to 100.  
	 *     To include all failures, set this to true.
	 * @param skipResults - If false, or unspecified, has no impact.  If true, largeResults is ignored, the individual QA failures will not be returned. 
	 *     This is useful for just retrieving the metadata for a run.  
	 * @return The details on all of the QA operations that have occurred.
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.runs)
	public RestQAResult[] readAll(@QueryParam(RequestParameters.altId) String altId,
			@QueryParam(RequestParameters.largeResults) @DefaultValue("false") String largeResults, 
			@QueryParam(RequestParameters.skipResults) @DefaultValue("false") String skipResults) throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.altId, 
				RequestParameters.largeResults, RequestParameters.skipResults);
		
		boolean limitResults = !Boolean.parseBoolean(largeResults.trim());
		boolean skipResultsB = Boolean.parseBoolean(skipResults.trim());
		
		List<QAResult> results = Get.service(QARunStorage.class).getQAResults();
		RestQAResult[] toReturn = new RestQAResult[results.size()];
		int resultSize = skipResultsB ? 0 : (limitResults ? 100 : Integer.MAX_VALUE);
		for (int i = 0; i < results.size(); i++)
		{
			toReturn[i] = new RestQAResult(results.get(i), resultSize);
		}
		
		return toReturn;
	}
}