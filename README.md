BOP Merchant Bitcoin Server Utility
===================================

This is a client side automation utility for the BOP Merchant Bitcoin Server (BopShop).

Build
-----

    mvn package

Register with the server
------------------------
The utility creates and stores a new master key that will receive the funds collected by the server and registers with the server. Example command line is:

       java -Djavax.net.ssl.trustStore=netlock.trust -jar target/bop-mbs-2.0.jar -register -n yourname -e your@email.com

The first parameter ensures that https communications with the BOP server are trusted by the process. BOP uses certificates of the certification authority Netlock, that is not accepted by all versions of java, supporting this option circumvents evtl. incompatibility.

The option -r declares that the tool should register a new user. The options -n and -e should support your name and email address BOP might use to contact you.

Executing the above example command will create and store a new master key into bopshop.key. An example output of the tool is:

user id: 11
password: CAecrLrVoenKeG
Google authenticator secret: UO4WVC3EVYY4IVXK

You will need the user id and password to authenticate while using the server API. The password is also used to encrypt the master key, now stored in the file bopshop.key.

The google authenticator secret is a random string you may import into a google authenticator to create one time tokens (time depending) for API calls the server require this authentication level.
The public key is displayed just for your reference. All payment requests the server generates for your requests use addresses that are derivable from this public key.

Claim funds collected
---------------------
Once a payment request is paid by your customer and the BOP provision is paid by you, the payment request will move into the state CLEARED.
At this point you may use this utility to transfer the funds to their destination address (e.g. cold storage) as follows:

       java -Djavax.net.ssl.trustStore=netlock.trust -jar target/bop-mbs-2.0.jar -u userid -p password -c requestid -a youraddress
       
Java 7 remarks
--------------
In case you receive:
Unexpected: java.security.InvalidKeyException: Illegal key size or default parameters

You likely run Oracle version 7 of java and need to additionally install JCE downloadable from:
http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html

unzip and copy the files local_policy.jar and US_export_policy.jar into your java runtime home lib/security folder.
Eg. on OS X it is: /Library/Java/JavaVirtualMachines/jdk1.7.0_25.jdk/Contents/Home/jre/lib/security/
