.. Cybersecurity GE Privacy-Preserving Data Sharing (P2DS) documentation master file, created by
   sphinx-quickstart on Mon Aug 31 10:00:09 2015.
   You can adapt this file completely to your liking, but it should at least
   contain the root `toctree` directive.

========================================================================
Cybersecurity GE Privacy-Preserving Data Sharing (P2DS) Operator's Guide
========================================================================

Contents:

.. toctree::
   :maxdepth: 2



==================
Indices and tables
==================

* :ref:`genindex`
* :ref:`modindex`
* :ref:`search`


==========
Setting Up
==========

You need at least four parties in order for P2DS to work: three
parties have data on which they want to perform a computation, and one
party manages that computation. Neither party needs to trust any other
party. The parties having the data host *input* and *privacy peers*
and the managing party hostrs the *group manager*.

-----------------------
Installing the Services
-----------------------

Installing from WAR
===================

You should have received three WAR files as part of the P2DS
distribution, or you can build them yourself; see below.

* p2ds-group-management.war contains the group manager
* p2ds-input-peer.war contains the input peer
* p2ds-privacy-peer.war contains the privacy peer

These must be deployed on application servers. Deploy
p2ds-group-management.war on your group manager and deploy the other
two on each organisation that wants to participate in the
computation.

Installing from Source
======================

In order to build and compile the services from source code, first get
the ZHAW modification of SEPIA::

  git clone https://github.com/fiware-cybercaptor/cybercaptor-P2DS.git
  cd sepia
  mvn install -DskipTests
  cd ..

  cd p2ds
  mvn package

Now the directories ``group-management/target``,
``input-peer/target``, and ``privacy-peer/target`` will contain the
respective WAR files.

--------------------
Generating Key Pairs
--------------------

All messages that are exchanged in P2DS are digitally
signed. Additionally, all parties should employ TLS.  Digital
signatures are needed to be able to check the messages by the
receiving parties in addition to the transport security offered by
TLS.  And TLS is needed because in some cases, sensitive information
like an authentication token is transported in the messages.

First, get and build the P2DS key generation program::

  git clone https://github.engineering.zhaw.ch/munt/p2dsKeygen.git
  cd p2dsKeygen
  mvn package -DskipTests

Next, use it to generate a key pair::

  java -cp target/keygen-0.0.0.1.jar ch.zhaw.ficore.p2ds.keygen/Main
  base64 key.private > key.private.b64
  base64 key.public > key.public.b64
  srm -s key.private key.public

The last command deletes the (unneeded) binary key files, leaving only
the Base64-encoded ones.

This generates an Elliptic Curve DSA key of 409 bits, which is
supposed to have the equivalent RSA strength of more than 8192 bits
(see `here
<http://wiki.openssl.org/index.php/Elliptic_Curve_Cryptography>`_). However,
it uses Java's ``SecureRandom`` generator, which has had `trouble
<https://en.wikipedia.org/wiki/Random_number_generator_attack-Java_nonce_collision>`_
in the past. So it is probably best to be on the lookout for messages
about SecureRandom.

This program generates to files, ``key.public`` and ``key.private``,
both of which must be uploaded to the respective peer.  They can in
principle be uploaded to any directory on the application server, but
we recommend a directory to which only the applicaiton server has read
access.  Since it is not necessary to change the key files, once
uploaded, we also recommend setting the permissions on these files to
read-only.  On Unix-like operating systems, do this::

  sudo cp key.public.b64 key.private.b64 /var/p2ds
  srm -s key.public.b64 key.private.b64
  cd /var/p2ds
  sudo chown apache key.public.b64 key.private.b64
  sudo chmod 444 key.public.b64
  sudo chmod 400 key.private.b64
  sudo chmod 500 .

Again, the unneeded copies of the key files are securely deleted. This
is not important for the pubic key but very important indeed for the
private key.

Here, ``apache`` is the system's pseudo user that runs the application
server's processes.

-------------------------
 Configuring the Services
-------------------------

Group Management
================

