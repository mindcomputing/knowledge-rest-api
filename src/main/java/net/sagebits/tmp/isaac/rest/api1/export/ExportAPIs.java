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

package net.sagebits.tmp.isaac.rest.api1.export;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.StreamingOutput;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.sagebits.tmp.isaac.rest.Util;
import net.sagebits.tmp.isaac.rest.api.exceptions.RestException;
import net.sagebits.tmp.isaac.rest.api1.RestPaths;
import net.sagebits.tmp.isaac.rest.session.RequestInfo;
import net.sagebits.tmp.isaac.rest.session.RequestParameters;
import net.sagebits.uts.auth.data.UserRole.SystemRoleConstants;
import sh.isaac.api.coordinate.StampCoordinate;
import sh.isaac.api.util.UUIDUtil;
import sh.isaac.misc.exporters.VetsExporter;
import sh.isaac.misc.exporters.rf2.RF2Exporter;
import sh.isaac.misc.exporters.rf2.files.RF2ReleaseStatus;
import sh.isaac.misc.exporters.rf2.files.RF2Scope;

/**
 * {@link ExportAPIs}
 * 
 * @author <a href="mailto:daniel.armbrust.list@sagebits.net">Dan Armbrust</a>
 */
@Path(RestPaths.exportAPIsPathComponent)
@RolesAllowed({ SystemRoleConstants.AUTOMATED, SystemRoleConstants.ADMINISTRATOR, SystemRoleConstants.SYSTEM_MANAGER, SystemRoleConstants.CONTENT_MANAGER,
	SystemRoleConstants.EDITOR, SystemRoleConstants.READ })
public class ExportAPIs
{
	private static Logger log = LogManager.getLogger(ExportAPIs.class);
	
	private SimpleDateFormat ymdParser = new SimpleDateFormat("yyyyMMdd");
	private SimpleDateFormat hmsParser = new SimpleDateFormat("HHmmss");
	private SimpleDateFormat hmsWzParser = new SimpleDateFormat("HHmmssZ");
	
	public static final File exportCacheFolder = new File(System.getProperty("java.io.tmpdir"), "UTS-export-cache");

	@Context
	private SecurityContext securityContext;

