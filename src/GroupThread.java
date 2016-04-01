/* This thread does all the work. It communicates with the client through Envelopes.
 * 
 */
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.KeyAgreement;
import javax.crypto.SealedObject;
import javax.crypto.SecretKey;

public class GroupThread extends Thread 
{
	private final Socket socket;
	private GroupServer my_gs;
	private boolean isSecureConnection;
	private boolean isAuthenticated;
	private int sequenceNumber;
	private KeyPair rsaKeyPair;
	private SecretKey sessionKey;
	private String username;

	public GroupThread(Socket _socket, GroupServer _gs)
	{
		socket = _socket;
		my_gs = _gs;
		isSecureConnection = false;
		isAuthenticated = false;
		sessionKey = null;
		rsaKeyPair = my_gs.keyPair;
		username = "";
	}

	public void run()
	{
		boolean proceed = true;
		try
		{
			//Announces connection and opens object streams
			System.out.println("*** New connection from " + socket.getInetAddress() + ":" + socket.getPort() + "***");
			final ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
			final ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
			
			do
			{
				Envelope message = null; // = (Envelope)input.readObject();
				Envelope response;
				Envelope innerResponse;
				if(!isSecureConnection) {
					message = (Envelope)input.readObject();
				}
				// decrypt envelopes after establishing a secure connection with
				// a shared symmetric secret key
				else {
					try {
						Envelope superE = (Envelope)input.readObject();
						message = Envelope.extractInner(superE, sessionKey);
					} catch(Exception e) {
						e.printStackTrace();
						response = new Envelope("FAIL");
						response.addObject(response);
						output.writeObject(response);
					}
				}
				// null message check
				if(message == null) {
					response = new Envelope("FAIL");
					response.addObject(response);
					output.writeObject(response);
				}
				
				System.out.println("\nRequest received: " + message.getMessage());
				
/*---------------------------------"RSALOGIN"---------------------------------*/	
				if (message.getMessage().equals("RSALOGIN")) {
					response = new Envelope("FAIL");
					if (message.getObjContents().size() == 3) {
						if (message.getObjContents().get(0) != null) {
							if (message.getObjContents().get(1) != null) {
								if (message.getObjContents().get(2) != null) {
									System.out.println("-----SIGNED-DIFFIE-HELLMAN - Receiving user Diffie Hellman Public Key-----");
									System.out.println("Received: \n" + message + "\n");
									String user = (String)message.getObjContents().get(0);
									SealedObject sealedHash = (SealedObject)message.getObjContents().get(1);
									PublicKey recvdKey = (PublicKey)message.getObjContents().get(2);
									PublicKey userPublicKey = getUserPublicKey(user);
									System.out.println("Seaching for User Public Key");
									if (userPublicKey != null) {
										System.out.println("Found User Public Key");
										byte[] recvHash = (byte[])CipherBox.decrypt(sealedHash, userPublicKey);
										byte[] madeHash = Hasher.hash(recvdKey);
										System.out.println("Verify the signed Hash with the made one.");
										if (Hasher.verifyHash(recvHash, recvdKey)) {
											System.out.println("Hashes Matched");
											KeyPair keyPair = null;
											KeyAgreement keyAgreement = null;
											// generate secret key and send back public key
											try {
												keyPair = DiffieHellman.genKeyPair();
												keyAgreement = DiffieHellman.genKeyAgreement(keyPair);
												sessionKey = DiffieHellman.generateSecretKey(recvdKey, keyAgreement);
												System.out.println("Generated Session Key: " + sessionKey);
												// Send second message
												System.out.println("-----SIGNED-DIFFIE-HELLMAN - Send my Diffie Hellman Public Keys-----");
												Envelope message2 = new Envelope("RSALOGINOK");
												byte[] hashedPublicKey = Hasher.hash(keyPair.getPublic());
												SealedObject sealedKey;
												sealedKey = CipherBox.encrypt(hashedPublicKey, my_gs.keyPair.getPrivate());
												message2.addObject(sealedKey);
												message2.addObject(keyPair.getPublic());
												System.out.println("Sending: ");
												System.out.println(message2 + "\n");
												output.writeObject(message2);
												// Get third message
												Envelope superMessage3 = (Envelope)input.readObject();
												Envelope message3 = Envelope.extractInner(superMessage3, sessionKey);
												System.out.println("-----SIGNED-DIFFIE-HELLMAN - Received Succes Hash and Inital Sequence Number-----");
												System.out.println("Received: " + message3 + "\n");
												if (message3 != null) {
													if (message3.getMessage().equals("SUCCESS")) {
														if (message3.getObjContents().size() == 2) {
															if (message3.getObjContents().get(0) != null) {
																if (message3.getObjContents().get(1) != null) {
																	byte[] recvHashWord = (byte[])message3.getObjContents().get(0);
																	String keyPlusWord = CipherBox.getKeyAsString(sessionKey);
																	keyPlusWord = keyPlusWord + user;
																	System.out.println("Verify that the Succes Hash matches");
																	if (Hasher.verifyHash(recvHashWord, keyPlusWord)) {
																		System.out.println("Hashes Match");
																		isSecureConnection = true;
																		isAuthenticated = true;
																		username = user;
																		Integer seqNum = (Integer)message3.getObjContents().get(1);
																		sequenceNumber = seqNum.intValue();
																		System.out.println("Inital Sequence Number set to: " + sequenceNumber);
																		// Client expects sequenceNumber + 1
																		sequenceNumber++;
																		// Send 4th message
																		System.out.println("-----SIGNED-DIFFIE-HELLMAN - Sending my Success Hash-----");
																		Envelope message4 = new Envelope("SUCCESS");
																		keyPlusWord = CipherBox.getKeyAsString(sessionKey);
																		keyPlusWord = keyPlusWord + "groupserver";
																		byte[] hashResponse = Hasher.hash(keyPlusWord);
																		message4.addObject(hashResponse);
																		message4.addObject(sequenceNumber);
																		System.out.println("Sending: ");
																		System.out.println(message4 + "\n");
																		response = Envelope.buildSuper(message4, sessionKey);
																		System.out.println("Secure and Authenticated connection with Group Client Established.");
																	}
																}
															}
														}
													}
												} 
											} catch(Exception e) {
												e.printStackTrace();
											}
										}
									}
									else {
										System.out.println("Look up failed");
									}
								}
							}
						}
					}
					output.writeObject(response);
				}
/*---------------------------------"RSAKEY"-----------------------------------*/
				else if (message.getMessage().equals("RSAKEY")
							&& isSecureConnection
							&& isAuthenticated) {
					if (message.getObjContents().size() < 2) {
						innerResponse = new Envelope("FAIL");
					}
					else {
						innerResponse = new Envelope("FAIL");
						if (message.getObjContents().get(0) != null) {
							if (message.getObjContents().get(1) != null) {
								if (username != null) {
									PublicKey userKey = (PublicKey)message.getObjContents().get(0);
									int tempseq = (Integer)message.getObjContents().get(1);
									if (tempseq == sequenceNumber + 1){
										if (setRSAKey(username, userKey)) {
											sequenceNumber += 2;
											innerResponse = new Envelope("OK");
											innerResponse.addObject(sequenceNumber);
											System.out.println(innerResponse);
										}
									}
								}
							}
						}
					}
					response = Envelope.buildSuper(innerResponse, sessionKey);
					output.writeObject(response);
				}
/*----------------------------------"GET"-------------------------------------*/
				else if (message.getMessage().equals("GET") 
							&& isSecureConnection
							&& isAuthenticated) {//Client wants a token
					innerResponse = new Envelope("FAIL");
					if (message.getObjContents().size() == 3) {
						if (message.getObjContents().get(0) != null){
							if (message.getObjContents().get(1) != null){
								if (message.getObjContents().get(2) != null){
									String user = (String)message.getObjContents().get(0); //Get the username
									PublicKey targetKey = (PublicKey)message.getObjContents().get(1);
									int tempseq = (Integer)message.getObjContents().get(2); //get sequence number
									if (tempseq == sequenceNumber + 1) {
										UserToken yourToken = createToken(username, targetKey); //Create a token
										//Respond to the client. On error, the client will receive a null token
										if (yourToken != null) {
											// Sign token
											if (yourToken.signToken(my_gs.keyPair.getPrivate())) {
												sequenceNumber += 2;
												innerResponse = new Envelope("OK");
												innerResponse.addObject(yourToken);
												innerResponse.addObject(sequenceNumber);
											}
										}
									}
								}
							}
						}
					}
					System.out.println("SENT from GET: " + innerResponse);
					response = Envelope.buildSuper(innerResponse, sessionKey);
					output.writeObject(response);
				}
/*--------------------------------"GET-GMETADATA"-----------------------------*/
				// retrieve the user's groups meta-data
				// should only be called on file upload/download after get token
				else if (message.getMessage().equals("GET-GMETADATA") 
						&& isSecureConnection
						&& isAuthenticated) {//Client wants meta-data for their groups
					if(message.getObjContents().size() != 2) {
						innerResponse = new Envelope("FAIL");
					}
					else {
						innerResponse = new Envelope("FAIL");
						// If there is no groupName
						//If there is no Token
						if (message.getObjContents().get(0) != null){
							if (message.getObjContents().get(1) != null) {
								int tempseq = (Integer)message.getObjContents().get(1);
								if (tempseq == sequenceNumber + 1){
									// Extract Token 
									UserToken yourToken = (UserToken)message.getObjContents().get(0);
									
									//check token to ensure expected and actual public keys match
									//if (KeyBox.compareKey(yourToken.getPublicKey(), rsaKeyPair.getPublic())) {
									//	innerResponse = new Envelope("FAIL");
									//}
									
									ArrayList<GroupMetadata> gMetaData = retrieveGroupsMetadata(yourToken);
									if(gMetaData != null) {
										sequenceNumber += 2;
										innerResponse = new Envelope("OK");
										innerResponse.addObject(gMetaData);
										innerResponse.addObject(sequenceNumber);
									}
								}
							}
						}
					}
					System.out.println("SENT from GET-GMETADATA: " + innerResponse);
					response = Envelope.buildSuper(innerResponse, sessionKey);
					output.writeObject(response);
				}
/*----------------------------------"CUSER"-----------------------------------*/
				else if (message.getMessage().equals("CUSER") 
							&& isSecureConnection
							&& isAuthenticated) {
					if (message.getObjContents().size() < 3) {
						innerResponse = new Envelope("FAIL");
					}
					else {
						innerResponse = new Envelope("FAIL");
						if(message.getObjContents().get(0) != null) {
							if(message.getObjContents().get(1) != null) {
								if (message.getObjContents().get(2) != null) {
									if(message.getObjContents().get(3) != null) {
										String username = (String)message.getObjContents().get(0); //Extract the username
										PublicKey newUserPubKey = (PublicKey)message.getObjContents().get(1);
										UserToken yourToken = (UserToken)message.getObjContents().get(2); //Extract the token
										int tempseq = (Integer)message.getObjContents().get(3); //get sequence number
										if (tempseq == sequenceNumber + 1) {
											if (KeyBox.compareKey(yourToken.getPublicKey(), rsaKeyPair.getPublic())) {
												innerResponse = new Envelope("FAIL");
											}	
											if (createUser(username, newUserPubKey, yourToken)) {
												sequenceNumber += 2;
												innerResponse = new Envelope("OK"); //Success
												innerResponse.addObject(sequenceNumber);
											}
										}
									}
								}
							}
						}
					}
					System.out.println("SENT from CUSER: " + innerResponse);
					response = Envelope.buildSuper(innerResponse, sessionKey);
					output.writeObject(response);
				}
/*----------------------------------"DUSER"-----------------------------------*/
				else if(message.getMessage().equals("DUSER") 
						&& isSecureConnection
						&& isAuthenticated) //Client wants to delete a user
				{
					if (message.getObjContents().size() < 3) {
						innerResponse = new Envelope("FAIL");
					}
					else {
						innerResponse = new Envelope("FAIL");
						
						if(message.getObjContents().get(0) != null){
							if(message.getObjContents().get(1) != null){
								if(message.getObjContents().get(2) != null){
									String username = (String)message.getObjContents().get(0); //Extract the username
									UserToken yourToken = (UserToken)message.getObjContents().get(1); //Extract the token
									int tempseq = (Integer)message.getObjContents().get(2); //extract seq num

									if (tempseq == sequenceNumber + 1){
										//check token to ensure expected and actual public keys match
										if (KeyBox.compareKey(yourToken.getPublicKey(), rsaKeyPair.getPublic())) {
											innerResponse = new Envelope("FAIL");
										}	
										
										if(deleteUser(username, yourToken)){
											sequenceNumber += 2;
											innerResponse = new Envelope("OK"); //Success
											innerResponse.addObject(sequenceNumber);
										}
									}
								}
							}
						}
					}
					System.out.println("SENT from DUSER: " + innerResponse);
					response = Envelope.buildSuper(innerResponse, sessionKey);
					output.writeObject(response);
				}
/*---------------------------------"CGROUP"-----------------------------------*/
				else if(message.getMessage().equals("CGROUP") 
						&& isSecureConnection
						&& isAuthenticated) //Client wants to create a group
				{	
					if (message.getObjContents().size() < 3) {
						innerResponse = new Envelope("FAIL");
					}
					else {
						innerResponse = new Envelope("FAIL");
						
						if(message.getObjContents().get(0) != null){
							if(message.getObjContents().get(1) != null){
								if(message.getObjContents().get(2) != null){
									String groupname = (String)message.getObjContents().get(0); //Extract the groupname
									UserToken yourToken = (UserToken)message.getObjContents().get(1); //Extract the token
									int tempseq = (Integer)message.getObjContents().get(2); //Extract sequence number

									if(tempseq == sequenceNumber + 1){
										//check token to ensure expected and actual public keys match
										if (KeyBox.compareKey(yourToken.getPublicKey(), rsaKeyPair.getPublic())) {
											innerResponse = new Envelope("FAIL");
										}
										
										if(createGroup(groupname, yourToken)){
											sequenceNumber += 2;
											innerResponse = new Envelope("OK"); //Success
											innerResponse.addObject(sequenceNumber);
										}
									}
								}
							}
						}
					}
					
					System.out.println("SENT from CGROUP: " + innerResponse);
					response = Envelope.buildSuper(innerResponse, sessionKey);
					output.writeObject(response);
				}
/*---------------------------------"DGROUP"-----------------------------------*/
				else if(message.getMessage().equals("DGROUP") 
						&& isSecureConnection
						&& isAuthenticated) //Client wants to delete a group
				{
					if (message.getObjContents().size() < 3) {
						innerResponse = new Envelope("FAIL");
					}
					else {
						innerResponse = new Envelope("FAIL");
						
						if(message.getObjContents().get(0) != null){
							if(message.getObjContents().get(1) != null){
								if(message.getObjContents().get(2) != null){
									String groupname = (String)message.getObjContents().get(0); //Extract the groupname
									UserToken yourToken = (UserToken)message.getObjContents().get(1); //Extract the token
									int tempseq = (Integer)message.getObjContents().get(2); //extract sequence number

									if(tempseq == sequenceNumber + 1){
										//check token to ensure expected and actual public keys match
										if (KeyBox.compareKey(yourToken.getPublicKey(), rsaKeyPair.getPublic())) {
											innerResponse = new Envelope("FAIL");
										}
										
										if(deleteGroup(groupname, yourToken)){
											sequenceNumber += 2;
											innerResponse = new Envelope("OK"); //Success
											innerResponse.addObject(sequenceNumber);
										}
									}
								}
							}
						}
					}
					System.out.println("SENT from DGROUP: " + innerResponse);
					response = Envelope.buildSuper(innerResponse, sessionKey);
					output.writeObject(response);
				}
/*---------------------------------"LMEMBERS"---------------------------------*/
				else if(message.getMessage().equals("LMEMBERS") 
						&& isSecureConnection
						&& isAuthenticated) //Client wants a list of members in a group
				{
					// If there isn't enough information in the envelope
					if (message.getObjContents().size() < 3) {
						innerResponse = new Envelope("FAIL");
					}
					else {
						innerResponse = new Envelope("FAIL");
						if (message.getObjContents().get(0) != null){
							if (message.getObjContents().get(1) != null){
								if(message.getObjContents().get(2) != null){
									String groupName = (String)message.getObjContents().get(0);
									UserToken yourToken = (UserToken)message.getObjContents().get(1);
									int tempseq = (Integer)message.getObjContents().get(2);

									if(tempseq == sequenceNumber + 1){
										//check token to ensure expected and actual public keys match
										if (KeyBox.compareKey(yourToken.getPublicKey(), rsaKeyPair.getPublic())) {
											innerResponse = new Envelope("FAIL");
										}

										List<String> members = listMembers(groupName, yourToken);
										if (members != null) {
											sequenceNumber += 2;
											innerResponse = new Envelope("OK");
											innerResponse.addObject(members);
											innerResponse.addObject(sequenceNumber);	
										}
									}
								}
							}
						}
					}
					System.out.println("SENT from LMEMBERS: " + innerResponse);
					output.flush();
					output.reset();
					response = Envelope.buildSuper(innerResponse, sessionKey);
					output.writeObject(response);
				}
/*-------------------------------"AUSERTOGROUP"-------------------------------*/
				else if(message.getMessage().equals("AUSERTOGROUP") 
						&& isSecureConnection
						&& isAuthenticated) //Client wants to add user to a group
				{
					// Is there a userName, groupName, and Token in the Envelope
					if (message.getObjContents().size() < 4)
					{
						innerResponse = new Envelope("FAIL");
					}
					else
					{
						innerResponse = new Envelope("FAIL");
						if (message.getObjContents().get(0) != null){
							if (message.getObjContents().get(1) != null){
								if (message.getObjContents().get(2) != null){
									if (message.getObjContents().get(3) != null){
										String userName = (String)message.getObjContents().get(0);
										String groupName = (String)message.getObjContents().get(1);
										UserToken yourToken = (UserToken)message.getObjContents().get(2);
										int tempseq = (Integer)message.getObjContents().get(3);

										if(tempseq == sequenceNumber + 1){
											//check token to ensure expected and actual public keys match
											if (KeyBox.compareKey(yourToken.getPublicKey(), rsaKeyPair.getPublic())) {
												innerResponse = new Envelope("FAIL");
											}

											if (addUserToGroup(userName, groupName, yourToken)){
												sequenceNumber += 2;
												innerResponse = new Envelope("OK");
												innerResponse.addObject(sequenceNumber);
											}
										}
									}
								}
							}
						}
					}
					System.out.println("SENT from AUSERTOGROUP: " + innerResponse);
					response = Envelope.buildSuper(innerResponse, sessionKey);
					output.writeObject(response);
				}
/*--------------------------------"RUSERFROMGROUP"----------------------------*/
				else if(message.getMessage().equals("RUSERFROMGROUP") 
						&& isSecureConnection
						&& isAuthenticated) //Client wants to remove user from a group
				{
					// Is there a userName, groupName, and Token in the Envelope
					if (message.getObjContents().size() < 4){
						innerResponse = new Envelope("FAIL");
					}
					else {
						innerResponse = new Envelope("FAIL");
						if (message.getObjContents().get(0) != null){
							if (message.getObjContents().get(1) != null){
								if (message.getObjContents().get(2) != null){
									if (message.getObjContents().get(3) != null){
										String userName = (String)message.getObjContents().get(0);
										String groupName = (String)message.getObjContents().get(1);
										UserToken yourToken = (UserToken)message.getObjContents().get(2);
										int tempseq = (Integer)message.getObjContents().get(3);

										if(tempseq == sequenceNumber + 1){
											//check token to ensure expected and actual public keys match
											if (KeyBox.compareKey(yourToken.getPublicKey(), rsaKeyPair.getPublic())) {
												innerResponse = new Envelope("FAIL");
											}
											
											if (deleteUserFromGroup(userName, groupName, yourToken)){
												sequenceNumber += 2;
												innerResponse = new Envelope("OK");
												innerResponse.addObject(sequenceNumber);
											}
										}
									}
								}
							}
						}
					}
					System.out.println("SENT from RUSERFROMGROUP: " + innerResponse);
					response = Envelope.buildSuper(innerResponse, sessionKey);
					output.writeObject(response);
				}
/*---------------------------------"DISCONNECT"-------------------------------*/
				else if(message.getMessage().equals("DISCONNECT") 
						&& isSecureConnection
						&& isAuthenticated) //Client wants to disconnect
				{
					isSecureConnection = false;
					username = null;
					sessionKey = null;
					socket.close(); //Close the socket
					proceed = false; //End this communication loop
				}
/*--------------------------------INVALID MESSAGE-----------------------------*/
				else
				{
					innerResponse = new Envelope("FAIL"); //Server does not understand client request
					System.out.println("SENT from INVALID MESSAGE: " + innerResponse);
					response = Envelope.buildSuper(innerResponse, sessionKey);
					output.writeObject(response);
				}
			}while(proceed);	
		}
		catch(Exception e)
		{
			isSecureConnection = false;
			username = null;
			sessionKey = null;
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace(System.err);
		}
	}

/*-------------------------Begin Additional Functions-------------------------*/
	
