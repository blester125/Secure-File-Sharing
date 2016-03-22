import java.security.Key;
import java.util.*;

import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

  public class GroupList implements java.io.Serializable {
    private static final long serialVersionUID = 8711454914678528003L;
    private Hashtable<String, Group> groups = new Hashtable<String, Group>();
    
    public synchronized void createGroup(String groupName, String username) {
      Group newGroup = new Group(username);
      groups.put(groupName, newGroup);
    } 

    public synchronized void deleteGroup(String groupName) {
    	groups.remove(groupName);
    } 

    public synchronized boolean checkGroup(String groupName) {
      if (groups.containsKey(groupName)) {
        return true;
      }
      return false;
    } 

    public synchronized ArrayList<String> getGroupUsers(String groupName) {
      return groups.get(groupName).getUsers();
    }

    public synchronized String getGroupOwner(String groupName) {
      return groups.get(groupName).getOwner();
    }

    public synchronized void addMember(String groupName, String userName) {
      groups.get(groupName).addUser(userName);
    }

    public synchronized void removeMember(String groupName, String userName) {
      groups.get(groupName).removeUser(userName);
      groups.get(groupName).evolveKey();
    }
    
    /**
     * returns the meta-data for a group (i.e. its keys
     * and associated data)
     * @param	groupname	name of the group
     * @return	GroupMetadata
     */
    public synchronized GroupMetadata getGroupMetadata(String groupname) {
    	return new GroupMetadata(groups.get(groupname).getCurrentKey(), groups.get(groupname).getCurrentKeyVer(), 
    			groups.get(groupname).getOldKeys());
    }

  class Group implements java.io.Serializable {
    private static final long serialVersionUID = -7700097447400932609L;
    private ArrayList<String> users;
    private String owner;
    private Key currentRootKey;
    private Key currentKey;
    private int currentKeyVer;
    private ArrayList<Key> oldKeys;
    private static final int maxKeyVersions = 99;

    public Group(String creator) {
      users = new ArrayList<String>();
      this.owner = creator;
    }

    public ArrayList<String> getUsers() {
      return users;
    }

    public String getOwner() {
      return owner;
    }
    
    public Key getCurrentKey() {
    	return currentKey;
    }

    public int getCurrentKeyVer() {
    	return currentKeyVer;
    }
    
    public ArrayList<Key> getOldKeys() {
    	return oldKeys;
    }
    
    public void addUser(String userName) {
      users.add(userName);
    }

    public void removeUser(String userName) {
      if (!users.isEmpty()) {
        if (users.contains(userName)) {
          users.remove(users.indexOf(userName));
        }
      }
    }
    
    public void evolveKey() {
    	// need to generate a new key
    	if(currentKeyVer == 0) {
    		oldKeys.add(currentKey);
    		KeyGenerator keyGen = null;
			try {
				keyGen = KeyGenerator.getInstance("AES");
			} catch (Exception e) {
				e.printStackTrace();
			}
    		keyGen.init(128);
    		currentRootKey = keyGen.generateKey();
    		currentKeyVer = maxKeyVersions;
    		byte[] keyBytes = currentKey.getEncoded();
    		for(int i = 0; i < currentKeyVer; i++) {
    			keyBytes = Hasher.hash(keyBytes);
    		}
    		currentKey = new SecretKeySpec(keyBytes, 0, 16, "AES");
    	}
    	// decrement key hashes by one
    	else {
    		currentKeyVer--;
    		byte[] keyBytes = currentRootKey.getEncoded();
    		for(int i = 0; i < currentKeyVer; i++) {
    			keyBytes = Hasher.hash(keyBytes);
    		}
    		currentKey = new SecretKeySpec(keyBytes, 0, 16, "AES");
    	}
    	
    }
  }
  
  class GroupMetadata implements java.io.Serializable {
	    private static final long serialVersionUID = -8960097447400932609L;
	    private Key currentKey;
	    private int currentKeyVer;
	    private ArrayList<Key> oldKeys;
	    
	    public GroupMetadata(Key currentKey, int currentKeyVer, ArrayList<Key> oldKeys) {
	    	this.currentKey = currentKey;
	    	this.currentKeyVer = currentKeyVer;
	    	this.oldKeys = oldKeys;
		}
	    
	    public Key getCurrentKey() {
	    	return currentKey;
	    }

	    public int getCurrentKeyVer() {
	    	return currentKeyVer;
	    }
	    
	    public ArrayList<Key> getOldKeys() {
	    	return oldKeys;
	    }
  }
}