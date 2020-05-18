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
package net.sagebits.tmp.isaac.rest.api1.release;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;
import org.apache.commons.lang3.StringUtils;
import net.sagebits.tmp.isaac.rest.api.exceptions.RestException;
import net.sagebits.tmp.isaac.rest.api1.RestPaths;
import net.sagebits.tmp.isaac.rest.api1.data.release.ReleaseJob;
import net.sagebits.tmp.isaac.rest.api1.data.release.ReleaseJobStorage;
import net.sagebits.tmp.isaac.rest.api1.data.release.RestReleaseJobResult;
import net.sagebits.tmp.isaac.rest.api1.export.ExportAPIs;
import net.sagebits.tmp.isaac.rest.session.RequestInfo;
import net.sagebits.tmp.isaac.rest.session.RequestParameters;
import net.sagebits.uts.auth.data.UserRole.SystemRoleConstants;
import sh.isaac.api.util.UUIDUtil;

/**
 * {@link ReleaseReadAPIs}
 *
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a>
 */
@Path(RestPaths.releaseAPIsPathComponent)
@RolesAllowed({ SystemRoleConstants.AUTOMATED, SystemRoleConstants.ADMINISTRATOR, SystemRoleConstants.SYSTEM_MANAGER, SystemRoleConstants.CONTENT_MANAGER,
		SystemRoleConstants.EDITOR, SystemRoleConstants.READ })
public class ReleaseReadAPIs
{
	@Context
	private SecurityContext securityContext;

	@Context 
	private ResourceContext resourceContext;
	
	/**
	 * Get the current information on the specified release job run
	 * @param id - The ID of a release run.  These UUIDs are returned via the call that launches the release.
	 * @return The details on the release run.
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.run + "{" + RequestParameters.id + "}")
	public RestReleaseJobResult read(@PathParam(RequestParameters.id) String id) throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.id);
		
		if (StringUtils.isBlank(id))
		{
			throw new RestException("id", "The id for the release job run is required");
		}
		
		Optional<UUID> jobKey = UUIDUtil.getUUID(id.trim());
		if (!jobKey.isPresent())
		{
			throw new RestException("id", id, "The id for the release job run must be a valid UUID");
		}
		
		ReleaseJob rrjr = ReleaseJobStorage.getReleaseJobResults(jobKey.get());
		if (rrjr == null)
		{
			throw new RestException("id", id, "No data was located for the specified release job run");
		}
		ExportAPIs exportAPI = resourceContext.getResource(ExportAPIs.class);
		
		return new RestReleaseJobResult(rrjr, exportAPI.cachedFileExists(jobKey.get()));
	}
	
	/**
	 * Get the current information on all known release job runs, ordered from most recently run to oldest run
	 * @return The details on all of the release job operations that have occurred.
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(RestPaths.runs)
	public RestReleaseJobResult[] readAll() throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters());
		
		ExportAPIs exportAPI = resourceContext.getResource(ExportAPIs.class);
		
		ArrayList<RestReleaseJobResult> results = new ArrayList<>();
		for (ReleaseJob rj : ReleaseJobStorage.getReleaseJobResults())
		{
			results.add(new RestReleaseJobResult(rj, exportAPI.cachedFileExists(rj.id)));
		}
		return results.toArray(new RestReleaseJobResult[results.size()]);
	}
}