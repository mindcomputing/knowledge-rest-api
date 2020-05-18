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

package net.sagebits.uts.auth.data;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.sagebits.uts.auth.users.UserService;
import sh.isaac.api.util.PasswordHasher;

/**
 * 
 * {@link User}
 *
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a>
 */
public class User implements Serializable
{
	private static final long serialVersionUID = 1L;
	private static Logger log = LogManager.getLogger(User.class);
	public static final UUID GLOBAL = UUID.nameUUIDFromBytes("GLOBAL".getBytes());
	public static final UUID ANON_READ_ID = UUID.fromString("aacbf0da-eeb9-4c37-8f49-f61918b67ead");  //Random UUID
	
	private final UUID id_;
	private String userName_;
	private String displayName_;
	private String email_;
	private final HashMap<UUID, Set<UserRole>> roles_ = new HashMap<>();
	private boolean enable_ = true;
	private String encryptedLocalPassword_;  //If the user is using local auth, this will be their local password
	private String encryptedServiceToken_;  //An optional token a user can assign to an account.  This is meant for service-to-service auth.
	private boolean initialPWChanged_= false;  
	private boolean licenseAccepted_ = false;
	private int logOutCount_ = 0;  //Used in the SSO tokens, to invalidate old tokens upon logout
	
	private transient UserService us_;
	
	/**
	 * Create a new user
	 * @param id - required
	 * @param userName - recommended, email has to be set if this isn't
	 * @param displayName - required
	 * @param globalRoles - optional
	 * @param dbRoles - optional
	 */
	public User(UUID id, String userName, String displayName, UserRole[] globalRoles, Map<UUID, UserRole[]> dbRoles)
	{
		id_ = id;
		userName_ = userName;
		displayName_ = displayName;
		HashSet<UserRole> globals = new HashSet<>();
		if (globalRoles != null)
		{
			for (UserRole role : globalRoles)
			{
				globals.add(role);
			}
		}
		roles_.put(GLOBAL, fixRoleChains(globals));
		
		if (dbRoles != null)
		{
			for (Entry<UUID, UserRole[]> dbRoleSet : dbRoles.entrySet())
			{
				HashSet<UserRole> temp = new HashSet<>(dbRoleSet.getValue().length);
				temp.addAll(Arrays.asList(dbRoleSet.getValue()));
				roles_.put(dbRoleSet.getKey(), fixRoleChains(temp));
			}
		}
	}

	/**
	 * Ensure that if a role like editor is granted, any roles it depends on it also granted
	 * @param temp
	 * @return
	 */
	private Set<UserRole> fixRoleChains(HashSet<UserRole> temp)
	{
		for (UserRole ur : UserRole.values())
		{
			if (temp.contains(ur))
			{
				for (UserRole dependsOn: ur.includesRoles())
				{
					if (!temp.contains(dependsOn))
					{
						log.info("Adding dependent role {} because required by {}", dependsOn, ur);
						temp.add(dependsOn);
					}
				}
			}
		}
		return temp;
	}

	/**
	 * @return the username
	 */
	public String getUserName()
	{
		return userName_;
	}
	
	/**
	 * @return The user specified name for display
	 */
	public String getDisplayName()
	{
		return displayName_;
	}

	/**
	 * @return The ID for this user
	 */
	public UUID getId()
	{
		return id_;
	}
	
	/**
	 * @return the email address
	 */
	public String getEmail()
	{
		return email_;
	}
	
	/**
	 * @return account status
	 */
	public boolean isEnabled()
	{
		return enable_;
	}

	/**
	 * @return the roles the apply to the entire system, and any database
	 */
	public Set<UserRole> getGlobalRoles()
	{
		return Collections.unmodifiableSet(roles_.get(GLOBAL));
	}
	
