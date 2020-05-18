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

package net.sagebits.tmp.isaac.rest.api1.data.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.logging.log4j.LogManager;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import net.sagebits.tmp.isaac.rest.Util;
import net.sagebits.tmp.isaac.rest.api.exceptions.RestException;
import net.sagebits.tmp.isaac.rest.api1.data.RestIdentifiedObject;
import net.sagebits.tmp.isaac.rest.api1.data.semantic.dataTypes.RestDynamicSemanticNid;
import net.sagebits.tmp.isaac.rest.session.RequestInfo;
import sh.isaac.MetaData;
import sh.isaac.api.Get;
import sh.isaac.api.Status;
import sh.isaac.api.component.semantic.version.DescriptionVersion;
import sh.isaac.api.constants.DynamicConstants;
import sh.isaac.api.coordinate.StampPrecedence;
import sh.isaac.api.externalizable.IsaacObjectType;
import sh.isaac.api.util.AlphanumComparator;
import sh.isaac.model.coordinate.ManifoldCoordinateImpl;
import sh.isaac.model.coordinate.StampCoordinateImpl;
import sh.isaac.model.coordinate.StampPositionImpl;
import sh.isaac.utility.Frills;

/**
 * 
 * {@link RestSemanticDescriptionVersion}
 *
 * @author <a href="mailto:daniel.armbrust.list@sagebits.net">Dan Armbrust</a>
 */
@XmlRootElement
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
public class RestSemanticDescriptionVersion extends RestSemanticVersion implements Comparable<RestSemanticDescriptionVersion>
{
	/**
	 * The concept that represents the case significance flag on the description .
	 * This should be description case sensitive, description not case sensitive or description initial character sensitive
	 */
	@XmlElement
	public RestIdentifiedObject caseSignificanceConcept;

	/**
	 * The concept that represents the language of the description (note, this is NOT the dialect)
	 */
	@XmlElement
	public RestIdentifiedObject languageConcept;

	/**
	 * The text of the description
	 */
	@XmlElement
	public String text;

	/**
	 * The concept that represents the type of the description.
	 * This should be FQN, Regular Name, or Definition.
	 */
	@XmlElement
	public RestIdentifiedObject descriptionTypeConcept;

	/**
	 * The optional concept that represents the extended type of the description.
	 * This should be a child of {@link MetaData#EXTENDED_DESCRIPTION_TYPE____SOLOR}.
	 */
	@XmlElement
	public RestIdentifiedObject descriptionExtendedTypeConcept;

	/**
	 * The dialects attached to this semantic. Not populated by default, include expand=nestedSemantics to expand this.
	 */
	@XmlElement
	public List<RestDynamicSemanticVersion> dialects = new ArrayList<>();

	protected RestSemanticDescriptionVersion()
	{
		// for Jaxb
	}

	public RestSemanticDescriptionVersion(DescriptionVersion dsv, boolean includeChronology, boolean expandNested, boolean expandReferenced, 
			boolean useLatestStamp) throws RestException
	{
		super();
		setup(dsv, includeChronology, expandNested, expandReferenced, useLatestStamp, ((restSemanticVersion, stampCoord) -> {
			// If the assemblage is a dialect, put it in our list.
			if (Get.taxonomyService().getSnapshotNoTree(new ManifoldCoordinateImpl(
					new StampCoordinateImpl(StampPrecedence.TIME, 
							new StampPositionImpl(Long.MAX_VALUE, stampCoord.getStampPosition().getStampPathSpecification().getNid()), 
							null, Status.ACTIVE_ONLY_SET), 
						Get.configurationService().getGlobalDatastoreConfiguration().getDefaultLanguageCoordinate()))  //language doesn't matter for our request
					.isKindOf(restSemanticVersion.semanticChronology.assemblage.nid, MetaData.DIALECT_ASSEMBLAGE____SOLOR.getNid()))
			{
				dialects.add((RestDynamicSemanticVersion) restSemanticVersion);
				return false;
			}
			// if the assemblage is extendedDescriptionType, skip - we handle below
			if (restSemanticVersion.semanticChronology.assemblage.nid == DynamicConstants.get().DYNAMIC_EXTENDED_DESCRIPTION_TYPE.getNid())
			{
				return false;
			}
			return true;
		}));
		caseSignificanceConcept = new RestIdentifiedObject(dsv.getCaseSignificanceConceptNid(), IsaacObjectType.CONCEPT);
		languageConcept = new RestIdentifiedObject(dsv.getLanguageConceptNid(), IsaacObjectType.CONCEPT);
		text = dsv.getText();
		descriptionTypeConcept = new RestIdentifiedObject(dsv.getDescriptionTypeConceptNid(), IsaacObjectType.CONCEPT);

		// populate descriptionExtendedTypeConceptNid
		Optional<UUID> descriptionExtendedTypeOptional = Frills.getDescriptionExtendedTypeConcept(RequestInfo.get().getStampCoordinate(), dsv.getNid(), false);
		if (descriptionExtendedTypeOptional.isPresent())
		{
			descriptionExtendedTypeConcept = new RestIdentifiedObject(descriptionExtendedTypeOptional.get());
		}
		
		sortDialects();
	}