The Group Management's database configuration is described in its
``persistence.xml`` file::

  <?xml version-"1.0"?>
  <persistence version-"1.0" xmlns-"http://java.sun.com/xml/ns/persistence">
    <persistence-unit name="testjpa" transaction-type="RESOURCE_LOCAL">
        <provider>
            org.hibernate.ejb.HibernatePersistence
        </provider>
        <class>ch.zhaw.ficore.p2ds.group.storage.Group</class>
        <class>ch.zhaw.ficore.p2ds.group.storage.Peer</class>
        <class>ch.zhaw.ficore.p2ds.group.storage.Registration</class>
        <properties>
          <property name="hibernate.connection.driver_class"
                    value-"com.mysql.jdbc.Driver"/>
          <property name="hibernate.connection.url"
                    value-"jdbc:mysql://localhost/p2ds"/>
          <property name="hibernate.connection.username"
                    value-"sepia"/>
          <property name="hibernate.connection.password"
                    value-"my=password"/>
          <property name="hibernate.dialect"
                    value-"org.hibernate.dialect.MySQLDialect"/>
          <property name="hibernate.hbm2ddl.auto" value-"create"/>
          <property name="hibernate.show_sql" value-"true"/>
          <property name="hibernate.format_sql" value-"true"/>
        </properties>
    </persistence-unit>
  </persistence>

The obviously configurable parameters are
``hibernate.connection.driver_class``, ``hibernate.connection.url``,
``hibernate.connection.username``, and
``hibernate.connection.password``. Change these to suit your database setup.

Input Peer
==========

