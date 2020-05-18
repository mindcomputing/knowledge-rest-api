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
package net.sagebits.tmp.isaac.rest.api1.data.qa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import sh.isaac.provider.qa.QAResult;

/**
 * {@link RestQAResult}
 *
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a> 
 */

@XmlRootElement
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
public class RestQAResult
{
	/**
	 * The time when this classification was started (in standard java form)
	 */
	@XmlElement
	public long launchTime;
	
	/**
	 * The time when this qa run was completed (in standard java form).  Null / not provided if it is still running.
	 */	
	@XmlElement
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public Long completeTime;
	
	/**
	 * The ID of this QA run
	 */
	@XmlElement
	public String qaId;

	
	/**
	 * A simple summary of the current status, such as Running, Failed, or Completed.
	 * If failed, this will contain some information on the cause of the failure.
	 */
	@XmlElement
	public String status;
	
	
	/**
	 * The individual QA Failure count
	 */	
	@XmlElement
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public int qaFailureCount;
	
	/**
	 * The individual QA failures - sorted by the message text
	 */
	@XmlElement
	public List<RestQAInfo> qaFailures;
	
	
	protected RestQAResult()
	{
		//For jaxb
	}

	/**
	 * @param result
	 * @param maxResult pass 0 to skip results, int max value for no limit
	 */
	public RestQAResult(QAResult result, int maxResult)
	{
		this.launchTime = result.getLaunchTime();
		this.completeTime = result.getCompleteTime();
		this.qaId = result.getQaId().toString();
		this.status = result.getStatus();
		this.qaFailureCount = result.getResult() == null ? 0 : result.getResult().length;
		this.qaFailures = new ArrayList<>(result.getResult() == null ? 0 : Math.min(maxResult, result.getResult().length));
		if (result.getResult() != null)
		{
			for (int i = 0; i < result.getResult().length; i++)
			{
				if (qaFailures.size() == maxResult)
				{
					break;
				}
				this.qaFailures.add(new RestQAInfo(result.getResult()[i]));
			}
		}
		
		Collections.sort(qaFailures);
	}
}
