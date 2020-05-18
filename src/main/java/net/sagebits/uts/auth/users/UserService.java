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

package net.sagebits.uts.auth.users;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.JsonWriter;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import net.sagebits.uts.auth.data.User;
import net.sagebits.uts.auth.data.UserRole;
import sh.isaac.api.sync.MergeFailOption;
import sh.isaac.api.sync.MergeFailure;
import sh.isaac.api.util.PasswordHasher;
import sh.isaac.provider.sync.git.SyncServiceGIT;


/**
 * {@link UserService}
 * 
 * Our store of users.  
 * 
 *
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a>
 */

public class UserService
{
	private transient static Logger log = LogManager.getLogger();
	
	/**
	 * system or environment value that will be read to find the user import file
	 */
	public static final String AUTH_USER_IMPORT = "AUTH_USER_IMPORT";
	
	/**
	 * Constant UUID to represent the automated user.  The automated user is always created, with the 
	 * {@link UserRole#AUTOMATED} assigned to it.  
	 */
	public static final UUID AUTOMATED_USER = UUID.fromString("8e54759d-f727-4b49-8028-830e93f57d89");
	
	private final ConcurrentHashMap<UUID, User> users_ = new ConcurrentHashMap<>();
	private final transient ConcurrentHashMap<String, UUID> uniqueUserName_ = new ConcurrentHashMap<>();
	private final transient ConcurrentHashMap<String, UUID> uniqueEmail_ = new ConcurrentHashMap<>();
	private final transient Set<UUID> usersWithServiceTokens_ = new ConcurrentHashMap<UUID, Boolean>().keySet(true);
	private final transient ConcurrentHashMap<UUID, AtomicInteger> loginFailCount_ = new ConcurrentHashMap<>();
	
	private final File storageLocation_;
	private final SyncServiceGIT gitFileSync_;
	private final String gitUserName_;
	private final char[] gitPassword_;
	
	boolean initialInitComplete = false;
	private Consumer<UUID> notify;
	
	/**
	 * @param storageLocation Where to store the users (as a json file).  Will also create a file with this name, 
	 *     plus an additional ".bak" extension.
	 * @param enableUserImport true to allow import of file at the AUTH_USER_IMPORT location, false otherwise.
	 *     Reads the value of the system or environment variable AUTH_USER_IMPORT, treating it as a file path.
	 */
	public UserService(File storageLocation, boolean enableUserImport)
	{
		this(storageLocation, enableUserImport, null, null, null, null);
	}

