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

import java.util.UUID;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * {@link ReleaseJob}
 *
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a> 
 */

@XmlRootElement
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
public class ReleaseJob implements Comparable<ReleaseJob>
{
	protected ReleaseJob()
	{
		//For jaxb
	}
	
	public ReleaseJob(UUID jobId, String namespace)
	{
		this.id = jobId;
		this.launchTime = System.currentTimeMillis();
		this.status = "Running";
	}
	
	/**
	 * The name of the module where the release was created
	 */
	@XmlElement
	public String releaseName;
	
	/**
	 * The time when this job was started (in standard java form)
	 */
	@XmlElement
	public long launchTime;
	
	/**
	 * The time when this release job was completed (in standard java form).  Null / not provided if it is still running.
	 */	
	@XmlElement
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public Long completeTime;
	
	/**
	 * The ID of this release job run
	 */
	@XmlElement
	public UUID id;
	
	/**
	 * The snomed namespace provided for the run - null if not provided
	 */
	@XmlElement
	public String namespace;
	
	/**
	 * The classifier results of this task, if applicable.  Null if not run
	 */
	@XmlElement
	public UUID classifierId;
	
	/**
	 * number of generated sctids.  Null if generation wasn't run
	 */
	@XmlElement
	public Integer generatedSCTIDs;
	
	/**
	 * The QA results of this task, if applicable
	 */
	@XmlElement
	public UUID qaId;
	
	/**
	 * The promoted concepts
	 */
	@XmlElement
	public int[] promotedConcepts;
	
	/**
	 * The promoted semantics
	 */
	@XmlElement
	public int[] promotedSemantics;
	
	/**
	 * A simple summary of the current status, such as Running, Failed, or Completed.
	 * If failed, this will contain some information on the cause of the failure.
	 */
	@XmlElement
	public String status;
	
	/**
	 * The end-user reportable error, if any
	 */
	@XmlElement
	public String exception;
	
	/**
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(ReleaseJob o)
	{
		return Long.compare(this.launchTime, o.launchTime);
	}
}