	/**
	 * @param dbId the UUID of the database to request role info on
	 * @return the additional roles, if any that apply to the specified DB
	 */
	public Set<UserRole> getDBRoles(UUID dbId)
	{
		Set<UserRole> temp = roles_.get(dbId);
		return temp == null ? new HashSet<>(0) : Collections.unmodifiableSet(temp);
	}
	
	/**
	 * @return the roles map that specifies roles for each DB, not including the global roles.  Returned as an unmodifiyable copy
	 */
	public Map<UUID, Set<UserRole>> getDBRoles()
	{
		HashMap<UUID, Set<UserRole>> temp = new HashMap<>();
		
		for (Entry<UUID, Set<UserRole>> x : roles_.entrySet())
		{
			if (!x.getKey().equals(GLOBAL))
			{
				temp.put(x.getKey(), Collections.unmodifiableSet(x.getValue()));
			}
		}
		return Collections.unmodifiableMap(temp);
	}
	
	/**
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id_ == null) ? 0 : id_.hashCode());
		return result;
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		User other = (User) obj;
		if (id_ == null)
		{
			if (other.id_ != null)
				return false;
		}
		else if (!id_.equals(other.id_))
			return false;
		return true;
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		return "User [userName=" + userName_ + ", email=" + email_+ ", id=" + id_ + ", Roles=" + rolesToString().toString() + ", displayName=" + displayName_ + ", enable=" + enable_ + 
				" serviceTokenAssigned? " + hasServiceToken() + " initialPasswordChanged=" + initialPWChanged_ + " licenseAccepted=" + licenseAccepted_ +
				" logOutCount=" + logOutCount_ + "]";
	}
	
	private StringBuilder rolesToString()
	{
		StringBuilder sb = new StringBuilder();
		for (Entry<UUID, Set<UserRole>> role : roles_.entrySet())
		{
			sb.append("DB ID ");
			sb.append(role.getKey().equals(GLOBAL) ? "Global" : role.getKey());
			sb.append("[");
			for (UserRole ur : role.getValue())
			{
				sb.append(ur.name());
				sb.append(",");
			}
			if (role.getValue().size() > 0)
			{
				sb.setLength(sb.length() - 1);
			}
			sb.append("]");
		}
		return sb;
	}

	/**
	 * @return
	 */
	public String getSSOToken()
	{
		return new SSOToken(id_, logOutCount_).getSerialized();
	}
	
	/**
	 * @param newPassword
	 */
	public void setPassword(char[] newPassword)
	{
		try
		{
			encryptedLocalPassword_ = PasswordHasher.getSaltedHash(newPassword);
		}
		catch (Exception e)
		{
			log.error("Unexpected error encrypting password", e);
			throw new RuntimeException(e);
		}
	}
	
	public void setEmail(String email)
	{
		email_ = email;
	}
	