	/**
	 * @param storageLocation The folder where to store the users.  The service will create multiple files in this folder.
	 * @param enableUserImport true to allow import of file at the AUTH_USER_IMPORT location, false otherwise.
	 * Reads the value of the system or environment variable AUTH_USER_IMPORT, treating it as a file path.
	 * @param gitURL - optional - the full URL for the git repository to sync users from / to
	 * @param gitUser - the username to use for the git sync
	 * @param gitPassword - the password to use for the git sync.
	 * @param automatedServiceToken - the service token to use for the automated user
	 */
	public UserService(File storageLocation, boolean enableUserImport, String gitURL, String gitUser, char[] gitPassword, char[] automatedServiceToken)
	{
		storageLocation_ = storageLocation;
		
		if (!storageLocation_.isDirectory())
		{
			throw new RuntimeException("storage location should be a directory");
		}
		
		if (StringUtils.isNotBlank(gitURL))
		{
			log.info("Connecting to git server {} to pull user data", gitURL);
			gitFileSync_ = new SyncServiceGIT();
			gitUserName_ = gitUser;
			gitPassword_ = gitPassword;
			if (gitFileSync_ == null)
			{
				throw new RuntimeException("Git sync service not available in config!");
			}
			
			gitFileSync_.setRootLocation(storageLocation_);
			gitFileSync_.setReadmeFileContent("This repository stores the user database for the auth server.  Do not directly edit these files.");
			gitFileSync_.setGitIgnoreContent("*.json.bak\r\n");
			try
			{
				gitFileSync_.linkAndFetchFromRemote(gitURL, gitUser, gitPassword);
				log.info("Linked to GIT");
			}
			catch (IllegalArgumentException | IOException e)
			{
				throw new RuntimeException("Failed to sync with remote git server", e);
			}
		}
		else
		{
			gitFileSync_ = null;
			gitUserName_ = null;
			gitPassword_ = null;
		}
		
		File jsonFile = new File(storageLocation_, "users.json");
		
		if (jsonFile.isFile())
		{
			log.info("Reading in user store from {}", jsonFile);
			try
			{
				JsonReader jr = new JsonReader(new FileInputStream(jsonFile));
				@SuppressWarnings("unchecked")
				ConcurrentHashMap<UUID, User> readUsers = (ConcurrentHashMap<UUID, User>)jr.readObject();
				jr.close();
				for (Entry<UUID, User> x : readUsers.entrySet())
				{
					x.getValue().setUserService(this);
					users_.put(x.getKey(), x.getValue());
					if (x.getValue().getEmail() != null)
					{
						uniqueEmail_.put(x.getValue().getEmail().toLowerCase(), x.getKey());
					}
					if (x.getValue().getUserName() != null)
					{
						uniqueUserName_.put(x.getValue().getUserName().toLowerCase(), x.getKey());
					}
					if (x.getValue().hasServiceToken())
					{
						usersWithServiceTokens_.add(x.getKey());
					}
				}
			}
			catch (FileNotFoundException e)
			{
				// this should be impossible
				log.error("error reading user store", e);
			}
			
			try
			{
				syncGit();
			}
			catch (IllegalArgumentException | IOException e)
			{
				log.error("initial sync to git failed!", e);
			}
		}
		else
		{
			log.info("Creating a new user store at {}", storageLocation_);
		}
		
		if (enableUserImport && System.getenv(AUTH_USER_IMPORT) != null || System.getProperty(AUTH_USER_IMPORT) != null)
		{
			try
			{
				File importFile = new File(System.getProperty(AUTH_USER_IMPORT, System.getenv(AUTH_USER_IMPORT)));
				if (importFile.isFile())
				{
					log.info("Importing users from {} because system property {} is set", importFile.getAbsolutePath(), AUTH_USER_IMPORT);
					try(CSVReader reader =  new CSVReaderBuilder(new BufferedReader(new InputStreamReader(new FileInputStream(importFile))))
							.withCSVParser(new CSVParserBuilder().withSeparator('\t').build()).build())
					{
						for (String[] line : reader.readAll())
						{
							if ((line.length > 0 && line[0].startsWith("#")) || line.length < 1 || line[0].length() == 0)
							{
								//skip comment line, lines missing usernames
								continue;
							}
							
							//#userName	displayName	ID	globalRoles	password (plaintext or encrypted via PasswordHasher)	emailAddress
							
							String userName = line[0];
							String displayName = (line.length < 2 || StringUtils.isBlank(line[1])) ? line[0] : line[1];
							UUID id = (line.length < 3 || StringUtils.isBlank(line[2])) ? UUID.randomUUID() : UUID.fromString(line[2]);
							String roles = (line.length < 4 || StringUtils.isBlank(line[3])) ? "" : line[3];
							char[] password = (line.length < 5 || StringUtils.isBlank(line[4])) ? new char[0] : PasswordHasher.decryptPropFileValueIfEncrypted(line[4]);
							String email = (line.length < 6 || StringUtils.isBlank(line[5])) ? "" : line[5];
							ArrayList<UserRole> globalRoles = new ArrayList<>();
							
							for (String roleString : roles.split(","))
							{
								if (roleString.length() > 0)
								{
									Optional<UserRole> ur = UserRole.parse(roleString);
									if (!ur.isPresent())
									{
										log.error("Invalid role string on user {} : {}, skipping role", roleString, userName);
										continue;
									}
									globalRoles.add(ur.get());
								}
							}
							
							if (users_.containsKey(id) || findUser(userName).isPresent() || (StringUtils.isNotBlank(email) && findUser(email).isPresent()))
							{
								log.info("Not adding user {} from the import file, because the user already exists in the store", userName);
							}
							else
							{
								User u = new User(id, userName, displayName, globalRoles.toArray(new UserRole[globalRoles.size()]), null);
								if (password.length > 0)
								{
									u.setPassword(password);
								}
								if (email.length() > 0)
								{
									u.setEmail(email);;
								}
								log.info("Adding user {} from import file", u);
								addOrUpdate(u);
							}
						}
					}
					
				}
				else
				{
					log.info("System property of {} was set to {}, but does not point to a file, will not import users", AUTH_USER_IMPORT, importFile.getAbsolutePath());
				}
			}
			catch (Exception e)
			{
				log.error("Error importing users from system property specified file", e);
			}
		}
		
		log.debug("Updating automated user");
		User automated = new User(AUTOMATED_USER, "automated", "automated", new UserRole[] {UserRole.AUTOMATED}, null);
		automated.setEnabled(automatedServiceToken != null && automatedServiceToken.length > 0);
		automated.setServiceToken(automatedServiceToken);
		addOrUpdate(automated);
		initialInitComplete = true;
	}
	
