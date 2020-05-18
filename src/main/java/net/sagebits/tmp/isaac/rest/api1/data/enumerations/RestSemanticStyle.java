/**
 * Copyright Notice
 *
 * This is a work of the U.S. Government and is not subject to copyright
 * protection in the United PremiseTypes. Foreign copyrights may apply.
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

package net.sagebits.tmp.isaac.rest.api1.data.enumerations;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * 
 * {@link RestSemanticStyle}
 * 
 * {@link SemanticStyle#PROPERTY} Generic semantic assemblage which contains data columns, and isn't the more specific mapset or association styles.  Available 
 *   with the semantic APIS.
 * {@link SemanticStyle#REFSET} Generic semantic assemblage which contains 0 data columns.  Available with the semantic APIs.
 * {@link SemanticStyle#MAPSET} A specific semantic assemblage that contains data columns, in a pattern defined as a mapset.  Accessible with the mapping APIs, 
 *   or the generic semantic APIs.
 * {@link SemanticStyle#ASSOCIATION} A specific semantic assemblage that contains data columns, in a pattern defined as a association.  Accessible with the association APIs, 
 *   or the generic semantic APIs.
 * extended types.
 * 
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a>
 */
@XmlRootElement
public class RestSemanticStyle extends Enumeration
{
	protected RestSemanticStyle()
	{
		// for jaxb
	}

	public RestSemanticStyle(SemanticStyle ds)
	{
		super(ds.name(), null, ds.ordinal());
	}

	public static RestSemanticStyle[] getAll()
	{
		RestSemanticStyle[] result = new RestSemanticStyle[SemanticStyle.values().length];
		for (int i = 0; i < SemanticStyle.values().length; i++)
		{
			result[i] = new RestSemanticStyle(SemanticStyle.values()[i]);
		}
		return result;
	}
}