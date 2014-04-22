BOP Merchant Bitcoin Server Utility
===================================

This is a client side automation utility for the BOP Merchant Bitcoin Server (BopShop).

Prerequisites
-------------
Install 

Oracle Java 7 JDK available from http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html

and 

Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files 7 from http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html

unzip and copy the files local_policy.jar and US_export_policy.jar into your java runtime home lib/security folder.
Eg. on OS X it is: /Library/Java/JavaVirtualMachines/jdk1.7.0_55.jdk/Contents/Home/jre/lib/security/

Binary distribution
-------------------

Binary distribution of this tool is available on 

https://bitsofproof.com/nexus/content/repositories/releases/com/bitsofproof/bop-mbs/2.2/bop-mbs-2.2-shaded.jar


Register with the server
------------------------
The utility creates and stores a new master key that will receive the funds collected by the server and registers with the server. Example command line is:

       java -Djavax.net.ssl.trustStore=netlock.trust -jar bop-mbs-2.2-shaded.jar -register -n yourname -e your@email.com

The first parameter ensures that https communications with the BOP server are trusted by the process. BOP uses certificates of the certification authority Netlock, that is not accepted by all versions of java, supporting this option circumvents evtl. incompatibility.

The option -r declares that the tool should register a new user. The options -n and -e should support your name and email address BOP might use to contact you.

Executing the above example command will create and store a new master key into bopshop.key. An example output of the tool is:

user id: 11
password: CAecrLrVoenKeG
Google authenticator secret: UO4WVC3EVYY4IVXK

You will need the user id and password to authenticate while using the server API. The password is also used to encrypt the master key, now stored in the file bopshop.key.
The server receives the public master key during registration, but not the private key. Payment requests will be using addresses derived from the public master key. Payments to those addresses can only be claimed with the private key stored in the bopshop.key file.

The google authenticator secret is a random string you may import into a google authenticator to create one time tokens (time depending) for API calls the server require this authentication level.

Claim funds collected
---------------------
Once a payment request is paid by your customer and the BOP provision is paid by you, the payment request will move into the state CLEARED.
Claim all outstanding CLEARED payment requests in a single transaction:

         java -Djavax.net.ssl.trustStore=netlock.trust -jar bop-mbs-2.2-shaded.jar -u userid -p password -b -a youraddress

Extract key for late payments
-----------------------------
In case a customer pays to a request after the server stopped listening for it, the payment can not be extracted with the above procedure. 

Retrive the late paid payment request e.g. with:

         curl -X GET -u user:password https://api.bitsofproof.com/mbs/1/paymentRequest/770cec5f-5151-4960-bfec-06b3da5cd68d

Extract the keyNumber property from the response and use the following command line:

         java -jar bop-mbs-2.2-shaded.jar -p password -l keyNumber         

It will print a private key that you may import into a traditional wallet e.g. blockchain.info to claim and move the funds.


Build
-----

    mvn package    

you find the bop-mbs-2.2-shaded.jar in the target directory, refer to it in above command lines with target/bop-mbs-2.2-shaded.jar
