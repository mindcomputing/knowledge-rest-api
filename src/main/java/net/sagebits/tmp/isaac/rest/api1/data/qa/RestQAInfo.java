/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 *
 * You may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributions from 2013-2017 where performed either by US government
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
 * 
 */
package net.sagebits.tmp.isaac.rest.api1.data.qa;

import java.util.Optional;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.logging.log4j.LogManager;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import net.sagebits.tmp.isaac.rest.api1.data.RestIdentifiedObject;
import net.sagebits.tmp.isaac.rest.api1.data.concept.RestConceptChronology;
import net.sagebits.tmp.isaac.rest.api1.data.enumerations.RestSeverity;
import sh.isaac.api.Get;
import sh.isaac.api.qa.QAInfo;
import sh.isaac.utility.Frills;

@XmlRootElement
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
public class RestQAInfo implements Comparable<RestQAInfo>
{
	/**
	 * The severity of the QA Rule message
	 */
	@XmlElement
	public RestSeverity severity;

	/**
	 * The component that triggered the QA rule
	 */
	@XmlElement
	public RestIdentifiedObject component;
	
	/**
	 * The nearest concept to the component that triggered the QA rule (which may be the same as component)
	 */
	@XmlElement
	public RestConceptChronology componentConcept;

	/**
	 * The reason why the rule was triggered
	 */
	@XmlElement
	public String message;

	/**
	 * Additional information about the rule failure - for example, if a description failed a rule, this will be the entire description.
	 * It is up to each QA rule to populate this, some rules may populate it with better info than others...
	 */
	@XmlElement
	public String failureContext = null;
	
	protected RestQAInfo()
	{
		//for jaxb
	}

	public RestQAInfo(QAInfo info)
	{
		this.severity = new RestSeverity(info.getSeverity());
		this.component = new RestIdentifiedObject(info.getComponent());
		this.message = info.getMessage();
		this.failureContext = info.getFailureContext();
		Optional<Integer> nearestConcept = Frills.getNearestConcept(info.getComponent());
		if (nearestConcept.isPresent())
		{
			this.componentConcept = new RestConceptChronology(Get.concept(nearestConcept.get()), false, false, true);
		}
		else
		{
			LogManager.getLogger().error("Missing nearest concept for {}", this.component);
		}
	}

	@Override
	public int compareTo(RestQAInfo o)
	{
		return this.message.compareTo(o.message);
	}
}