	/**
	 * @param passwordToTest
	 * @return true, if the password is correct
	 */
	public boolean validatePassword(char[] passwordToTest)
	{
		if (us_ == null)
		{
			throw new RuntimeException("UserStore ref not set prior to validate Password attempt");
		}
		if (!isEnabled())
		{
			log.info("Preventing disabled user authentication attempt");
			return false;
		}
		if (StringUtils.isBlank(encryptedLocalPassword_))
		{
			us_.userAuthFailed(id_);
			return false;
		}
		try
		{
			if (PasswordHasher.check(passwordToTest, encryptedLocalPassword_))
			{
				us_.userAuthSuccess(id_);
				return true;
			}
			else
			{
				us_.userAuthFailed(id_);
				return false;
			}
		}
		catch (Exception e)
		{
			log.error("Unexpected error checking password", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param displayName
	 */
	public void setDisplayName(String displayName)
	{
		displayName_ = displayName;
	}

	/**
	 * @param b
	 */
	public void setEnabled(boolean b)
	{
		enable_ = b;
	}

	/**
	 * @param userName
	 */
	public void setUserName(String userName)
	{
		userName_ = userName;
	}
	
	@Override
	public User clone()
	{
		User u = new User(id_, userName_, displayName_, getGlobalRoles().toArray(new UserRole[] {}), null);
		
		for (Entry<UUID, Set<UserRole>> dbRoleSet : getDBRoles().entrySet())
		{
			u.roles_.put(dbRoleSet.getKey(), dbRoleSet.getValue());
		}
		u.encryptedLocalPassword_ = encryptedLocalPassword_;
		u.email_ = email_;
		u.enable_ = enable_;
		u.encryptedServiceToken_ = encryptedServiceToken_;
		u.initialPWChanged_ = initialPWChanged_;
		u.licenseAccepted_ = licenseAccepted_;
		u.logOutCount_ = logOutCount_;
		u.us_ = us_;
		
		return u;
	}

	/**
	 * @param dbId the dbId to remove roles for
	 */
	public void removeDBRoles(UUID dbId)
	{
		roles_.remove(dbId);
	}

	/**
	 * @param projectId
	 * @param newRoles
	 */
	public void setDBRoles(UUID projectId, HashSet<UserRole> newRoles)
	{
		roles_.put(projectId, fixRoleChains(newRoles));
	}
	
	/**
	 * Create a new service token for this account (replacing / invalidating any existing token)
	 * @return The newly assigned serviceToken.
	 */
	public String assignServiceToken()
	{
		UUID serviceToken = UUID.randomUUID();
		try
		{
			encryptedServiceToken_ = PasswordHasher.getSaltedHash(serviceToken.toString().toCharArray());
			return serviceToken.toString();
		}
		catch (Exception e)
		{
			log.error("Unexpected error encrypting service token", e);
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Set a service token for this account (replacing / invalidating any existing token).
	 * This is intended for internal use, you should use  {@link #assignServiceToken}
	 * @param tokenValue the unencrypted tokenValue
	 */
	public void setServiceToken(char[] tokenValue)
	{
		try
		{
			encryptedServiceToken_ = (tokenValue == null || tokenValue.length == 0 ? null : PasswordHasher.getSaltedHash(tokenValue));
		}
		catch (Exception e)
		{
			log.error("Unexpected error encrypting service token", e);
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Call this to invalidate all existing SSO tokens
	 */
	public void logout()
	{
		log.debug("All tokens invalidated for {} - {}", getUserName(), getId());
		logOutCount_++;
	}
	
	public int getLogoutCount()
	{
		return logOutCount_;
	}
	
	/**
	 * Remove the service token from this account, leaving it so that you cannot login with a service token
	 */
	public void removeServiceToken()
	{
		encryptedServiceToken_ = null;
	}
	
	/**
	 * @return true, if there exists a service token that can be used to access this account.  false otherwise.
	 */
	public boolean hasServiceToken()
	{
		return StringUtils.isNotBlank(encryptedServiceToken_);
	}
	
	/**
	 * Validate the passed in service token.
	 * @param serviceToken the token to check
	 * @return true if valid, false otherwise.
	 */
	public boolean validateServiceToken(String serviceToken)
	{
		if (!isEnabled())
		{
			log.info("Preventing disabled user authentication attempt");
			return false;
		}
		if (StringUtils.isBlank(encryptedServiceToken_))
		{
			return false;
		}
		try
		{
			return PasswordHasher.check(serviceToken.toCharArray(), encryptedServiceToken_);
		}
		catch (Exception e)
		{
			log.error("Unexpected error checking service token", e);
			throw new RuntimeException(e);
		}
	}
	
	public void setLicenseAccepted()
	{
		licenseAccepted_ = true;
	}
	
	public boolean isLicenseAccepted()
	{
		return licenseAccepted_;
	}
	
	public boolean isInitialPasswordChanged()
	{
		return initialPWChanged_;
	}
	
	/**
	 * For UserService use only
	 * @param us
	 */
	public void setUserService(UserService us)
	{
		us_ = us;
	}

	public void initialPasswordChanged(boolean b)
	{
		initialPWChanged_ = b;
	}
}