package com.oohlalog.commons;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

public class StatsPayload {
	static final String PAYLOAD_METRICS = "metrics";
	static final String PAYLOAD_COUNTERS = "counters";

	// Config
	private String authToken = null;
	private String host = null;
	private String hostName = null;
	private String path = null;
	private String agent = null;
	private Map<String, Double> metrics = null;
	private int port;
	private boolean secure = false;
	private boolean debug = true;

	private Map<String, Object> counters = null;

	/**
	 * lock constructor to require usage of the builder
	 */
	private StatsPayload() {
		super();
	}


	/**
	 * Serialize payload into a transferrable dataformat (json)
	 * @return
	 */
	public String serialize( ) {
		StatsPayload pl = this;
		Map<String,Object> payload = new HashMap<String,Object>();

		// Add logs
		payload.put( PAYLOAD_METRICS, pl.metrics);
		payload.put("host", this.host);

		return new Gson().toJson( payload );
	}


	/**
	 * Write this payload to remote service
	 * @param pl
	 * @throws RuntimeException
	 */
	public static void send( StatsPayload pl ) throws RuntimeException {
		OutputStream os = null;
	    BufferedReader rd  = null;
	    StringBuilder sb = null;
	    String line = null;
	    HttpURLConnection con = null;
		try {
			if (pl.getDebug()) System.out.println("Serializing: " + pl.toString());
			// Serialize payload into json
			String json = pl.serialize();

			if (pl.getDebug()) System.out.println( ">>>>>>>>>>>Payload: " + pl.toString() );

			// Create connection to oohlalog server
			URL url = new URL( (pl.getSecure() ? "https" : "http"), pl.getHost(), pl.getPort(), pl.getPath()+"?apiKey="+pl.getAuthToken() );

			if (pl.getDebug()) System.out.println( ">>>>>>>>>>>Submitting to: " + url.toString() );
			if (pl.getDebug()) System.out.println( ">>>>>>>>>>>JSON: " + json.toString() );
			con = (HttpURLConnection) url.openConnection();
			con.setDoOutput(true);
			con.setDoInput(true);
			con.setInstanceFollowRedirects(false);
			con.setRequestMethod("POST");
			con.setRequestProperty("Content-Type", "application/json");
			con.setRequestProperty("Content-Length", "" + json.getBytes().length);
			con.setUseCaches(false);

			// Get output stream and write json
			os = con.getOutputStream();
			os.write( json.getBytes() );

			rd  = new BufferedReader(new InputStreamReader(con.getInputStream()));
			sb = new StringBuilder();

			while ((line = rd.readLine()) != null){
			  sb.append(line + '\n');
			}
			if (pl.getDebug()) System.out.println( ">>>>>>>>>>>Received: " + sb.toString() );

		}
		catch ( Throwable t ) {
			t.printStackTrace();
		}
		finally {
			if ( os != null ) {
				try {
				  con.disconnect();
					os.flush();
					os.close();
					con = null;
				}
				catch ( Throwable t ) {
					// swallow
				}
			}
		}
	}

	public String getAuthToken() {
		return authToken;
	}

	public void setAuthToken(String authToken) {
		this.authToken = authToken;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getHostName() {
		return hostName;
	}

	public void setHostName(String hostName) {
		this.hostName = hostName;
	}


	public Map<String, Object> getCounters() {
		return counters;
	}

	public void setCounters(Map<String, Object> counters) {
		this.counters = counters;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getAgent() {
		return agent;
	}

	public void setAgent(String agent) {
		this.agent = agent;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public boolean getSecure() {
		return secure;
	}

	public void setSecure(boolean secure) {
		this.secure = secure;
	}

	public boolean getDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer();
		sb.append("Payload");
		sb.append("{authToken='").append(authToken).append('\'');
		sb.append(", host='").append(host).append('\'');
		sb.append(", path='").append(path).append('\'');
		sb.append(", agent='").append(agent).append('\'');
		sb.append(", secure='").append(secure).append('\'');
		sb.append(", debug='").append(debug).append('\'');
		sb.append(", port=").append(port);
		sb.append('}');
		return sb.toString();
	}

	/**
	 * Builder pattern helper
	 */
	public static class Builder {
		private String authToken = null;
		private String host = null;
		private String hostName = null;
		private String path = null;
		private String agent = "log4j";
		private int port = 80;
		private boolean secure = false;
		private boolean debug = false;
		private Map<String, Double> metrics = null;
		private Map<String, Object> counters = null;

		public Builder() {}
		public StatsPayload build() {
			StatsPayload pl = new StatsPayload();
			pl.authToken = this.authToken;
			pl.host = this.host;
			pl.hostName = this.hostName;
			pl.metrics = this.metrics;
			pl.counters = this.counters;
			pl.port = this.port;
			pl.agent = this.agent;
			pl.path = this.path;
			pl.secure = this.secure;
			pl.debug = this.debug;
			return pl;
		}

		public Builder authToken( String token ) {
			this.authToken = token;
			return this;
		}

		public Builder host( String host ) {
			this.host = host;
			return this;
		}

		public Builder path( String path ) {
			this.path = path;
			return this;
		}

		public Builder agent( String agent ) {
			this.agent = agent;
			return this;
		}

		public Builder hostName( String hostName ) {
			this.hostName = hostName;
			return this;
		}

		public Builder metrics( Map<String, Double> metrics ) {
			this.metrics = metrics;
			return this;
		}

		public Builder counters( Map<String,Object> counters ) {
			this.counters = counters;
			return this;
		}

		public Builder port( int port ) {
			this.port = port;
			return this;
		}
		
		public Builder secure( boolean secure ) {
			this.secure = secure;
			return this;
		}

		public Builder debug( boolean debug ) {
			this.debug = debug;
			return this;
		}
	}
}
