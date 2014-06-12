package com.bitsofproof.merchant.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.bitsofproof.supernode.account.ExtendedKeyAccountManager;
import com.bitsofproof.supernode.api.Address;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionInput;
import com.bitsofproof.supernode.api.TransactionOutput;
import com.bitsofproof.supernode.common.ByteUtils;
import com.bitsofproof.supernode.common.ExtendedKey;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ScriptFormat;
import com.bitsofproof.supernode.common.ScriptFormat.Opcode;
import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.common.WireFormat;
import com.bitsofproof.supernode.misc.SimpleFileWallet;

public class KeyTool
{
	private static final String ACCOUNT = "BopShop";
	private static final String KEYFILE = "bopshop.key";

	private static final String SERVER_URL = "https://api.bitsofproof.com/mbs/1";

	private static final long DUST_LIMIT = 5430;
	private static final long KB_FEE = 1000;
	private static final long MINIMUM_FEE = 10000;
	private static final long MAXIMUM_FEE = 1000000;

	public static void main (String[] args)
	{
		final CommandLineParser parser = new GnuParser ();
		final Options gnuOptions = new Options ();
		gnuOptions.addOption ("u", "user", true, "user");
		gnuOptions.addOption ("p", "password", true, "password");
		gnuOptions.addOption ("n", "name", true, "name");
		gnuOptions.addOption ("e", "email", true, "email");
		gnuOptions.addOption ("r", "register", false, "create new key and account");
		gnuOptions.addOption ("b", "batch", false, "claim all cleared payment requests");
		gnuOptions.addOption ("a", "address", true, "address to forward the claimed amount");
		gnuOptions.addOption ("E", "export", false, "Export master private key");
		gnuOptions.addOption ("l", "late", true, "extract key for a late payment");

		System.out.println ("BOP Merchant Server Client 2.4 (c) 2013 bits of proof zrt.");
		Security.addProvider (new BouncyCastleProvider ());
		CommandLine cl = null;
		String user = null;
		String password = null;
		String name = null;
		String email = null;
		String address = null;
		String late = null;
		boolean batch = false;
		boolean export = false;
		boolean register = false;
		try
		{
			cl = parser.parse (gnuOptions, args);
			name = cl.getOptionValue ('n');
			email = cl.getOptionValue ('e');
			user = cl.getOptionValue ('u');
			password = cl.getOptionValue ('p');
			register = cl.hasOption ('r');
			address = cl.getOptionValue ('a');
			late = cl.getOptionValue ('l');
			batch = cl.hasOption ('b');
			export = cl.hasOption ('E');
		}
		catch ( org.apache.commons.cli.ParseException e )
		{
			e.printStackTrace ();
			System.exit (1);
		}
		if ( export )
		{
			if ( password == null )
			{
				System.err.println ("also support -p password");
				System.exit (1);
			}
			SimpleFileWallet w;
			try
			{
				w = SimpleFileWallet.read (KEYFILE);
				w.unlock (password);
				System.out.println ("Private key: " + w.getMaster ().serialize (true));
				w.lock ();
			}
			catch ( IOException e )
			{
				e.printStackTrace ();
				System.exit (1);
			}
			catch ( ValidationException e )
			{
				e.printStackTrace ();
				System.exit (1);
			}
		}
		else if ( late != null )
		{
			if ( password == null )
			{
				System.err.println ("also support -p password");
				System.exit (1);
			}
			SimpleFileWallet w;
			try
			{
				w = SimpleFileWallet.read (KEYFILE);
				w.unlock (password);
				ExtendedKey master = ((ExtendedKeyAccountManager) w.getAccountManager (ACCOUNT)).getMaster ();
				System.out.println ("Private key: " + master.getKey (Integer.valueOf (late)));
				w.lock ();
			}
			catch ( IOException e )
			{
				e.printStackTrace ();
				System.exit (1);
			}
			catch ( ValidationException e )
			{
				e.printStackTrace ();
				System.exit (1);
			}
		}
		else if ( register )
		{
			if ( name == null || email == null )
			{
				System.err.println ("also support -n name -e email");
				System.exit (1);
			}
			File keyfile = new File (KEYFILE);
			if ( keyfile.exists () )
			{
				System.err.println ("key file " + KEYFILE + " already exists.");
				System.exit (1);
			}
			if ( password == null )
			{
				byte[] newpass = new byte[10];
				new SecureRandom ().nextBytes (newpass);
				password = ByteUtils.toBase58 (newpass);
			}
			SimpleFileWallet w = new SimpleFileWallet (KEYFILE);
			w.init (password);
			try
			{
				w.unlock (password);
				ExtendedKeyAccountManager account = (ExtendedKeyAccountManager) w.createAccountManager (ACCOUNT);
				w.lock ();
				w.persist ();

				String publicKey = account.getMaster ().serialize (true);

				HttpClient httpclient = new DefaultHttpClient ();
				HttpPost post = new HttpPost (SERVER_URL + "/account");
				post.setHeader ("Content-Type", "application/json");
				JSONObject r = new JSONObject ();
				r.put ("name", name);
				r.put ("password", password);
				r.put ("publicKey", publicKey);
				r.put ("email", email);
				post.setEntity (new StringEntity (r.toString ()));
				HttpResponse response = httpclient.execute (post);
				if ( response.getEntity () != null )
				{
					BufferedReader in = new BufferedReader (new InputStreamReader (response.getEntity ().getContent (), "UTF-8"));
					StringWriter writer = new StringWriter ();
					String line;
					while ( (line = in.readLine ()) != null )
					{
						writer.write (line);
					}
					JSONObject reply = new JSONObject (writer.toString ());
					System.out.println ("user id: " + reply.getString ("customerId"));
					System.out.println ("password: " + password);
					System.out.println ("Google authenticator secret: " + reply.getString ("secret"));
				}
			}
			catch ( ValidationException e )
			{
				System.err.println ("Unexpected: " + e.getMessage ());
				System.exit (1);
			}
			catch ( JSONException e )
			{
				e.printStackTrace ();
				System.exit (1);
			}
			catch ( IOException e )
			{
				System.err.println ("Can not store key file " + KEYFILE + " " + e.getMessage ());
				System.exit (1);
			}
		}
		else if ( batch )
		{
			if ( user == null || password == null || address == null )
			{
				System.err.println ("Provide -u user -p password -a address");
				System.exit (1);
			}
			try
			{
				HttpClient httpclient = new DefaultHttpClient ();
				HttpGet get = new HttpGet (SERVER_URL + "/paymentRequest?state=CLEARED&entriesPerPage=1000");
				String authorizationString = "Basic " + new String (Base64.encodeBase64 ((user + ":" + password).getBytes (), false));
				get.setHeader ("Authorization", authorizationString);
				HttpResponse response = httpclient.execute (get);
				String output = null;
				if ( response.getEntity () != null )
				{
					BufferedReader in = new BufferedReader (new InputStreamReader (response.getEntity ().getContent (), "UTF-8"));
					StringWriter writer = new StringWriter ();
					String line;
					while ( (line = in.readLine ()) != null )
					{
						writer.write (line);
					}
					output = writer.toString ();
				}
				JSONObject prlist = new JSONObject (output);
				List<JSONObject> itemList = new ArrayList<JSONObject> ();
				if ( !prlist.has ("_embedded") )
				{
					System.err.println ("Nothing to claim.");
					System.exit (0);
				}
				JSONArray items = prlist.getJSONObject ("_embedded").optJSONArray ("item");
				if ( items != null )
				{
					for ( int i = 0; i < items.length (); ++i )
					{
						itemList.add (items.getJSONObject (i));
					}
				}
				else
				{
					itemList.add (prlist.getJSONObject ("_embedded").getJSONObject ("item"));
				}

				List<JSONObject> prs = new ArrayList<JSONObject> ();
				for ( JSONObject item : itemList )
				{
					get = new HttpGet (item.getJSONObject ("_links").getJSONObject ("self").getString ("href"));
					get.setHeader ("Authorization", authorizationString);
					response = httpclient.execute (get);
					output = null;
					if ( response.getEntity () != null )
					{
						BufferedReader in = new BufferedReader (new InputStreamReader (response.getEntity ().getContent (), "UTF-8"));
						StringWriter writer = new StringWriter ();
						String line;
						while ( (line = in.readLine ()) != null )
						{
							writer.write (line);
						}
						output = writer.toString ();
						prs.add (new JSONObject (output));
					}
				}

				long paid = 0;

				for ( JSONObject pr : prs )
				{
					paid += pr.getLong ("paid");
				}
				long fee = Math.max (Math.min (MAXIMUM_FEE, (prs.size () / 4 + 1) * KB_FEE), MINIMUM_FEE);
				if ( paid == 0 || (paid - fee) <= 0 )
				{
					System.err.println ("Nothing left to claim.");
					System.exit (0);
				}

				Transaction t = new Transaction ();
				List<TransactionInput> inputs = new ArrayList<TransactionInput> ();
				t.setInputs (inputs);
				List<TransactionOutput> outputs = new ArrayList<TransactionOutput> ();
				t.setOutputs (outputs);

				TransactionOutput o = new TransactionOutput ();
				o.setValue (paid - fee);
				outputs.add (o);
				writeOutputScript (address, o);

				SimpleFileWallet w = SimpleFileWallet.read (KEYFILE);
				w.unlock (password);
				ExtendedKey master = ((ExtendedKeyAccountManager) w.getAccountManager (ACCOUNT)).getMaster ();
				List<Key> signingKeys = new ArrayList<Key> ();
				for ( JSONObject pr : prs )
				{
					JSONArray events = pr.getJSONArray ("events");
					Key key;
					if ( pr.has ("keyNumber") )
					{
						key = master.getChild (pr.getInt ("child")).getKey (pr.getInt ("keyNumber"));
					}
					else
					{
						key = master.getKey (pr.getInt ("child"));
					}
					for ( int i = 0; i < events.length (); ++i )
					{
						JSONObject event = events.getJSONObject (i);
						if ( event.getString ("eventType").equals ("TRANSACTION") )
						{
							TransactionInput in = new TransactionInput ();
							in.setSourceHash (event.getString ("txHash"));
							in.setIx (event.getInt ("ix"));
							in.setScript (ByteUtils.fromHex (event.getString ("script")));

							boolean duplicate = false;
							for ( TransactionInput c : inputs )
							{
								if ( c.getSourceHash ().equals (in.getSourceHash ()) && c.getIx () == in.getIx () )
								{
									duplicate = true;
								}
							}
							if ( !duplicate )
							{
								inputs.add (in);
								signingKeys.add (key);
							}
						}
					}
				}
				int j = 0;
				for ( TransactionInput i : inputs )
				{
					ScriptFormat.Writer sw = new ScriptFormat.Writer ();
					Key key = signingKeys.get (j);
					byte[] sig = key.sign (hashTransaction (t, j, ScriptFormat.SIGHASH_ALL, i.getScript ()));
					byte[] sigPlusType = new byte[sig.length + 1];
					System.arraycopy (sig, 0, sigPlusType, 0, sig.length);
					sigPlusType[sigPlusType.length - 1] = (byte) (ScriptFormat.SIGHASH_ALL & 0xff);
					sw.writeData (sigPlusType);
					sw.writeData (key.getPublic ());
					i.setScript (sw.toByteArray ());
					++j;
				}

				System.out.println ("Sending " + o.getValue () + " satoshis to " + address);
				HttpPost post = new HttpPost (SERVER_URL + "/route");
				post.setHeader ("Content-Type", "application/json");
				authorizationString = "Basic " + new String (Base64.encodeBase64 ((user + ":" + password).getBytes (), false));
				post.setHeader ("Authorization", authorizationString);
				JSONObject r = new JSONObject ();
				r.put ("transaction", t.toWireDump ());
				post.setEntity (new StringEntity (r.toString ()));
				response = httpclient.execute (post);
				if ( response.getEntity () != null )
				{
					BufferedReader in = new BufferedReader (new InputStreamReader (response.getEntity ().getContent (), "UTF-8"));
					StringWriter wr = new StringWriter ();
					String line;
					while ( (line = in.readLine ()) != null )
					{
						wr.write (line);
					}
					System.out.println ("Sent transaction: " + wr.toString ());
				}
			}
			catch ( IOException e )
			{
				e.printStackTrace ();
				System.exit (1);
			}
			catch ( ValidationException e )
			{
				e.printStackTrace ();
				System.exit (1);
			}
			catch ( JSONException e )
			{
				e.printStackTrace ();
				System.exit (1);
			}
			System.exit (0);
		}
		System.exit (0);
	}

