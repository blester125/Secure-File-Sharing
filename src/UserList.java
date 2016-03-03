/* This list represents the users on the server */
import java.util.*;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.*;
import java.security.interfaces.*;

import javax.crypto.*;
import javax.crypto.spec.*;
import javax.crypto.interfaces.*;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class UserList implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7600343803563417992L;
	private Hashtable<String, User> list = new Hashtable<String, User>();
	
	public synchronized void addUser(String username)
	{
		User newUser = new User();
		list.put(username, newUser);
	}
	
	public synchronized void deleteUser(String username)
	{
		list.remove(username);
	}
		
	public synchronized boolean checkUser(String username)
	{
		if(list.containsKey(username))
		{
			return true;
		}
		else
		{
			return false;
		}
	}
		
	public synchronized ArrayList<String> getUserGroups(String username)
	{
		return list.get(username).getGroups();
	}
		
	public synchronized ArrayList<String> getUserOwnership(String username)
	{
		return list.get(username).getOwnership();
	}
	
	public synchronized void addGroup(String user, String groupname)
	{
		list.get(user).addGroup(groupname);
	}
	
	public synchronized void removeGroup(String user, String groupname)
	{
		list.get(user).removeGroup(groupname);
	}
	
	public synchronized void addOwnership(String user, String groupname)
	{
		list.get(user).addOwnership(groupname);
	}
	
	public synchronized void removeOwnership(String user, String groupname)
	{
		list.get(user).removeOwnership(groupname);
	}
	
	public synchronized void setPassword(String user, byte[] password) 
	{
		list.get(user).setPassword(password);
	}

	public synchronized boolean checkPassword(String user, byte[] password)
	{
		return list.get(user).checkPassword(password);
	}

	public synchronized void setSalt(String user, BigInteger salt)
	{
		list.get(user).setSalt(salt);
	}

	public synchronized BigInteger getSalt(String user) 
	{
		return list.get(user).getSalt();
	}

	public synchronized void setPublicKey(String user, PublicKey publicKey)
	{
		list.get(user).setPublicKey(publicKey);
	}

	public synchronized PublicKey getPublicKey(String user)
	{
		return list.get(user).getPublicKey();		
	}

	class User implements java.io.Serializable {

		/**
		 * 
		 */
		private static final long serialVersionUID = -6699986336399821598L;
		private ArrayList<String> groups;
		private ArrayList<String> ownership;
		private PublicKey publicKey;
		private BigInteger salt;
		private byte[] password;

		public User()
		{
			groups = new ArrayList<String>();
			ownership = new ArrayList<String>();
		}
		
		public ArrayList<String> getGroups()
		{
			return groups;
		}
		
		public ArrayList<String> getOwnership()
		{
			return ownership;
		}
		
		public void addGroup(String group)
		{
			groups.add(group);
		}
		
		public void removeGroup(String group)
		{
			if(!groups.isEmpty())
			{
				if(groups.contains(group))
				{
					groups.remove(groups.indexOf(group));
				}
			}
		}
		
		public void addOwnership(String group)
		{
			ownership.add(group);
		}
		
		public void removeOwnership(String group)
		{
			if(!ownership.isEmpty())
			{
				if(ownership.contains(group))
				{
					ownership.remove(ownership.indexOf(group));
				}
			}
		}
	
		public void setPassword(byte[] password) 
		{
			this.password = password;
		}	

		public boolean checkPassword(byte[] password) 
		{
			return MessageDigest.isEqual(this.password, password);
		} 

		public void setSalt(BigInteger s) 
		{
			salt = s;
		}

		public BigInteger getSalt() 
		{
			return salt;
		}

		public void setPublicKey(PublicKey publicKey) 
		{
			this.publicKey = publicKey;
		}

		public PublicKey getPublicKey() 
		{
			return publicKey;
		}
	}
}	
