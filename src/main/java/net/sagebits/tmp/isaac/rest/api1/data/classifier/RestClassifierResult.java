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
package net.sagebits.tmp.isaac.rest.api1.data.classifier;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import net.sagebits.tmp.isaac.rest.api1.data.RestStampedVersion;
import net.sagebits.tmp.isaac.rest.api1.data.concept.RestConceptChronology;
import net.sagebits.tmp.isaac.rest.api1.data.coordinate.RestLogicCoordinate;
import net.sagebits.tmp.isaac.rest.api1.data.coordinate.RestStampCoordinate;
import sh.isaac.api.Get;

/**
 * {@link RestClassifierResult}
 *
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a> 
 */

@XmlRootElement
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
public class RestClassifierResult
{
	/**
	 * The time when this classification was started (in standard java form)
	 */
	@XmlElement
	private long launchTime;
	
	/**
	 * The time when this classification was completed (in standard java form).  Null / not provided if it is still running.
	 */	
	@XmlElement
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Long completeTime;
	
	/**
	 * The concepts with logic graphs changed by this classification.  Only provided upon successful completion.  Limited to 100 results by default, 
	 * unless the parameter largeResults=true is passed.  
	 */
	@XmlElement
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private RestConceptChronology[] affectedConcepts;
	
	/**
	 * The count of concepts affected by this classification.  Only provided upon successful completion.
	 * This value is computed from affectedConcepts prior to trimming.
	 */
	@XmlElement
	private int affectedConceptCount = 0;
	
	/**
	 * The equivalent sets identified by the classifier.  Only provided upon successful completion.  Limited to 100 results by default, 
	 * unless the parameter largeResults=true is passed.  
	 */
	@XmlElement
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private RestClassifierEquivalentSet[] equivalentSets;
	
	/**
	 * The count of equivalent sets identified by the classifier.
	 * This value is computed from equivalentSets prior to trimming.  
	 */
	@XmlElement
	private int equivalentSetCount = 0;
	
	/**
	 * The cycles identified by the classification process.   Limited to 100 results by default, unless the parameter largeResults=true is passed.
	 */
	@XmlElement
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private RestClassifierCycle[] cycles;
	
	/**
	 * The count of cycles identified by the classification process.
	 * This value is computed from cycles prior to trimming.  
	 */
	@XmlElement
	private int cycleCount = 0;
	
	/**
	 * Orphaned concepts identified by the classification process.   Limited to 100 results by default, unless the parameter largeResults=true is passed.
	 */
	@XmlElement
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private RestConceptChronology[] orphanedConcepts;
	
	/**
	 * Count of orphaned concepts identified by the classification process. 
	 * This value is computed from orphanedConcepts prior to trimming.  
	 * 
	 */
	@XmlElement
	private int orphanedConceptCount = 0;
	
	/**
	 * The count of concepts evaluated during the classification.  The initial run of the classifier after the start of the system
	 * will result in all concepts being evaluated.  Subsequent classifications will only evaluate modified concepts, so long as the 
	 * classifier status cache was maintained.  This number is only a measure of how much work the classifier did.  Only provided upon 
	 * successful completion.
	 */
	@XmlElement
	private int processedConceptCount = 0;

	
	/**
	 * The ID of this classification run
	 */
	@XmlElement
	private String classificationId;
	
	/**
	 * A simple summary of the current status, such as Running, Failed, or Completed.
	 * If failed, this will contain some information on the cause of the failure.
	 */
	@XmlElement
	private String status;
	
	/**
	 * The stamp coordinate used when this classification was executed
	 */
	@XmlElement
	protected RestStampCoordinate stampCoordinate;
	
	/**
	 * The logic coordinate used when this classification was executed
	 */
	@XmlElement
	protected RestLogicCoordinate logicCoordinate;
	
	/**
	 * The stamp version info where the classifier results were written
	 */
	@XmlElement
	protected RestStampedVersion writeStamp;
	
	protected RestClassifierResult()
	{
		//For jaxb
	}
	
	public RestClassifierResult(ClassifierResult cr, boolean limitResult, boolean skipResults)
	{
		this.classificationId = cr.classificationId;
		this.launchTime = cr.launchTime;
		this.status = cr.status;
		this.completeTime = cr.completeTime;
		this.processedConceptCount = cr.processedConceptCount;
		if (cr.affectedConcepts != null && cr.affectedConcepts.length > 0)
		{
			this.affectedConceptCount = cr.affectedConcepts.length;
			if (!skipResults)
			{
				this.affectedConcepts = new RestConceptChronology[limitResult ? Math.min(100, cr.affectedConcepts.length) : cr.affectedConcepts.length];
				
				int i = 0;
				for (int nid : cr.affectedConcepts)
				{
					this.affectedConcepts[i++] = new RestConceptChronology(Get.concept(nid), false, false, true);
					if (limitResult && i == 100)
					{
						break;
					}
				}
			}
		}
		
		if (cr.equivalentSets != null && cr.equivalentSets.size() > 0)
		{
			this.equivalentSetCount = cr.equivalentSets.size();
			
			if (!skipResults)
			{
				this.equivalentSets = new RestClassifierEquivalentSet[(limitResult ? Math.min(100, cr.equivalentSets.size()) : cr.equivalentSets.size())];
				int i = 0;
				for (int[] ial : cr.equivalentSets)
				{
					List<RestConceptChronology> set = new ArrayList<>(ial.length);
					for (int nid : ial)
					{
						set.add(new RestConceptChronology(Get.concept(nid), false, false, true));
					}
					
					this.equivalentSets[i] = new RestClassifierEquivalentSet(set);
					
					if (limitResult && ++i == 100)
					{
						break;
					}
				}
			}
		}
		
		if (cr.cycles != null && cr.cycles.size() > 0)
		{
			cycleCount = cr.cycles.size();
			if (!skipResults)
			{
				cycles = new RestClassifierCycle[(limitResult ? Math.min(100, cr.cycles.size()) : cr.cycles.size())];
				
				int i = 0;
				for (ClassifierCycle cycle : cr.cycles)
				{
					cycles[i] = new RestClassifierCycle(cycle);
					if (limitResult && ++i == 100)
					{
						break;
					}
				}
			}
		}
		
		if (cycleCount > 0)
		{
			status = "Failed due to cycles";
		}
		
		if (cr.orphanedConcepts != null && cr.orphanedConcepts.length > 0)
		{
			this.orphanedConceptCount = cr.orphanedConcepts.length;
			if (!skipResults)
			{
				this.orphanedConcepts = new RestConceptChronology[limitResult ? Math.min(100, cr.orphanedConcepts.length) : cr.orphanedConcepts.length];
				int j = 0;
				for (int orphan : cr.orphanedConcepts)
				{
					orphanedConcepts[j++] = new RestConceptChronology(Get.concept(orphan), false, false, true);
					if (limitResult && j == 100)
					{
						break;
					}
				}
			}
		}
		
		this.stampCoordinate = cr.stampCoordinate;
		this.logicCoordinate = cr.logicCoordinate;
		if (cr.writeStamp != 0)
		{
			this.writeStamp = new RestStampedVersion(Get.stampService().getStamp(cr.writeStamp));
		}
	}
}