	private static void writeOutputScript (String address, TransactionOutput o) throws ValidationException
	{
		ScriptFormat.Writer writer = new ScriptFormat.Writer ();
		writer.writeToken (new ScriptFormat.Token (Opcode.OP_DUP));
		writer.writeToken (new ScriptFormat.Token (Opcode.OP_HASH160));
		byte[] a = Address.fromSatoshiStyle (address).toByteArray ();
		if ( a.length != 20 )
		{
			throw new ValidationException ("claim to an address");
		}
		writer.writeData (a);
		writer.writeToken (new ScriptFormat.Token (Opcode.OP_EQUALVERIFY));
		writer.writeToken (new ScriptFormat.Token (Opcode.OP_CHECKSIG));
		o.setScript (writer.toByteArray ());
	}

	private static byte[] hashTransaction (Transaction transaction, int inr, int hashType, byte[] script) throws ValidationException
	{
		Transaction copy = null;
		try
		{
			copy = transaction.clone ();
		}
		catch ( CloneNotSupportedException e1 )
		{
			return null;
		}

		// implicit SIGHASH_ALL
		int i = 0;
		for ( TransactionInput in : copy.getInputs () )
		{
			if ( i == inr )
			{
				in.setScript (script);
			}
			else
			{
				in.setScript (new byte[0]);
			}
			++i;
		}

		if ( (hashType & 0x1f) == ScriptFormat.SIGHASH_NONE )
		{
			copy.getOutputs ().clear ();
			i = 0;
			for ( TransactionInput in : copy.getInputs () )
			{
				if ( i != inr )
				{
					in.setSequence (0);
				}
				++i;
			}
		}
		else if ( (hashType & 0x1f) == ScriptFormat.SIGHASH_SINGLE )
		{
			int onr = inr;
			if ( onr >= copy.getOutputs ().size () )
			{
				// this is a Satoshi client bug.
				// This case should throw an error but it instead retuns 1 that is not checked and interpreted as below
				return ByteUtils.fromHex ("0100000000000000000000000000000000000000000000000000000000000000");
			}
			for ( i = copy.getOutputs ().size () - 1; i > onr; --i )
			{
				copy.getOutputs ().remove (i);
			}
			for ( i = 0; i < onr; ++i )
			{
				copy.getOutputs ().get (i).setScript (new byte[0]);
				copy.getOutputs ().get (i).setValue (-1L);
			}
			i = 0;
			for ( TransactionInput in : copy.getInputs () )
			{
				if ( i != inr )
				{
					in.setSequence (0);
				}
				++i;
			}
		}
		if ( (hashType & ScriptFormat.SIGHASH_ANYONECANPAY) != 0 )
		{
			List<TransactionInput> oneIn = new ArrayList<TransactionInput> ();
			oneIn.add (copy.getInputs ().get (inr));
			copy.setInputs (oneIn);
		}

		WireFormat.Writer writer = new WireFormat.Writer ();
		copy.toWire (writer);

		byte[] txwire = writer.toByteArray ();
		byte[] hash = null;
		try
		{
			MessageDigest a = MessageDigest.getInstance ("SHA-256");
			a.update (txwire);
			a.update (new byte[] { (byte) (hashType & 0xff), 0, 0, 0 });
			hash = a.digest (a.digest ());
		}
		catch ( NoSuchAlgorithmException e )
		{
		}
		return hash;
	}

}
