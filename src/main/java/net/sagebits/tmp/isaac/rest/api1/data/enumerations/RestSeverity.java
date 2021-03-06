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
import sh.isaac.api.qa.Severity;

/**
 * @author darmbrust
 */
@XmlRootElement
public class RestSeverity extends Enumeration
{
	protected RestSeverity()
	{
		// for jaxb
	}

	public RestSeverity(Severity st)
	{
		super(st.name(), null, st.ordinal());
	}

	public static RestSeverity[] getAll()
	{
		RestSeverity[] result = new RestSeverity[Severity.values().length];
		for (int i = 0; i < Severity.values().length; i++)
		{
			result[i] = new RestSeverity(Severity.values()[i]);
		}
		return result;
	}
}