	/**
	 * Sorts preferred before acceptable, and orders dialects by us-en, us-gb, then alphabetical
	 */
	private void sortDialects()
	{
		if (dialects.size() > 1)
		{
			Collections.sort(dialects, new Comparator<RestDynamicSemanticVersion>()
			{
				@Override
				public int compare(RestDynamicSemanticVersion o1, RestDynamicSemanticVersion o2)
				{
					//This is a semantic with a single nid column, which represents preferred or acceptable.  
					//The assemblage concept will be something like "US English Dialect"
					
					//If preferred / acceptable is the same, sort on the dialects...
					if (((RestDynamicSemanticNid)o1.dataColumns.get(0)).getNid() == ((RestDynamicSemanticNid)o2.dataColumns.get(0)).getNid())
					{
						if (o1.semanticChronology != null)  //If one chronology is here, they both should be here
						{
							if (o1.semanticChronology.assemblage.nid.intValue() == MetaData.US_ENGLISH_DIALECT____SOLOR.getNid())
							{
								return -1;
							}
							else if (o2.semanticChronology.assemblage.nid.intValue() == MetaData.US_ENGLISH_DIALECT____SOLOR.getNid())
							{
								return 1;
							}
							else if (o1.semanticChronology.assemblage.nid.intValue() == MetaData.GB_ENGLISH_DIALECT____SOLOR.getNid())
							{
								return -1;
							}
							else if (o2.semanticChronology.assemblage.nid.intValue() == MetaData.GB_ENGLISH_DIALECT____SOLOR.getNid())
							{
								return 1;
							}
							else
							{
								//Some other dialect... just sort on the dialect text
								return AlphanumComparator.compare(Util.readBestDescription(o1.semanticChronology.assemblage.nid),
										Util.readBestDescription(o2.semanticChronology.assemblage.nid),  true);
							}
						}
						else
						{
							//If chronology isn't populated, I can't sort here
							return 0;
						}
					}
					else if (((RestDynamicSemanticNid)o1.dataColumns.get(0)).getNid() == MetaData.PREFERRED____SOLOR.getNid())
					{
						return -1;
					}
					else if (((RestDynamicSemanticNid)o2.dataColumns.get(0)).getNid() == MetaData.PREFERRED____SOLOR.getNid())
					{
						return 1;
					}
					else
					{
						//should be impossible - not the same, and neither is preferred - must be invalid data.
						LogManager.getLogger().warn("Unexpected sort case");
						return 0;
					}
				}
			});
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString()
	{
		return "RestSemanticDescriptionVersion [" + "caseSignificanceConceptNid=" + caseSignificanceConcept + ", languageConceptNid=" + languageConcept
				+ ", text=" + text + ", descriptionTypeConceptNid=" + descriptionTypeConcept + ", descriptionExtendedTypeConceptNid="
				+ descriptionExtendedTypeConcept + ", dialects=" + dialects + ", expandables=" + expandables + ", semanticChronology=" + semanticChronology
				+ ", semanticVersion=" + semanticVersion + ", nestedSemantics=" + nestedSemantics + "]";
	}

	/**
	 * Sort descriptions associated with the concept, sorted via many levels:
	 * level 1 - by core types first (FSN, Regular Name, Definition) or - if not a core type, grouped by associated core type, and then 
	 *     alphabetical by the description type within the group (fsn, regular name, definition).
	 * level 2 - by language - EN first, and then alphabetical by the language after this
	 * level 3 - If it has a dialect marking preferred, this comes before any descriptions with only acceptable dialect markings
	 * level 4 - alphabetical by the text of the description
	 */
	@Override
	public int compareTo(RestSemanticDescriptionVersion o)
	{
		//This will handle native types, and the grouping of external types.
		int coreTypeLeft = Frills.getDescriptionType(this.descriptionTypeConcept.nid.intValue(), null);
		int coreTypeRight = Frills.getDescriptionType(o.descriptionTypeConcept.nid.intValue(), null);
		
		//handle cases where 1 of the 2 is a core type
		if (coreTypeLeft == MetaData.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE____SOLOR.getNid() 
				|| coreTypeRight == MetaData.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE____SOLOR.getNid())
		{
			if (coreTypeLeft == MetaData.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE____SOLOR.getNid() 
					&& coreTypeRight != MetaData.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE____SOLOR.getNid())
			{
				return -1;
			}
			else if (coreTypeLeft != MetaData.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE____SOLOR.getNid() 
					&& coreTypeRight == MetaData.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE____SOLOR.getNid())
			{
				return 1;
			}
		}
		
		else if (coreTypeLeft == MetaData.REGULAR_NAME_DESCRIPTION_TYPE____SOLOR.getNid() 
				|| coreTypeRight == MetaData.REGULAR_NAME_DESCRIPTION_TYPE____SOLOR.getNid())
		{
			if (coreTypeLeft == MetaData.REGULAR_NAME_DESCRIPTION_TYPE____SOLOR.getNid() 
					&& coreTypeRight != MetaData.REGULAR_NAME_DESCRIPTION_TYPE____SOLOR.getNid())
			{
				return -1;
			}
			else if (coreTypeLeft != MetaData.REGULAR_NAME_DESCRIPTION_TYPE____SOLOR.getNid() 
					&& coreTypeRight == MetaData.REGULAR_NAME_DESCRIPTION_TYPE____SOLOR.getNid())
			{
				return 1;
			}
		}
		
		else if (coreTypeLeft == MetaData.DEFINITION_DESCRIPTION_TYPE____SOLOR.getNid() 
				|| coreTypeRight == MetaData.DEFINITION_DESCRIPTION_TYPE____SOLOR.getNid())
		{
			if (coreTypeLeft == MetaData.DEFINITION_DESCRIPTION_TYPE____SOLOR.getNid() 
					&& coreTypeRight != MetaData.DEFINITION_DESCRIPTION_TYPE____SOLOR.getNid())
			{
				return -1;
			}
			else if (coreTypeLeft != MetaData.DEFINITION_DESCRIPTION_TYPE____SOLOR.getNid() 
					&& coreTypeRight == MetaData.DEFINITION_DESCRIPTION_TYPE____SOLOR.getNid())
			{
				return 1;
			}
		}
		
		//the core types are identical.  If there is an external type - check that.
		if (this.descriptionExtendedTypeConcept == null || o.descriptionExtendedTypeConcept == null)
		{
			//external (not extended) type which is different
			if (this.descriptionTypeConcept.nid.intValue() != o.descriptionTypeConcept.nid.intValue())
			{
				return AlphanumComparator.compare(Util.readBestDescription(this.descriptionTypeConcept.nid.intValue()), 
						Util.readBestDescription(o.descriptionTypeConcept.nid.intValue()), true);
			}
		}
		else
		{
			//extended type, where the types are different
			if (this.descriptionExtendedTypeConcept.nid.intValue() != o.descriptionExtendedTypeConcept.nid.intValue())
			{
				return AlphanumComparator.compare(Util.readBestDescription(this.descriptionExtendedTypeConcept.nid.intValue()), 
						Util.readBestDescription(o.descriptionExtendedTypeConcept.nid.intValue()), true);
			}
		}
		//Still tied on type, move to level 2
		return sortLanguage(o);
	}

	private int sortLanguage(RestSemanticDescriptionVersion o)
	{
		if (this.languageConcept.nid.intValue() != o.languageConcept.nid.intValue())
		{
			if (this.languageConcept.nid.intValue() == MetaData.ENGLISH_LANGUAGE____SOLOR.getNid() || o.languageConcept.nid.intValue() == MetaData.ENGLISH_LANGUAGE____SOLOR.getNid())
			{
				if (this.languageConcept.nid.intValue() == MetaData.ENGLISH_LANGUAGE____SOLOR.getNid())
				{
					return -1;
				}
				else
				{
					return 1;
				}
			}
			else
			{
				return AlphanumComparator.compare(Util.readBestDescription(this.languageConcept.nid.intValue()), Util.readBestDescription(o.languageConcept.nid.intValue()), true);
			}
		}
		else
		{
			//still tied, level to 3.
			return sortDialect(o);
		}
	}

	private int sortDialect(RestSemanticDescriptionVersion o)
	{
		boolean left = hasPreferredDialect(this);
		boolean right = hasPreferredDialect(o);
		
		if ((left && right) || !left && !right)
		{
			//still tied - level 4
			return AlphanumComparator.compare(this.text, o.text, true);
		}
		else if (left && !right)
		{
			return -1;
		}
		else // (!left && right)
		{
			return 1;
		}
	}
	
	private boolean hasPreferredDialect(RestSemanticDescriptionVersion o)
	{
		if (o.dialects == null)
		{
			return false;
		}
		
		for (RestDynamicSemanticVersion dialect : o.dialects)
		{
			if (((RestDynamicSemanticNid)dialect.dataColumns.get(0)).getNid() == MetaData.PREFERRED____SOLOR.getNid())
			{
				return true;
			}
		}
		return false;
	}
}