	/**
	 * This method will stream back an XML file. It may take some time to stream the entire file, depending on the filter criteria.
	 * 
	 * parameters that represent times may be passed in one of two formats. The parameter is sent as a string - if the parameter is parseable
	 * as a numeric long value, then it will be treated as a java date - the number of milliseconds since January 1, 1970, 00:00:00 GMT.
	 * 
	 * If it is not parseable as a numeric long value, then it is parsed as a {@link DateTimeFormatter#ISO_DATE_TIME}
	 * https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/time/format/DateTimeFormatter.html#ISO_DATE_TIME
	 * 
	 * @param changedAfter - optional - if provided, only exports content created or modified on or after this time. If not provided, returns the
	 *            latest version of every component (up to <code>changedBefore</code>) from VHAT.
	 * @param changedBefore - optional - if provided, only exports content created or modified on or before this time. If not provided,
	 *            includes any content created or modified up to now.
	 * @return an VETs schema valid XML file with the VHAT content that meets the date filters.
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_XML })
	@Path(RestPaths.vetsXMLComponent)
	public Response exportVetsXML(@QueryParam(RequestParameters.changedAfter) String changedAfter, 
			@QueryParam(RequestParameters.changedBefore) String changedBefore)
			throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.changedAfter,
				RequestParameters.changedBefore, RequestParameters.COORDINATE_PARAM_NAMES);

		long changedAfterL;
		long changedBeforeL;
		try
		{
			changedAfterL = Util.parseDate(changedAfter);
		}
		catch (DateTimeParseException e)
		{
			throw new RestException("changedAfter", "Could not be parsed as ISO-8601");
		}
		try
		{
			changedBeforeL = Util.parseDate(changedBefore);
		}
		catch (DateTimeParseException e)
		{
			throw new RestException("changedBefore", "Could not be parsed as ISO-8601");
		}
		if (changedAfterL == Long.MAX_VALUE)
		{
			throw new RestException("changedAfter", "Cannot be set to 'latest'");
		}
		if (changedAfterL > System.currentTimeMillis())
		{
			throw new RestException("changedAfter", "Cannot be set to a future time");
		}
		if (changedBeforeL < changedAfterL)
		{
			throw new RestException("changedAfter", "Cannot be set to a time greater than changedBefore");
		}

		log.info("Export VETs XML with the filter " + (changedAfterL > 0 ? "After: " + new Date(changedAfterL).toString() + " " : "")
				+ (changedBeforeL > 0 ? "Before: " + new Date(changedBeforeL).toString() + " " : ""));

		StreamingOutput stream = new StreamingOutput()
		{
			@Override
			public void write(OutputStream output) throws IOException, WebApplicationException
			{
				VetsExporter ve = new VetsExporter();
				ve.export(output, changedAfterL, changedBeforeL, false);
			}
		};
		// In order to make the file download compliant with the needed file download javascript library
		// https://github.com/johnculviner/jquery.fileDownload
		// We must set the cookie fileDownload, this enables the GUI to provide feedback to the user telling them the file download
		// was a success or a failure.
		try
		{
			return Response.ok(stream).header("content-disposition", "attachment; filename = export.xml")
					.cookie(new NewCookie(new Cookie("fileDownload", "true", "/", null))).build();
		}
		catch (Exception e)
		{
			log.warn("Error streaming the XML file back", e);
			// ClassNotFoundException should not happen in a well built system.
			throw new IllegalStateException(e);
		}
	}
	
	/**
	 * This method will stream back a ZIP file that contains a RF2 formatted export.  It may take some time to stream the entire file, 
	 * depending on the filter criteria.  There may be a long pause before the zip file starts streaming, depending on the content requested.
	 * <br>
	 * <br>
	 * The global coordinates, detailed below, are generally used for the selection and reading of the content to be exported.  
	 * <br>
	 * <p>{@code coordToken} specifies an explicit serialized CoordinatesToken string specifying all coordinate parameters.  A CoordinatesToken
	 *            may be obtained by a separate (prior) call to <code>/coordinate/coordinatesToken</code>.  Alternatively, you may specify individual parameters, per below
	 *            (as with most other calls, but re-documented here for clarity, and their need for the creation of proper exports)
	 * <p>{@code time} specifies time component of StampPosition component of the StampCoordinate. Values are Long time values or "latest" (case ignored).
	 *            The default is "latest".  Content modifications after this date will NOT be promoted.  To specify the earliest date of content to promote, 
	 *            and to change how the delta is computed, see <code>changedAfter</code>.
     * <p>{@code modules} specifies modules of the StampCoordinate. Value may be a comma delimited list of module concept UUID or int ids.  Module concepts
	 *     with children are automatically expanded to include the children.
	 * <p>{@code path} specifies path component of StampPosition component of the StampCoordinate. Values is path UUID, int id or the term "development"
	 *            or "master". The default is "development".
	 * 
	 * @param releaseDate - optional - The YYYYMMDD value to use for the RF2 package.  May be reused as <code>versionDate</code>, as well, see below.  
	 *     If not specified, uses today.
	 * @param releaseTime - optional - The HHMMDD value to use for the RF2 package.  Defaults to 120000 if not provided.
	 * @param releaseTimeZone - optional - The timezone value to use for the RF2 package.  Defaults to 'Z' if not provided.
	 * @param versionDate - optional - uses <code>releaseDate</code> if not specified.  The YYYYMMDD value to use for individual files.

	 * @param solorRF2 - optional - if specified as true, then the RF2 will be output in SOLOR format.  If false, or not provided, it will be output 
	 *     in standard RF2.
	 * @param exportType - a comma separated list of values from <code>snapshot</code>, <code>delta</code> or <code>full</code>.  You may provide 1 to 3 entries.  
	 *     The output zip file will contain each requested format.
	 *     <p> <code>snapshot</code> - will include the most recent version of each component accessible on the specified STAMP path.
	 *     <p> <code>full</code> - will include all versions of each component accessible on the specified STAMP path, up to the <code>time</code> specified in the 
	 *         STAMP path.  Leave the <code>time</code> set to latest, for a complete full export.
	 *     <p> <code>delta</code> - will include each version of each component accessible on the specified STAMP path from the <code>changedAfter</code> date up 
	 *       to the <code>time</code> in the specified STAMP path.  Depending on the organization of content, and your use of modules, you may or may not need to 
	 *       make use of <code>changedAfter</code> for the desired calculation of a delta.
	 *
	 * @param changedAfter - optional - only applicable with <code>delta</code> <code>exportType</code>.  If provided, only exports content created or 
	 *     modified on or after this time. 
	 *     <br>If not provided, returns the latest version of every component up to the time in the STAMP, on the provided STAMP path.  The parameter is sent as a string
	 *     <br>if the parameter is parseable as a numeric long value, then it will be treated as a java date - the number of milliseconds since January 1, 1970, 00:00:00 GMT.
	 *     <br>If it is not parseable as a numeric long value, then it is parsed as a {@link DateTimeFormatter#ISO_DATE_TIME}
	 *     https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#ISO_DATE_TIME
	 * @param publish - if true, then in addition to streaming the zip file back to the caller, the content will also be published to the artifact server.
	 * @return a ZIP file that contains the RF2 formatted export.
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_OCTET_STREAM})
	@Path(RestPaths.rf2Component)
	public Response exportRF2(@QueryParam(RequestParameters.releaseDate) String releaseDate, 
			@QueryParam(RequestParameters.releaseTime) String releaseTime, 
			@QueryParam(RequestParameters.releaseTimeZone) String releaseTimeZone, 
			@QueryParam(RequestParameters.versionDate) String versionDate, 
			@QueryParam(RequestParameters.solorRF2)  @DefaultValue("false") String solorRF2, 
			@QueryParam(RequestParameters.exportType) String exportType, 
			@QueryParam(RequestParameters.changedAfter) String changedAfter, 
			@QueryParam(RequestParameters.publish) @DefaultValue("false") String publish)
			throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(),  RequestParameters.releaseDate,  RequestParameters.releaseTime, 
				 RequestParameters.releaseTimeZone,  RequestParameters.versionDate,  RequestParameters.solorRF2,  RequestParameters.exportType, RequestParameters.changedAfter,
				 RequestParameters.publish, RequestParameters.COORDINATE_PARAM_NAMES);

		
		//TODO [1]add params for product, contryNamespace, and releaseStatus
		InputStream is = exportRF2Internal(releaseDate, releaseTime, releaseTimeZone, versionDate, solorRF2, exportType, changedAfter, 
				publish == null ? false : Boolean.parseBoolean(publish.trim()), 
				RequestInfo.get().getStampCoordinate(), "testExtension", "test", RF2ReleaseStatus.ALPHA);
		
		StreamingOutput stream = new StreamingOutput()
		{
			@Override
			public void write(OutputStream output) throws IOException, WebApplicationException
			{
				is.transferTo(output);
				is.close();
			}
		};
		// In order to make the file download compliant with the needed file download javascript library
		// https://github.com/johnculviner/jquery.fileDownload
		// We must set the cookie fileDownload, this enables the GUI to provide feedback to the user telling them the file download
		// was a success or a failure.
		try
		{
			return Response.ok(stream).header("content-disposition", "attachment; filename = export.zip")
					.cookie(new NewCookie(new Cookie("fileDownload", "true", "/", null))).build();
		}
		catch (Exception e)
		{
			log.warn("Error streaming the XML file back", e);
			// ClassNotFoundException should not happen in a well built system.
			throw new IllegalStateException(e);
		}
	}
	
	/**
	 * 
	 * @param releaseDate - allows NOW or KEEP for compatibility with release job reuse
	 * @param releaseTime
	 * @param releaseTimeZone
	 * @param versionDate
	 * @param solorRF2
	 * @param exportType
	 * @param changedAfter
	 * @param publish
	 * @param readCoord
	 * @param product - Camel case short title sufficient to identify the product.  Used in the top folder name.  "FooExtension" for example
	 * @param countryNamespace 2 letter country code, or 7 digit namespace (or a combination of the two) used in the file name
	 * @param releaseStatus 
	 * @return an input stream of the zip file
	 * @throws RestException
	 */
	public InputStream exportRF2Internal(String releaseDate, String releaseTime, String releaseTimeZone, String versionDate, String solorRF2, String exportType, 
			String changedAfter, boolean publish, StampCoordinate readCoord, String product, String countryNamespace, RF2ReleaseStatus releaseStatus) throws RestException
	{
		long changedAfterL;
		final String releaseDateFormatted;
		try
		{
			changedAfterL = Util.parseDate(changedAfter);
		}
		catch (DateTimeParseException e)
		{
			throw new RestException("changedAfter", "Could not be parsed as ISO-8601");
		}
		if (changedAfterL == Long.MAX_VALUE)
		{
			throw new RestException("changedAfter", "Cannot be set to 'latest'");
		}
		if (changedAfterL > System.currentTimeMillis())
		{
			throw new RestException("changedAfter", "Cannot be set to a future time");
		}
		if (readCoord.getStampPosition().getTime() < changedAfterL)
		{
			throw new RestException("changedAfter", "Cannot be set to a time greater than the time in the stamp coordinate");
		}
		
		if (StringUtils.isNotBlank(releaseDate) && !releaseDate.equalsIgnoreCase("NOW") && !releaseDate.equalsIgnoreCase("KEEP"))
		{
			try 
			{
				releaseDateFormatted = ymdParser.format(ymdParser.parse(releaseDate));
			}
			catch (Exception e)
			{
				throw new RestException("releaseDate", "Could not be parsed.  It must be formatted as YYYYMMDD");
			}
		}
		else
		{
			releaseDateFormatted = ymdParser.format(new Date());
		}
		
		if (StringUtils.isNotBlank(releaseTime))
		{
			try 
			{
				hmsParser.parse(releaseTime);
			}
			catch (Exception e)
			{
				throw new RestException("releaseTime", "Could not be parsed.  It must be formatted as HHMMSS");
			}
		}
		
		if (StringUtils.isNotBlank(releaseTimeZone))
		{
			try 
			{
				hmsWzParser.parse((StringUtils.isBlank(releaseTime) ? "120000" : releaseTime) + releaseTimeZone);
			}
			catch (Exception e)
			{
				throw new RestException("releaseTimeZone", "Could not be parsed.  It must be formatted as Z or as a proper RFC 822 value, such as +0200");
			}
		}
		
		if (StringUtils.isNotBlank(versionDate))
		{
			try 
			{
				hmsParser.parse(versionDate);
			}
			catch (Exception e)
			{
				throw new RestException("versionDate", "Could not be parsed.  It must be formatted as HHMMSS if provided");
			}
		}

		boolean solorFormat = false;
		
		if (StringUtils.isNotBlank(solorRF2))
		{
			solorFormat = Boolean.parseBoolean(solorRF2);
		}
		
		if (solorFormat)
		{
			//TODO [1] implement solor format
			throw new RestException("Solor format not yet supported");
		}
		
		boolean makeSnapshot = false;
		boolean makeDelta = false;
		boolean makeFull = false;
		
		if (StringUtils.isBlank(exportType))
		{
			throw new RestException("exportType", "One or more export types is required for the creation of the RF2 files");
		}
		if (exportType.length() == 0)
		{
			throw new RestException("exportType", "One or more export types is required for the creation of the RF2 files");
		}
		String[] exportTypes = exportType.split(",");
		
		for (String et : exportTypes)
		{
			if (et.trim().toLowerCase().equals("snapshot"))
			{
				makeSnapshot = true;
			}
			else if (et.trim().toLowerCase().equals("delta"))
			{
				makeDelta = true;
			}
			else if (et.trim().toLowerCase().equals("full"))
			{
				makeFull = true;
			}
			else
			{
				throw new RestException("exportType", "export type must be snapshot, delta or full");
			}
		}

		log.info("Export RF2 begins");
		try
		{
			RF2Exporter exporter = new RF2Exporter(readCoord, 
					makeFull, makeSnapshot, makeDelta, 
					Optional.of(changedAfterL), 
					product, 
					Optional.of(RF2Scope.Extension), 
					releaseStatus, 
					countryNamespace, 
					releaseDateFormatted, 
					StringUtils.isNotBlank(releaseTime) ? Optional.of(releaseTime) : Optional.empty(), 
					StringUtils.isNotBlank(releaseTimeZone) ? Optional.of(releaseTimeZone) : Optional.empty(),
					StringUtils.isNotBlank(versionDate) ? Optional.of(versionDate) : Optional.empty());
			
			//No reason to actually run this in a background thread, we are likely already threaded.
			File zipFile = exporter.call();
			
			if (publish)
			{
				//TODO [1] implement artifact publish
			}
			
			InputStream is = new FileInputStream(zipFile);
			return is;
		}
		catch (Exception e)
		{
			log.error("Unexpected internal error during rf2 export", e);
			throw new RestException("Unexpected internal error");
		}

	}