	//Method to create tokens
	private UserToken createToken(String username, PublicKey serverKey) 
	{
		//Check that user exists
		if(my_gs.userList.checkUser(username))
		{
			//Add the timestamp and signage
			//Issue a new token with server's name, user's name, and user's groups
			UserToken yourToken = new Token(my_gs.name, username, my_gs.userList.getUserGroups(username), serverKey);
			System.out.println(yourToken);
			return yourToken;
		}
		else
		{
			return null;
		}
	}
	
	
	//Method to create a user
	private boolean createUser(
						String username, 
						PublicKey userPublicKey, 
						UserToken yourToken) {
		String requester = yourToken.getSubject();
		
		//Check if requester exists
		if (my_gs.userList.checkUser(requester)) {
			//Get the user's groups
			ArrayList<String> temp = my_gs.userList.getUserGroups(requester);
			//requester needs to be an administrator
			if (temp.contains("ADMIN")) {
				//Does user already exist?
				if (my_gs.userList.checkUser(username)) {
					return false; //User already exists
				}
				else {
					my_gs.userList.addUser(username);
					my_gs.userList.setPublicKey(username, userPublicKey);
					// We no longer use passwords so this can be removed.
					// BigInteger salt = Passwords.generateSalt();
					// my_gs.userList.setSalt(username, salt);
					// byte[] hashword = Passwords.generatePasswordHash(
					// 								password, 
					// 								salt); 
					// my_gs.userList.setPassword(username, hashword);
					return true;
				}
			}
			else {
				return false; //requester not an administrator
			}
		}
		else {
			return false; //requester does not exist
		}
	}
	
