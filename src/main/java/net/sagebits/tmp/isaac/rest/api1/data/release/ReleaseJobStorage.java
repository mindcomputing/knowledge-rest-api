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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.JsonWriter;
import sh.isaac.api.Get;

/**
 * {@link ReleaseJobStorage}
 *
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a> 
 */
public class ReleaseJobStorage
{
	private static Logger log = LogManager.getLogger();
	private static final String RELEASE_JOB_STORE = "releaseJobStore";
	
	/**
	 * For use by the read API to read back the release job results. 
	 * @param releaseJobKey - the release job to read.
	 * @return The ReleaseJobs, if available, null if the key is unknown.
	 */
	public static ReleaseJob getReleaseJobResults(UUID releaseJobKey)
	{
		String temp = Get.metaContentService().<UUID, String>openStore(RELEASE_JOB_STORE).get(releaseJobKey);
		if (temp == null)
		{
			return null;
		}
		else
		{
			return (ReleaseJob)JsonReader.jsonToJava(temp);
		}
	}
	
	/**
	 * @return all stored release job info, sorted by launch time, most recent to oldest. 
	 */
	public static List<ReleaseJob> getReleaseJobResults()
	{
		ArrayList<ReleaseJob> results = new ArrayList<>();
		for(String s : Get.metaContentService().<UUID, String>openStore(RELEASE_JOB_STORE).values())
		{
			ReleaseJob rcr = (ReleaseJob)JsonReader.jsonToJava(s); 
			results.add(rcr);
		}
		Collections.sort(results);
		return results;
	}
	
	public static void store(ReleaseJob rj)
	{
		String temp = JsonWriter.objectToJson(rj);
		log.debug("Release Run stored:" + rj.toString());
		Get.metaContentService().<UUID, String>openStore(RELEASE_JOB_STORE).put(rj.id, temp);
	}
	
	public static void clearStoredData()
	{
		Get.metaContentService().<UUID, String>openStore(RELEASE_JOB_STORE).clear();
		log.info("Release run data cleared");
	}
	
	/**
	 * Iterate the stored release job executions, and mark any that are still active as failed.
	 * This would typically only be called on system startup, to clean up after any release job executions that were abandoned due to a server shutdown.
	 */
	public static void cleanAbandoned()
	{
		for (UUID uuid : Get.metaContentService().<UUID, String>openStore(RELEASE_JOB_STORE).keySet()) 
		{
			ReleaseJob rj = getReleaseJobResults(uuid);
			if (rj != null && rj.completeTime == null)
			{
				rj.completeTime = System.currentTimeMillis();
				rj.status = "System shutdown prior to completion";
				Get.metaContentService().<UUID, String>openStore(RELEASE_JOB_STORE).put(uuid, JsonWriter.objectToJson(rj));
			}
		}
	}
}
