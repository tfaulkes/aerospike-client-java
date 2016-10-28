/*
 * Copyright 2012-2016 Aerospike, Inc.
 *
 * Portions may be licensed to Aerospike, Inc. under one or more contributor
 * license agreements WHICH ARE COMPATIBLE WITH THE APACHE LICENSE, VERSION 2.0.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.aerospike.test.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Info;
import com.aerospike.client.Log;
import com.aerospike.client.Log.Level;
import com.aerospike.client.cluster.Node;
import com.aerospike.client.policy.TlsPolicy;
import com.aerospike.client.util.Util;

public class Args {
	public static Args Instance = new Args();
	
	public String host;
	public int port;
	public String user;
	public String password;
	public String namespace;
	public String set;
	public TlsPolicy tlsPolicy;
	public boolean hasUdf;
	public boolean hasMap;
	public boolean singleBin;
	public boolean hasLargeDataTypes;
	
	public Args() {
		host = "127.0.0.1";
		port = 3000;
		namespace = "test";
		set = "test";

		String argString = System.getProperty("args");
		
		if (argString == null) {
			return;
		}
	
		try {
			String[] args = argString.split(" ");
			
			Options options = new Options();
			options.addOption("h", "host", true,
					"List of seed hosts in format:\n" +
					"hostname1[:tlsname][:port1],...\n" +
					"The tlsname is only used when connecting with a secure TLS enabled server. " +
					"If the port is not specified, the default port is used.\n" +
					"IPv6 addresses must be enclosed in square brackets.\n" +
					"Default: localhost\n" + 
					"Examples:\n" + 
					"host1\n" + 
					"host1:3000,host2:3000\n" + 
					"192.168.1.10:cert1:3000,[2001::1111]:cert2:3000\n" 
					);
			options.addOption("p", "port", true, "Server default port (default: 3000)");
			options.addOption("U", "user", true, "User name");
			options.addOption("P", "password", true, "Password");
			options.addOption("n", "namespace", true, "Namespace (default: test)");
			options.addOption("s", "set", true, "Set name. Use 'empty' for empty set (default: demoset)");
			options.addOption("tls", "tlsEnable", false, "Use TLS/SSL sockets");
			options.addOption("tp", "tlsProtocols", true, 
					"Allow TLS protocols\n" +
					"Values:  SSLv3,TLSv1,TLSv1.1,TLSv1.2 separated by comma\n" +
					"Default: TLSv1.2"
					);
			options.addOption("tlsCiphers", "tlsCipherSuite", true, 
					"Allow TLS cipher suites\n" +
					"Values:  cipher names defined by JVM separated by comma\n" +
					"Default: null (default cipher list provided by JVM)"
					);
			options.addOption("tr", "tlsRevoke", true, 
					"Revoke certificates identified by their serial number\n" +
					"Values:  serial numbers separated by comma\n" +
					"Default: null (Do not revoke certificates)"
					);
			options.addOption("te", "tlsEncryptOnly", false, 
					"Enable TLS encryption and disable TLS certificate validation"
					);
			options.addOption("d", "debug", false, "Run in debug mode.");
			options.addOption("u", "usage", false, "Print usage.");

			CommandLineParser parser = new PosixParser();
			CommandLine cl = parser.parse(options, args, false);

			if (cl.hasOption("u")) {
				logUsage(options);
				throw new AerospikeException("Terminate after displaying usage");
			}
			
			host = cl.getOptionValue("h", host);
			String portString = cl.getOptionValue("p", "3000");
			port = Integer.parseInt(portString);
			namespace = cl.getOptionValue("n", namespace);
			set = cl.getOptionValue("s", "test");

			if (set.equals("empty")) {
				set = "";
			}
			
	        if (cl.hasOption("tls")) {
	        	tlsPolicy = new TlsPolicy();
	        	
				if (cl.hasOption("tp")) {
					String s = cl.getOptionValue("tp", "");
					tlsPolicy.protocols = s.split(",");
				}
				
				if (cl.hasOption("tlsCiphers")) {
					String s = cl.getOptionValue("tlsCiphers", "");
					tlsPolicy.ciphers = s.split(",");
				}
				
				if (cl.hasOption("tr")) {
					String s = cl.getOptionValue("tr", "");
					tlsPolicy.revokeCertificates = Util.toBigIntegerArray(s);
				}
				
				if (cl.hasOption("te")) {
					tlsPolicy.encryptOnly = true;
				}
	        }

	        user = cl.getOptionValue("U");
			password = cl.getOptionValue("P");
			
			if (user != null && password == null) {
				java.io.Console console = System.console();
				
				if (console != null) {
					char[] pass = console.readPassword("Enter password:");
					
					if (pass != null) {
						password = new String(pass);
					}
				}
			}
						
			if (cl.hasOption("d")) {				
				Log.setLevel(Level.DEBUG);
			}
		}
		catch (Exception ex) {
			throw new AerospikeException("Failed to parse args: " + argString);
		}
	}
	
	private static void logUsage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		String syntax = "mvn test [-Dargs='<options>']";
		formatter.printHelp(pw, 100, syntax, "options:", options, 0, 2, null);
		System.out.println(sw.toString());
	}
	
	/**
	 * Some database calls need to know how the server is configured.
	 */
	public void setServerSpecific(AerospikeClient client) {
		Node node = client.getNodes()[0];
		String featuresFilter = "features";
		String namespaceFilter = "namespace/" + namespace;
		Map<String,String> tokens = Info.request(null, node, featuresFilter, namespaceFilter);

		String features = tokens.get(featuresFilter);
		hasUdf = false;
		hasMap = false;
		
		if (features != null) {
			String[] list = features.split(";");
			
			for (String s : list) {
				if (s.equals("udf")) {
					hasUdf = true;
					break;
				}
				else if (s.equals("cdt-map")) {
					hasMap = true;
					break;
				}
			}
		}
		
		String namespaceTokens = tokens.get(namespaceFilter);
		
		if (namespaceTokens == null) {
			throw new AerospikeException(String.format(
				"Failed to get namespace info: host=%s port=%d namespace=%s",
				host, port, namespace));
		}

		singleBin = parseBoolean(namespaceTokens, "single-bin");
		hasLargeDataTypes = parseBoolean(namespaceTokens, "ldt-enabled");
	}
	
	private static boolean parseBoolean(String namespaceTokens, String name) {
		String search = name + '=';
		int begin = namespaceTokens.indexOf(search);

		if (begin < 0) {
			return false;
		}

		begin += search.length();
		int end = namespaceTokens.indexOf(';', begin);

		if (end < 0) {
			end = namespaceTokens.length();
		}

		String value = namespaceTokens.substring(begin, end);
		return Boolean.parseBoolean(value);
	}
	
	public String getBinName(String name) {
		// Single bin servers don't need a bin name.
		return singleBin ? "" : name;
	}

	public boolean validateLDT() {
		if (! hasLargeDataTypes) {
			System.out.println("Skip test because LDT not enabled on server");
			return false;
		}
		return true;
	}
	
	public boolean validateMap() {
		if (! hasMap) {
			System.out.println("Skip test because cdt-map not enabled on server");
			return false;
		}
		return true;
	}
}