	//Method to delete a user
	private boolean deleteUser(String username, UserToken yourToken)
	{
		String requester = yourToken.getSubject();

		//can't delete yourself
		if(requester.equals(username))
			return false;
		
		//Does requester exist?
		if(my_gs.userList.checkUser(requester))
		{
			ArrayList<String> temp = my_gs.userList.getUserGroups(requester);

			//requester needs to be an administer
			if(temp.contains("ADMIN")){
				//Does user exist?
				if(my_gs.userList.checkUser(username)){
					//User needs deleted from the groups they belong
					ArrayList<String> deleteFromGroups = new ArrayList<String>();
					
					//This will produce a hard copy of the list of groups this user belongs
					for(int index = 0; index < my_gs.userList.getUserGroups(username).size(); index++){
						deleteFromGroups.add(my_gs.userList.getUserGroups(username).get(index));
					}
					
					//Delete the user from the groups
					//If user is the owner, removeMember will automatically delete group!
					for(int index = 0; index < deleteFromGroups.size(); index++){
						System.out.println("index: " + index + ", group: " + deleteFromGroups.get(index));
						my_gs.groupList.removeMember(deleteFromGroups.get(index), username);
					}
					
					//If groups are owned, they must be deleted
					ArrayList<String> deleteOwnedGroup = new ArrayList<String>();
					
					//Make a hard copy of the user's ownership list
					for(int index = 0; index < my_gs.userList.getUserOwnership(username).size(); index++){
						deleteOwnedGroup.add(my_gs.userList.getUserOwnership(username).get(index));
					}
					
					//Delete owned groups
					for(int index = 0; index < deleteOwnedGroup.size(); index++){
						//Use the delete group method. Token must be created for this action
						deleteGroup(deleteOwnedGroup.get(index), new Token(my_gs.name, username, deleteOwnedGroup));
					}
					
					//Delete the user from the user list
					my_gs.userList.deleteUser(username);
					
					return true;	
				}
				else {
					return false; //User does not exist
					
				}
			}
			else {
				return false; //requester is not an administer
			}
		}
		else {
			return false; //requester does not exist
		}
	}
	