	/**
	 * @return The UUIDs of all currently known users
	 */
	public Set<UUID> getUsers()
	{
		return users_.keySet();
	}
	
	/**
	 * @return users with the global role of {@link UserRole#SYSTEM_MANAGER}
	 */
	public Set<User> getSystemAdmins()
	{
		HashSet<User> results = new HashSet<>();
		for (User u : users_.values())
		{
			if (u.getGlobalRoles().contains(UserRole.SYSTEM_MANAGER))
			{
				results.add(u.clone());
			}
		}
		return results;
	}
	
	/**
	 * Add a user to the set of known users.  Username and email, if provided, must be unique.
	 * @param user 
	 */
	public void addOrUpdate(User user)
	{
		if (AUTOMATED_USER.equals(user.getId()) && initialInitComplete)
		{
			throw new IllegalArgumentException("Not allowed to modify the automated user");
		}
		if (StringUtils.isNotBlank(user.getEmail()))
		{
			UUID test = uniqueEmail_.get(user.getEmail().toLowerCase());
			if (test != null && !user.getId().equals(test))
			{
				throw new IllegalArgumentException("The provided email address is already in use by a user");
			}
		}
		
		if (StringUtils.isNotBlank(user.getUserName()))
		{
			UUID test = uniqueUserName_.get(user.getUserName().toLowerCase());
			if (test != null && !user.getId().equals(test))
			{
				throw new IllegalArgumentException("The provided user name '" + user.getUserName() + "' is in use by a user");
			}
		}
		
		user.setUserService(this);
		User oldUserData = users_.put(user.getId(), user);
		if (oldUserData != null)
		{
			log.info("Replaced user information for previously existing user, old: {}, new: {}", oldUserData, user);
			if (StringUtils.isNotBlank(oldUserData.getEmail()))
			{
				uniqueEmail_.remove(oldUserData.getEmail().toLowerCase());
			}
			if (StringUtils.isNotBlank(oldUserData.getUserName()))
			{
				uniqueUserName_.remove(oldUserData.getUserName().toLowerCase());
			}
			if (oldUserData.hasServiceToken() && !user.hasServiceToken())
			{
				usersWithServiceTokens_.remove(user.getId());
			}
		}
		else
		{
			log.info("Stored new user: {}", user);
		}
		
		if (StringUtils.isNotBlank(user.getEmail()))
		{
			uniqueEmail_.put(user.getEmail().toLowerCase(), user.getId());
		}
		if (StringUtils.isNotBlank(user.getUserName()))
		{
			uniqueUserName_.put(user.getUserName().toLowerCase(), user.getId());
		}
		if (user.hasServiceToken())
		{
			usersWithServiceTokens_.add(user.getId());
		}
		
		try
		{
			save();
		}
		catch (IOException e)
		{
			log.error("Error writing user store", e);
		}
		notify(user.getId());
	}
	
	/**
	 * @param user The user to remove
	 * @return true if the user existed, and was removed.  False if the user did not exist, or could not be removed because it is protected
	 * (such as the automated user)
	 */
	public boolean removeUser(UUID user)
	{
		if (AUTOMATED_USER.equals(user))
		{
			return false;
		}
		Optional<User> userObject = getUser(user);
		if (userObject.isPresent())
		{
			users_.remove(user);
			if (StringUtils.isNotBlank(userObject.get().getUserName()))
			{
				uniqueUserName_.remove(userObject.get().getUserName().toLowerCase());
			}
			if (StringUtils.isNotBlank(userObject.get().getEmail()))
			{
				uniqueEmail_.remove(userObject.get().getEmail().toLowerCase());
			}
			usersWithServiceTokens_.remove(userObject.get().getId());
			try
			{
				save();
			}
			catch (IOException e)
			{
				log.error("Error writing user store", e);
			}
			notify(user);
			log.info("Removed user {}", userObject);
			return true;
		}
		else
		{
			log.info("Remove called with user that isn't present: {}");
			return false;
		}
	}
	
