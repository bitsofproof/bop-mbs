package com.bitsofproof.merchant.client;

import java.io.File;
import java.io.IOException;
import java.security.Security;
import java.util.UUID;

import javax.jms.ConnectionFactory;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.fusesource.stomp.jms.StompJmsConnectionFactory;

import com.bitsofproof.supernode.api.AddressConverter;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.BaseAccountManager;
import com.bitsofproof.supernode.api.ExtendedKeyAccountManager;
import com.bitsofproof.supernode.api.FileWallet;
import com.bitsofproof.supernode.api.JMSServerConnector;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.common.ValidationException;

public class KeyTool
{
	private static final String ACCOUNT = "BMBS";
	private static final long FEE = 10000;

	private static int addressFlag;

	private static ConnectionFactory getConnectionFactory (String server, String user, String password)
	{
		StompJmsConnectionFactory connectionFactory = new StompJmsConnectionFactory ();
		connectionFactory.setBrokerURI (server);
		connectionFactory.setUsername (user);
		connectionFactory.setPassword (password);
		return connectionFactory;
	}

	private static BCSAPI getServer (ConnectionFactory connectionFactory)
	{
		JMSServerConnector connector = new JMSServerConnector ();
		connector.setConnectionFactory (connectionFactory);
		connector.setClientId (UUID.randomUUID ().toString ());
		connector.init ();
		return connector;
	}

	public static void main (String[] args)
	{
		final CommandLineParser parser = new GnuParser ();
		final Options gnuOptions = new Options ();
		gnuOptions.addOption ("s", "server", true, "Server URL");
		gnuOptions.addOption ("u", "user", true, "User");
		gnuOptions.addOption ("p", "password", true, "Password");
		gnuOptions.addOption ("n", "newkey", true, "new key");
		gnuOptions.addOption ("b", "public", true, "public key export");
		gnuOptions.addOption ("v", "private", true, "private key export");
		gnuOptions.addOption ("w", "sweep", true, "sweep to address");
		gnuOptions.addOption ("a", "address", true, "address to sweep to");
		gnuOptions.addOption ("l", "lookahead", true, "number of addresses to look ahead while sweeping (default 100)");

		System.out.println ("BOP Merchant Server Client 1.0 (c) 2013 bits of proof zrt.");
		Security.addProvider (new BouncyCastleProvider ());
		CommandLine cl = null;
		String url = null;
		String user = null;
		String password = null;
		String newkey = null;
		String priv = null;
		String pub = null;
		String sweep = null;
		String address = null;
		String lookahead = null;
		try
		{
			cl = parser.parse (gnuOptions, args);
			url = cl.getOptionValue ('s');
			user = cl.getOptionValue ('u');
			password = cl.getOptionValue ('p');
			newkey = cl.getOptionValue ('n');
			pub = cl.getOptionValue ('b');
			priv = cl.getOptionValue ('v');
			sweep = cl.getOptionValue ('w');
			address = cl.getOptionValue ('a');
			lookahead = cl.getOptionValue ('l');
		}
		catch ( org.apache.commons.cli.ParseException e )
		{
			e.printStackTrace ();
			System.exit (1);
		}
		if ( newkey != null )
		{
			File keyfile = new File (newkey);
			if ( keyfile.exists () )
			{
				System.err.println ("key file " + newkey + " already exists.");
				System.exit (1);
			}
			System.console ().printf ("Enter passphrase: ");
			String passphrase = System.console ().readLine ();
			FileWallet w = new FileWallet (newkey);
			w.init (passphrase);
			try
			{
				w.unlock (passphrase);
				w.createAccountManager (ACCOUNT);
				w.lock ();
				w.persist ();
			}
			catch ( ValidationException e )
			{
				System.err.println ("Unexpected: " + e.getMessage ());
				System.exit (1);
			}
			catch ( IOException e )
			{
				System.err.println ("Can not store key file " + newkey + " " + e.getMessage ());
				System.exit (1);
			}
		}
		else if ( pub != null )
		{
			try
			{
				FileWallet w = FileWallet.read (pub);
				ExtendedKeyAccountManager am = (ExtendedKeyAccountManager) w.getAccountManager (ACCOUNT);
				System.out.println (am.getMaster ().serialize (true));
			}
			catch ( IOException e )
			{
				System.err.println ("Can not read key file " + pub + " " + e.getMessage ());
				System.exit (1);
			}
			catch ( ValidationException e )
			{
				System.err.println ("Can not read key file " + pub + " " + e.getMessage ());
				System.exit (1);
			}
		}
		else if ( priv != null )
		{
			try
			{
				FileWallet w = FileWallet.read (priv);
				ExtendedKeyAccountManager am = (ExtendedKeyAccountManager) w.getAccountManager (ACCOUNT);
				System.console ().printf ("Enter passphrase: ");
				String passphrase = System.console ().readLine ();
				w.unlock (passphrase);
				System.out.println (am.getMaster ().serialize (true));
				w.lock ();
			}
			catch ( IOException e )
			{
				System.err.println ("Can not read key file " + priv + " " + e.getMessage ());
				System.exit (1);
			}
			catch ( ValidationException e )
			{
				System.err.println ("Can not read key file " + priv + " " + e.getMessage ());
				System.exit (1);
			}
		}
		if ( sweep != null )
		{
			if ( url == null || user == null || password == null || address == null )
			{
				System.err.println ("Need -s server -u user -p password --address address");
				System.exit (1);
			}
			try
			{
				BCSAPI api = getServer (getConnectionFactory (url, user, password));
				api.isProduction ();
				FileWallet w = FileWallet.read (sweep);
				ExtendedKeyAccountManager account = (ExtendedKeyAccountManager) w.getAccountManager (ACCOUNT);

				int lookAhead = 100;
				if ( lookahead != null )
				{
					lookAhead = Integer.valueOf (lookahead);
				}

				account.sync (api, lookAhead);
				if ( account.getChange () != 0 || account.getReceiving () != 0 )
				{
					System.err.println ("There are unconfirmed transactions pending with this key");
					System.exit (1);
				}
				long total = account.getConfirmed ();
				if ( total != 0 )
				{
					System.console ().printf ("Enter passphrase: ");
					String passphrase = System.console ().readLine ();
					w.unlock (passphrase);
					Transaction transaction = account.pay (AddressConverter.fromSatoshiStyle (address, api.isProduction () ? 0x0 : 0x6f), total - FEE, FEE);
					long fee = BaseAccountManager.estimateFee (transaction);
					transaction = account.pay (AddressConverter.fromSatoshiStyle (address, api.isProduction () ? 0x0 : 0x6f), total - fee, fee);
					w.lock ();
					System.console ().printf (
							"You are about to send " + (total - fee) + " satoshis (fee: " + fee + ") to " + address + "\nType yes to continue: ");
					String yes = System.console ().readLine ();
					if ( yes.equals ("yes") )
					{
						api.sendTransaction (transaction);
						System.out.println (transaction.getHash ());
					}
					else
					{
						System.out.println ("Nothing happened.");
					}
				}
				else
				{
					System.out.println ("There are no funds available to sweep.");
				}
			}
			catch ( IOException e )
			{
				System.err.println ("Can not read key file " + priv + " " + e.getMessage ());
				System.exit (1);
			}
			catch ( ValidationException e )
			{
				System.err.println ("Can not read key file " + priv + " " + e.getMessage ());
				System.exit (1);
			}
			catch ( BCSAPIException e )
			{
				System.err.println ("Can not sweep key file " + priv + " " + e.getMessage ());
				System.exit (1);
			}
		}
		System.exit (0);
	}
}
