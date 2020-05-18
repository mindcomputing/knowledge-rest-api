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

package net.sagebits.tmp.isaac.rest.session;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.sagebits.tmp.isaac.rest.ApplicationConfig;
import net.sagebits.tmp.isaac.rest.Util;
import net.sagebits.tmp.isaac.rest.api.exceptions.RestException;
import net.sagebits.tmp.isaac.rest.tokens.EditToken;
import net.sagebits.uts.auth.data.SSOToken;
import net.sagebits.uts.auth.data.User;
import net.sagebits.uts.auth.data.UserRole;
import net.sagebits.uts.auth.rest.api1.data.RestUser;
import net.sagebits.uts.auth.rest.session.AuthRequestParameters;
import net.sagebits.uts.auth.users.UserService;
import sh.isaac.api.Get;

/**
 * 
 * {@link RestUserServiceLocal}
 *
 * This implementation of the {@link RestUserService} is for testing and simple deployments, and does lookups against
 * a local user store.  It does NOT currently support service tokens.
 *
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a>
 */

public class RestUserServiceLocal implements RestUserService
{
	private static Logger log = LogManager.getLogger(RestUserServiceLocal.class);
	
	private UserService us;
	private UUID dbID;
	
	protected RestUserServiceLocal()
	{
		//for RestUserServiceSelector
		
		File tempDirName = new File(System.getProperty("java.io.tmpdir"));
		File userStore = new File(tempDirName, "auth-users-" + ApplicationConfig.getInstance().getContextPath());
		userStore.mkdirs();
		
		us = new UserService(userStore, true);
		dbID = Get.dataStore().getDataStoreId().get();
		//set up the SSOToken secret path for our use
		SSOToken.setSecretPathPrefix(ApplicationConfig.getInstance().getContextPath());
	}
	

	/**
	 * @see net.sagebits.tmp.isaac.rest.session.RestUserService#getUser(java.util.Map, net.sagebits.tmp.isaac.rest.tokens.EditToken)
	 */
	@Override
	public Optional<RestUser> getUser(Map<String, List<String>> requestParameters, EditToken editToken, Function<String, Optional<String>> cookieValueProvider) throws RestException
	{
		Optional<User> user = Optional.empty();
		if (requestParameters.containsKey(AuthRequestParameters.ssoToken) || cookieValueProvider.apply(SSOToken.cookieName).isPresent())
		{
			try
			{
				SSOToken st = new SSOToken(cookieValueProvider.apply(SSOToken.cookieName).orElse(requestParameters.get(AuthRequestParameters.ssoToken).get(0)));
				user = us.getUser(st.getUser());
				if (!user.isPresent())
				{
					log.error("Valid SSO token but no user for user id {}?" , st.getUser());
				}
				else if (!user.get().isEnabled())
				{
					throw new SecurityException("The user tied to the passed SSO token is disabled");
				}
				else if (st.getUserLogOutCount() != user.get().getLogoutCount())
				{
					//They have logged out since this token was issued, this one is invalid
					throw new SecurityException("The user logged out after this SSO token was issued");
				}
			}
			catch (Exception e)
			{
				throw new SecurityException(e.getMessage());
			}
		}
		
		if (!user.isPresent() && (requestParameters.containsKey(AuthRequestParameters.userName) || requestParameters.containsKey(AuthRequestParameters.email)))
		{
			//Local Auth
			if (!requestParameters.containsKey(AuthRequestParameters.password))
			{
				throw new SecurityException("If 'userName' or 'email' is provided, then 'password' must be provided as well");
			}
			
			Optional<User> oUser = us.findUser(RequestInfoUtils.getFirstParameterValue(requestParameters, AuthRequestParameters.userName), 
					RequestInfoUtils.getFirstParameterValue(requestParameters, AuthRequestParameters.email));
			
			if (oUser.isPresent())
			{
				if (!oUser.get().isEnabled())
				{
					throw new SecurityException("User account disabled");
				}
				else if (oUser.get().validatePassword(requestParameters.get(AuthRequestParameters.password).get(0).toCharArray()))
				{
					user = oUser;
				}
				else
				{
					throw new SecurityException("Invalid user information - auth fail");
				}
			}
			else
			{
				throw new SecurityException("Invalid user information");
			}
		}
		
		if (editToken != null)
		{
			if (user.isPresent())
			{
				//the nid in the editToken better align with the RestUser info
				if (!Get.identifierService().hasUuid(user.get().getId()))
				{
					log.warn("User id came in as {}:{} while the editToken has a user of {}:{}", 
							user.get().getId(), user.get().getUserName(), 
							editToken.getAuthorNid(), Util.readBestDescription(editToken.getAuthorNid()));
					throw new SecurityException("Mis-aligned editToken / authentication information");
				}
				else if (Get.identifierService().getNidForUuids(user.get().getId()) != editToken.getAuthorNid())
				{
					log.warn("User id came in as {}:{}:{} while the editToken has a user of {}:{}", user.get().getId(), 
							Get.identifierService().getNidForUuids(user.get().getId()), 
							Util.readBestDescription(Get.identifierService().getNidForUuids(user.get().getId())),
							editToken.getAuthorNid(), Util.readBestDescription(editToken.getAuthorNid()));
					throw new SecurityException("Mis-aligned editToken / authentication information");
				}
			}
			else
			{
				//Only an editToken, no ssoToken / user / password.  setup the user from the info in the editToken.
				user = us.getUser(Get.identifierService().getUuidPrimordialForNid(editToken.getAuthorNid()));
			}
		}
		
		if (!user.isPresent() && RestConfig.getInstance().allowAnonymousRead())
		{
			//provide a read role for ease of testing / debug
			log.info("Generating a random read-only user for test, since anon read is enabled");
			user = Optional.of(new User(User.ANON_READ_ID, "ReadOnly", "Read Only", 
					new UserRole[] {UserRole.READ}, null));
		}
		
		Optional<RestUser> ru = Optional.ofNullable(user.isPresent() ? new RestUser(user.get(), dbID) : null);
		if (ru.isPresent())
		{
			//Make sure that a concept exists for the user.
			getAuthorNid(ru.get());
			
			if (editToken != null)
			{
				editToken.setUser(ru.get());
			}
		}
		return ru;
	}


	@Override
	public void clearCache()
	{
		// noop
	}
}
