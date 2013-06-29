BOP Merchant Bitcoin Server Client Utility
==========================================

This is a client side utility for the BOP Merchant Bitcoin Server.

Build
-----

    mvn package

Create the Master Key
---------------------

BOP Merchant Bitcoin Server generates payment addresses such that they can be claimed using a single master key stored at your site. The first step of using the service is to generate and store the master key. Use the utility bop-mbs to generate your master key as follows:

    java -jar target/bop-mbs-1.0.jar --newkey keyfile

The program will exit with an error if the keyfile already exists avoiding to overwrite an existing master key. The utility will prompt you for a passphrase that is used to encrypt the master private key stored in the keyfile you choose. Remember the passphrase as Bits of Proof will not be able to help you recovering it - since the algorithm of key generation and encryption was carefully designed to disallow access of the private master key without knowing the passphrase. It is also designed to be highly resistant against brute (computation) force attacks. After successful completion the master private key is encrypted with the passphrase and is stored in the keyfile. Please back up the keyfile to protect it from accidental loss.
Once the master private key is generated the master public key can be printed by the utility. The master public key allows the generation of addresses that belong to you but it does not  allow claiming those funds since it is not suitable to sign transactions (only the private key is). Print the master public key using the following command line.

    java -jar target/bop-mbs-1.0.jar --public keyfile

The master public key is a parameter to some calls you place with the BOP Merchant Bitcoin Server
BOP Merchant Bitcoin Server uses the BIP32 standard of key generation, storage and serialization. Please refer to BIP32 for further technical details.

Sweep the collected funds to an address
---------------------------------------
You may periodically sweep the funds received with the help of the BOP Merchant Bitcoin Server to a destination address using the bop-mbs utility as follows:

    java -jar target/bop-mbs-1.0.jar -u bop_user -p bop_password -s bop_server_url --sweep keyfile --address destinationaddress

The tool will send all funds collected in addresses of the master private key onto the destination address. You need a BOP Enterprise Bitcoin Server account to create and route the sweeping transaction, that account is referred by the first three options. 
Once you received the funds on the destination address you may also choose to generate a new master key and set it to the server to use in future payment requests. Be however sure to keep the master key until you have outstanding payment requests with the old key. Also make sure you set the new master public new key to the server.

Export the Master Private Key
-----------------------------
As an alternative to sweeping the funds with the bop-mbs utility you may also export the master private key and import it into a wallet that supports the BIP32 standard, thereby effectively giving the control over those funds to that wallet. You should no longer use the corresponding master public key with BOP Merchant Bitcoin Server as your wallet might use addresses for an other purpose than BOP Merchant Bitcoin Server does, best delete the current and generate a new keyfile after importing the private key to your wallet. Be aware that the private key exported is not encrypted, you should only store it in very safe place, preferably another encrypted wallet.

    java -jar target/bop-mbs-1.0.jar --private keyfile