The input peer's configuration is likewise in its ``web.xml``::

  <?xml version-"1.0" encoding-"UTF-8"?>
  <!-- This web.xml file is not required when using Servlet 3.0 container, 
     see implementation details http://jersey.java.net/nonav/documentation/latest/jax-rs.html-d4e194 -->
  <web-app version-"2.5" xmlns-"http://java.sun.com/xml/ns/javaee" xmlns:xsi-"http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation-"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd">
    <servlet>
      <servlet-name>Jersey Web Application</servlet-name>
      <servlet-class>com.sun.jersey.spi.container.servlet.ServletContainer</servlet-class>
      <init-param>
        <param-name>com.sun.jersey.config.property.packages</param-name>
        <param-value>ch.zhaw.ficore.p2ds</param-value>
      </init-param>
        <load-on-startup>1</load-on-startup>
    </servlet>
    <servlet-mapping>
      <servlet-name>Jersey Web Application</servlet-name>
      <url-pattern>/*</url-pattern>
    </servlet-mapping>

    <env-entry>
      <env-entry-name>inputPeer/hanspeer/registrationCode</env-entry-name>
      <env-entry-value>TEST</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>
  
    <env-entry>
      <env-entry-name>inputPeer/hanspeer/groupMgmtURL</env-entry-name>
      <env-entry-value>http://localhost:12001/p2ds-group-management/group-mgmt</v-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>
  
    <env-entry>
      <env-entry-name>inputPeer/hanspeer/pubKeyPath</env-entry-name>
      <env-entry-value>/var/p2ds/hanspeer.public.b64</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>
  
    <env-entry>
      <env-entry-name>inputPeer/hanspeer/privKeyPath</env-entry-name>
      <env-entry-value>/var/p2ds/hanspeer.private</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>
  
    <env-entry>
      <env-entry-name>inputPeer/peerhans/registrationCode</env-entry-name>
      <env-entry-value>TEST</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>
  
    <env-entry>
      <env-entry-name>inputPeer/peerhans/groupMgmtURL</env-entry-name>
      <env-entry-value>http://localhost:12001/p2ds-group-management/group-mgmt</v-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>
    
     <env-entry>
      <env-entry-name>inputPeer/peerhans/pubKeyPath</env-entry-name>
      <env-entry-value>/var/p2ds/peerhans.public.b64</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>
  
    <env-entry>
      <env-entry-name>inputPeer/peerhans/privKeyPath</env-entry-name>
      <env-entry-value>/var/p2ds/peerhans.private</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>
  
    <env-entry>
      <env-entry-name>inputPeer/url</env-entry-name>
      <env-entry-value>http://localhost:12001/p2ds-input-peer/peer</env-entry-  value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>
  </web-app>


This file contains a number of very straightforward configuraiton
options and some not so straightforward ones. Each peer can operate as
one of several peer instances. These peers are distinguished by their
names.  For example, if you want to run a peer called ``hanspeer``,
you would tell the peer to register itself with the group manager
under the name ``hanspeer``. If you wished the peer to run as
``peerhans``, you would tell the peer to register under that
name. Names must be unique and it's a discussion between the
organisations running the peers who gets to choose which names.

Once a computation starts, the group manager will contact the peer and
tell it the name of the peer it should run as. This then determines
the public and private keys to use and hence the ``publicKeyPath`` and
``privateKeyPath`` configuration options.

Privacy Peer
============

The privacy peer's configuration is likewise in its ``web.xml``::

  <?xml version-"1.0" encoding-"UTF-8"?>
  <!-- This web.xml file is not required when using Servlet 3.0 container, 
     see implementation details http://jersey.java.net/nonav/documentation/latest/jax-rs.html-d4e194 -->
  <web-app version-"2.5" xmlns-"http://java.sun.com/xml/ns/javaee" xmlns:xsi-"http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation-"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd">
    <servlet>
      <servlet-name>Jersey Web Application</servlet-name>
      <servlet-class>com.sun.jersey.spi.container.servlet.ServletContainer</servlet-class>
      <init-param>
        <param-name>com.sun.jersey.config.property.packages</param-name>
        <param-value>ch.zhaw.ficore.p2ds</param-value>
      </init-param>
      <load-on-startup>1</load-on-startup>
    </servlet>
    
    <servlet-mapping>
      <servlet-name>Jersey Web Application</servlet-name>
      <url-pattern>/*</url-pattern>
    </servlet-mapping>

    <env-entry>
      <env-entry-name>privacyPeer/ppeer/registrationCode</env-entry-name>
      <env-entry-value>TEST</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>

    <env-entry>
      <env-entry-name>privacyPeer/ppeer/groupMgmtURL</env-entry-name>
      <env-entry-value>http://localhost:12001/p2ds-group-management/group-mgmt</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>

    <env-entry>
      <env-entry-name>privacyPeer/ppeer/pubKeyPath</env-entry-name>
      <env-entry-value>/var/p2ds/ppeer.public.b64</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>

    <env-entry>
      <env-entry-name>privacyPeer/ppeer/privKeyPath</env-entry-name>
      <env-entry-value>/var/p2ds/ppeer.private</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>
    
    <env-entry>
      <env-entry-name>privacyPeer/ppeer2/registrationCode</env-entry-name>
      <env-entry-value>TEST</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>

    <env-entry>
      <env-entry-name>privacyPeer/ppeer2/groupMgmtURL</env-entry-name>
      <env-entry-value>http://localhost:12001/p2ds-group-management/group-mgmt</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>

    <env-entry>
      <env-entry-name>privacyPeer/ppeer2/pubKeyPath</env-entry-name>
      <env-entry-value>/var/p2ds/ppeer2.public.b64</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>

    <env-entry>
      <env-entry-name>privacyPeer/ppeer2/privKeyPath</env-entry-name>
      <env-entry-value>/var/p2ds/ppeer2.private</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>

    <env-entry>
      <env-entry-name>privacyPeer/ppeer3/registrationCode</env-entry-name>
      <env-entry-value>TEST</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>

    <env-entry>
      <env-entry-name>privacyPeer/ppeer3/groupMgmtURL</env-entry-name>
      <env-entry-value>http://localhost:12001/p2ds-group-management/group-mgmt</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>

    <env-entry>
      <env-entry-name>privacyPeer/ppeer3/pubKeyPath</env-entry-name>
      <env-entry-value>/var/p2ds/ppeer3.public.b64</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>
    
    <env-entry>
      <env-entry-name>privacyPeer/ppeer3/privKeyPath</env-entry-name>
      <env-entry-value>/var/p2ds/ppeer3.private</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>

    <env-entry>
      <env-entry-name>privacyPeer/url</env-entry-name>
      <env-entry-value>http://localhost:12001/p2ds-privacy-peer/peer</env-entry-value>
      <env-entry-type> java.lang.String </env-entry-type>
    </env-entry>

  </web-app>

Again, a privacy peer can operate under different names and can work
for different group managers and have different key pairs.  The
``privacyPeer/url`` entry tells the group manager under which URL it
can reach this privacy peer.

========================
Performing a Computation
========================

----------------------
Configuring the Inputs
----------------------

----------------
Creating a Group
----------------

---------------------
Registering the Peers
---------------------

------------------------
Starting the Computation
------------------------

-------------------
Viewing the Results
-------------------