	/**
	 * This method will stream back an release file, if it is available to the server.  If artifactId/groupId/version is provided, the 
	 * file will be pulled from the artifact server, if available.  Alternatively, provide an id from write/release/releaseJob
	 * 
	 * @param artifactId optional - the artifactID to get from the artifact server.  Requires <code>groupId</code> and <code>version</code>.
	 * @param groupId optional - the groupID to get from the artifact server.  Requires <code>artifactId and <code>version</code>.
	 * @param version optional - the version to get from the artifact server.  Requires <code>artifactId and <code>groupId</code>.
	 * @param id - optional - the UUID identifier of a batch release file to download.  Provided by <code>write/release/releaseJob</code>
	 * 
	 * @return the requested file.
	 * @throws RestException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_OCTET_STREAM })
	@Path(RestPaths.artifactComponent)
	public Response downloadExisting(@QueryParam(RequestParameters.artifactId) String artifactId, 
			@QueryParam(RequestParameters.groupId) String groupId,
			@QueryParam(RequestParameters.version) String version, 
			@QueryParam(RequestParameters.id) String id)
			throws RestException
	{
		RequestParameters.validateParameterNamesAgainstSupportedNames(RequestInfo.get().getParameters(), RequestParameters.artifactId, RequestParameters.groupId,
				RequestParameters.version, RequestParameters.id, RequestParameters.COORDINATE_PARAM_NAMES);
		
		if (StringUtils.isNotBlank(artifactId))
		{
			//TODO [1] implement artifact download
			throw new RestException("Artifact download not yet supported");
		}
		
		Optional<UUID> idParsed = UUIDUtil.getUUID(id);
		if (idParsed.isPresent())
		{
			exportCacheFolder.mkdirs();
			File tempZipFile = new File(exportCacheFolder, idParsed.get().toString() + ".zip");
			if (tempZipFile.isFile())
			{
				StreamingOutput stream = new StreamingOutput()
				{
					@Override
					public void write(OutputStream output) throws IOException, WebApplicationException
					{
						FileInputStream fis = new FileInputStream(tempZipFile);
						fis.transferTo(output);
						fis.close();
					}
				};
				
				return Response.ok(stream).header("content-disposition", "attachment; filename = " + idParsed.get().toString().toString() + ".zip")
						.cookie(new NewCookie(new Cookie("fileDownload", "true", "/", null))).build();
			}
			else
			{
				throw new RestException("The file is no longer available");
			}
		}
		else
		{
			throw new RestException("Must provide an artifact or id identifier");
		}
	}
	
	//TODO [1] make this take into account if we can do an artifact lookup for it
	public boolean cachedFileExists(UUID id)
	{
		exportCacheFolder.mkdirs();
		File tempZipFile = new File(exportCacheFolder, id.toString() + ".zip");
		if (tempZipFile.isFile())
		{
			return true;
		}
		return false;
	}
}
