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
 */
package net.sagebits.tmp.isaac.rest.api1.data.release;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * {@link RestReleaseJobResult}
 *
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a> 
 */

@XmlRootElement
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
public class RestReleaseJobResult
{
	
	protected RestReleaseJobResult()
	{
		//For jaxb
	}
	public RestReleaseJobResult(ReleaseJob rj, boolean releaseFileExists)
	{
		this.releaseName = rj.releaseName;
		this.launchTime = rj.launchTime;
		this.completeTime = rj.completeTime;
		this.id = rj.id.toString();
		this.classifierRunId = rj.classifierId == null ? null : rj.classifierId.toString();
		this.qaRunId = rj.qaId == null ? null : rj.qaId.toString();
		this.namespace = rj.namespace;
		this.generatedSCTIDCount = rj.generatedSCTIDs;
		this.promotedConceptCount = rj.promotedConcepts == null ? 0 : rj.promotedConcepts.length;
		this.promotedSemanticCount = rj.promotedSemantics == null ? 0 : rj.promotedSemantics.length;
		this.status = rj.status;
		this.releaseFileAvailable = releaseFileExists;
		this.exception = rj.exception;
	}
	
	/**
	 * The name of the module where the release was created
	 */
	@XmlElement
	public String releaseName;
	
	/**
	 * The time when this classification was started (in standard java form)
	 */
	@XmlElement
	private long launchTime;
	
	/**
	 * The time when this release job was completed (in standard java form).  Null / not provided if it is still running.
	 */	
	@XmlElement
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Long completeTime;
	
	/**
	 * The ID of this release job run
	 */
	@XmlElement
	private String id;
	
	/**
	 * The classifier results of this task, if applicable
	 */
	@XmlElement
	private String classifierRunId;
	
	/**
	 * The QA results of this task, if applicable
	 */
	@XmlElement
	private String qaRunId;
	
	/**
	 * The namespace provided for SCTID generation, if any
	 */
	@XmlElement
	private String namespace;
	
	/**
	 * The output of the SCTID generation, if applicable.  Null if not run
	 */
	@XmlElement
	private Integer generatedSCTIDCount;
	
	/**
	 * The promoted concepts
	 */
	@XmlElement
	public int promotedConceptCount;
	
	/**
	 * The promoted semantics
	 */
	@XmlElement
	public int promotedSemanticCount;
	
	/**
	 * true, if the resulting file from this release job can be downloaded via /export/artifact/{id}, false if the file is not available
	 */
	@XmlElement
	public boolean releaseFileAvailable;
	
	/**
	 * If the job failed with an exception, these are the details
	 */
	@XmlElement
	public String exception;
	
	/**
	 * A simple summary of the current status, such as Running, Failed, or Completed.
	 * If failed, this will contain some information on the cause of the failure.
	 */
	@XmlElement
	private String status;
}