	private void notify(UUID user)
	{
		Consumer<UUID> localNotify = notify;
		if (localNotify != null)
		{
			localNotify.accept(user);
		}
	}

	public void setUserChangeNotify(Consumer<UUID> notify)
	{
		this.notify = notify;
	}
	
	/**
	 * Lookup a user via either their username (login name) or their email address.
	 * @param userNameOrEmail
	 * @return the user, if present
	 */
	public Optional<User> findUser(String ... userNameOrEmail)
	{
		for (String s : userNameOrEmail)
		{
			if (StringUtils.isBlank(s))
			{
				continue;
			}
			UUID temp = uniqueEmail_.get(s.toLowerCase());
			if (temp != null)
			{
				Optional<User> u =  Optional.ofNullable(users_.get(temp));
				return u.isPresent() ? Optional.of(u.get().clone()) : u;
			}
			
			temp = uniqueUserName_.get(s.toLowerCase());
			if (temp != null)
			{
				Optional<User> u =  Optional.ofNullable(users_.get(temp));
				return u.isPresent() ? Optional.of(u.get().clone()) : u;
			}
		}
		return Optional.empty();
	}
	

	/**
	 * Return all of the user information, if present, for the specified user.
	 * @param userId 
	 * @return the user, if present
	 */
	public Optional<User> getUser(UUID userId)
	{
		Optional<User> u = Optional.ofNullable(users_.get(userId));
		return u.isPresent() ? Optional.of(u.get().clone()) : u;
	}
	
	/**
	 * Return all of the user information, if present, for the user identified
	 * by a service token only
	 * @param serviceToken 
	 * @return the user, if present
	 */
	public Optional<User> getUser(String serviceToken)
	{
		for (UUID uuid : usersWithServiceTokens_)
		{
			Optional<User> u = getUser(uuid);
			if (u.isPresent() && u.get().validateServiceToken(serviceToken))
			{
				return u;
			}
		}
		return Optional.empty();
	}

	private void save() throws IOException
	{
		final Map<String, Object> args = new HashMap<>();
		args.put(JsonWriter.PRETTY_PRINT, true);
		
		File newWrite = new File(storageLocation_, "users.json.new");
		
		//overwrite any existing file
		try (JsonWriter jsonWriter = new JsonWriter(new FileOutputStream(newWrite, false), args);)
		{
			jsonWriter.write(users_);
		}
		
		File main = new File(storageLocation_, "users.json");
		File backup = new File(storageLocation_, "users.json.bak");
		
		//Move the current file out of the way, move the new file to the correctly named place.
		if (main.isFile())
		{
			Files.move(main.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		Files.move(newWrite.toPath(), main.toPath(), StandardCopyOption.REPLACE_EXISTING);
		
		syncGit();
	}
	
	private void syncGit() throws IllegalArgumentException, IOException
	{
		if (gitFileSync_ != null)
		{
			log.info("syncing users file to git server");
			gitFileSync_.addUntrackedFiles();
			
			Runnable r = new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						log.debug("commit and push begins");
						gitFileSync_.updateCommitAndPush("Synchronizing user store", gitUserName_, gitPassword_, MergeFailOption.KEEP_LOCAL, (String[])null);
						log.info("users file synchronized");
					}
					catch (IllegalArgumentException | IOException | MergeFailure e)
					{
						log.error("Sync to git failed!", e);
					}
				}
			};
			Thread t = new Thread(r, "backgroundGitPush");
			t.setDaemon(true);
			t.start();
		}
	}
	
	public void userAuthFailed(UUID userId)
	{
		AtomicInteger ai = loginFailCount_.computeIfAbsent(userId, idAgain -> new AtomicInteger(0));
		int count= ai.incrementAndGet();
		log.debug("User login fail count now {} for {}", count, userId);
		
		if (count >= 5)
		{
			Optional<User> user = getUser(userId);
			if (user.isPresent())
			{
				log.info("Disabling user {} due to auth attempt fail count", user.get());
				user.get().setEnabled(false);
				addOrUpdate(user.get());
			}
		}
	}
	
	public void userAuthSuccess(UUID userId)
	{
		loginFailCount_.remove(userId);
	}
}