	/**
	 * creates the group with the specified name, assigning the user of the corresponding
	 * token to be its owner
	 * @param groupName	name of the group
	 * @param token	token of user creating group (group owner)
	 * @return	true on success, false on failure
	 */
	private boolean createGroup(String groupName, UserToken token) {
		String requester = token.getSubject();
		
		// Check if group does not exist
		// this assumes all group names must be unique, regardless of owner
		if(!my_gs.groupList.checkGroup(groupName)){
			if(my_gs.userList.checkUser(requester)){		
				my_gs.groupList.createGroup(groupName, requester);
				my_gs.groupList.addMember(groupName, requester);
				my_gs.userList.addGroup(requester, groupName);
				my_gs.userList.addOwnership(requester, groupName);
				return true;
			}
			return false;
		}
		else {
			return false; //group exists
		}
	}
	
	/**
	 * Deletes the specified group, as requested by the given user's token
	 * @param groupName	group to be deleted
	 * @param token	token of user requesting group deletion
	 * @return	true on success, false on failure
	 */
	private boolean deleteGroup(String groupName, UserToken token) {
		String requester = token.getSubject();
		
		if(groupName.equals("ADMIN"))
			return false;

		// check if group exists
		if(my_gs.groupList.checkGroup(groupName)){
			// check if requester is the group's owner
			if(my_gs.groupList.getGroupOwner(groupName).equals(requester)) {
				// delete group from users
				for(String user : my_gs.groupList.getGroupUsers(groupName)) {
					my_gs.userList.removeGroup(user, groupName);
				}
				// delete the group
				my_gs.groupList.deleteGroup(groupName);
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Lists the members in the specified group
	 * @param groupName group to list the members of
	 * @param token token of the user requesting the list
	 * @return List of strings on success, null on failure
	 */
	private List<String> listMembers(String groupName, UserToken token)
	{
		//Get the requester
		String requester = token.getSubject();
		// Does the requester exist?
		if (my_gs.userList.checkUser(requester)){
			// Get the groups the requester belongs to
			ArrayList<String> groups = my_gs.userList.getUserGroups(requester);

			// is the user authorized to be in this group?
			// check requester is the owner of the group
			if (groups.contains(groupName) && my_gs.groupList.getGroupOwner(groupName).equals(requester)){
				// get the members of this group
				return my_gs.groupList.getGroupUsers(groupName);
			}
			// The user is not authorized to see this group
			else {
				return null;
			}
		}
		// The requester doesn't exist
		else {
			return null;
		}
	}

	/**
	 * Add specified user to specified group
	 * @param userName user to be added to the group
	 * @param groupName group for the user to be added to
	 * @param token token of user requesting the addition
	 * @return true in success, false on failure
	 */
	private boolean addUserToGroup(String userName, String groupName, UserToken token)
	{
		String requester = token.getSubject();
		if (my_gs.userList.checkUser(requester)){
			ArrayList<String> owns = my_gs.userList.getUserOwnership(requester);
			if (owns.contains(groupName)){
				if (my_gs.userList.checkUser(userName)){
					ArrayList<String> users_in_group = my_gs.groupList.getGroupUsers(groupName);
					if (!users_in_group.contains(userName)){
						//Add user to group
						my_gs.groupList.addMember(groupName, userName);
						// add group to user
						my_gs.userList.addGroup(userName, groupName);
						return true;
					}
					else { // User is already in the group
						return false;
					}
				}
				else { // user to be added doesn't exist
					return false;
				}
			}
			else { // requester doesn't own the group
				return false;
			}
		}
		else { // requester doesn't exist
			return false;
		}
	}

	/**
	 * Delete specified user to specified group
	 * @param userName user to be deleted from the group
	 * @param groupName group for the user to be removed from
	 * @param token token of user requesting the deletion
	 * @return true in success, false on failure
	 */
	private boolean deleteUserFromGroup(String userName, String groupName, UserToken token)
	{
		String requester = token.getSubject();
		if (my_gs.userList.checkUser(requester)){
			ArrayList<String> owns = my_gs.userList.getUserOwnership(requester);
			if (owns.contains(groupName)){
				if (my_gs.userList.checkUser(userName)){
					ArrayList<String> users_in_group = my_gs.groupList.getGroupUsers(groupName);
					if (users_in_group.contains(userName)){
						// remove user from group
						my_gs.groupList.removeMember(groupName, userName);
						// remove group from user
						my_gs.userList.removeGroup(userName, groupName);
						return true;
					}
					else { // User is not in the group
						return false;
					}
				}
				else { // user to be added doesn't exist
					return false;
				}
			}
			else { // requester doesn't own the group
				return false;
			}
		}
		else { // requester doesn't exist
			return false;
		}
	}

	private boolean setRSAKey(String user, PublicKey key) {
		my_gs.userList.setPublicKey(user, key);
		return true;
	}

	private PublicKey getUserPublicKey(String user) {
		if (!my_gs.userList.checkUser(user)) {
			return null;
		}
		return my_gs.userList.getPublicKey(user);
	}

	/**
	 * return the group meta-data for all of the user's groups
	 * @param token	token
	 * @return	arraylist of groups and their meta-data
	 */
	private ArrayList<GroupMetadata> retrieveGroupsMetadata(UserToken token) {
		ArrayList<GroupMetadata> uGroupMetadata = new ArrayList<GroupMetadata>();
		String requester = token.getSubject();
		// check user exists
		if(my_gs.userList.checkUser(requester)) {
			for(String group : token.getGroups()){
				if(my_gs.groupList.getGroupMetadata(group) == null) {
					return null;
				}
				uGroupMetadata.add(my_gs.groupList.getGroupMetadata(group));
			}
			return uGroupMetadata;
		}
		return null;
	}


	//unused password-related elseifs
	// Client wishes to establish a shared symmetric secret key
				/*if(message.getMessage().equals("SESSIONKEY")) {
					// Retrieve Client's public key
					PublicKey clientPK = (PublicKey)message.getObjContents().get(0);
					KeyPair keypair = null;
					KeyAgreement keyAgreement = null;
					// generate secret key and send back public key
					try {
						keypair = DiffieHellman.genKeyPair();
						keyAgreement = DiffieHellman.genKeyAgreement(keypair);
						sessionKey = DiffieHellman.generateSecretKey(clientPK, keyAgreement);
						response = new Envelope("OK");
						response.addObject(keypair.getPublic());
						output.writeObject(response);
						isSecureConnection = true;
					} catch(Exception e) {
						e.printStackTrace();
						response = new Envelope("FAIL");
						response.addObject(response);
						output.writeObject(response);
					}
				}*/
	/*else if(message.getMessage().equals("LOGIN") 
							&& isSecureConnection) {

					if (message.getObjContents().size() < 2)
					{
				 		innerResponse = new Envelope("FAIL");
				 	}
				 	else
				 	{
				 		innerResponse = new Envelope("FAIL");
				 		if (message.getObjContents().get(0) != null)
				 		{
				 			if (message.getObjContents().get(1) != null)
							{
								innerResponse = new Envelope("FAIL");
								String user = (String)message.getObjContents().get(0);
								String password = (String)message.getObjContents().get(1);
								if (checkUser(user, password)) {
									isAuthenticated = true;
									if (checkFlag(username)) {
										innerResponse = new Envelope("CHANGEPASSWORD");
									}
									else {
										innerResponse = new Envelope("OK");
									}
								}
								else {
									innerResponse = new Envelope("FAIL");
								}
 							}
				 		}
					}
					response = buildSuper(innerResponse);
					output.writeObject(response);
				}
				else if (message.getMessage().equals("CHANGEPASSWORD") 
							&& isSecureConnection 
							&& isAuthenticated) {
					if (message.getObjContents().size() < 1) {
						innerResponse = new Envelope("FAIL");
					}
					else {
						innerResponse = new Envelope("FAIL");
						if (message.getObjContents().get(0) != null) {
							String password = (String)message.getObjContents().get(0);
							if (setPassword(username, password)) {
								innerResponse = new Envelope("OK");
							}
						}
					}
					response = Envelope.buildSuper(innerResponse, sessionKey);
					output.writeObject(response);
				}*/


	/*

	// More password-related methods
	
	private boolean setPassword(String user, String password) {
		BigInteger salt = Passwords.generateSalt();
		my_gs.userList.setSalt(user, salt);
		byte[] hashword = Passwords.generatePasswordHash(password, salt);
		my_gs.userList.setPassword(user, hashword);
		my_gs.userList.setNewPassword(user, false);
		return true;
	}
	
		private boolean checkUser(String user, String pwd) {
		if (my_gs.userList.checkUser(user) == false) {
			return false;
		}
		BigInteger salt = my_gs.userList.getSalt(user);
		byte[] password = Passwords.generatePasswordHash(pwd, salt);
		return my_gs.userList.checkPassword(user, password);
	}

	private boolean checkFlag(String user) {
		return my_gs.userList.getNewPassword(user);
	}


	*/
}