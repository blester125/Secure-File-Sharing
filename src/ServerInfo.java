import java.io.Serializable;

public class ServerInfo implements Serializable {
	private String hostname;
	private String port;
	private static final long serialVersionUID = 2674987946519874951L;

	
	public ServerInfo(String hostname, String port) {
		this.hostname = hostname;
		this.port = port;
	}
	
	public String getHostname() {
		return this.hostname;
	}
	public String getPort() {
		return this.getPort();
	}
	@Override
	public int hashCode() {
		return (this.hostname + this.port).hashCode();
	}
}