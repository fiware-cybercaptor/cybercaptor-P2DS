********************************************************
P2DS Installation and Administration Guide
********************************************************

Setting Up
==========

You need at least four parties in order for P2DS to work: three parties
have data on which they want to perform a computation, and one party
manages that computation. Neither party needs to trust any other party.
The parties having the data host *input* and *privacy peers* and the
managing party hosters the *group manager*.

Installing the Services
-----------------------

Installing from WAR
~~~~~~~~~~~~~~~~~~~

You should have received three WAR files as part of the P2DS
distribution, or you can build them yourself; see below.

-  p2ds-group-management.war contains the group manager
-  p2ds-peer.war contains peers
-  p2ds-receiver.war may be used for demo purposes (receives final
   results)

These must be deployed on application servers. Deploy
p2ds-group-management.war on your group manager and deploy the
pe2ds-peer.war on each organisation that wants to participate in the
computation. Additionally you can also deploy the receiver, but you
should write your own endpoint.

Installing from Source
~~~~~~~~~~~~~~~~~~~~~~

In order to build and compile the services from source code, first get
the source, then build:

.. code:: bash

    git clone https://github.com/fiware-cybercaptor/cybercaptor-P2DS
    cd sepia
    mvn install -DskipTests
    cd ../p2ds
    mvn package

Now the directories ``group-management/target``, ``peer/target``, and
``receiver/target`` will contain the respective WAR files.

Generating Key Pairs
--------------------

All messages that are exchanged in P2DS are digitally signed.
Additionally, all parties should employ TLS. Digital signatures are
needed to be able to check the messages by the receiving parties in
addition to the transport security offered by TLS. And TLS is needed
because in some cases, sensitive information like an authentication
token is transported in the messages.

First, get and build the P2DS key generation program:

.. code:: bash

    git clone https://github.engineering.zhaw.ch/munt/p2dsKeygen.git
    cd p2dsKeygen
    mvn package -DskipTests

Next, use it to generate a key pair:

.. code:: bash

    java -cp target/keygen-0.0.0.1.jar ch.zhaw.ficore.p2ds.keygen/Main
    base64 key.private > key.private.b64
    base64 key.public > key.public.b64
    srm -s key.private key.public

The last command deletes the (unneeded) binary key files, leaving only
the Base64-encoded ones.

This generates an Elliptic Curve DSA key of 409 bits, which is supposed
to have the equivalent RSA strength of more than 8192 bits (see
`here <http://wiki.openssl.org/index.php/Elliptic_Curve_Cryptography>`__).
However, it uses Java's ``SecureRandom`` generator, which has had
`trouble <https://en.wikipedia.org/wiki/Random_number_generator_attack-Java_nonce_collision>`__
in the past. So it is probably best to be on the lookout for messages
about SecureRandom.

This program generates two files, ``key.public`` and ``key.private``,
both of which must be uploaded to the respective peer. They can in
principle be uploaded to any directory on the application server, but we
recommend a directory to which only the applicaiton server has read
access. Since it is not necessary to change the key files, once
uploaded, we also recommend setting the permissions on these files to
read-only. On Unix-like operating systems, do this:

.. code:: bash

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

Configuring the Services
========================

Group Management
----------------

The Group Management's database configuration is described in its
``persistence.xml`` file:

.. code:: xml

      <?xml version-"1.0"?>
      <persistence version-"1.0" xmlns-"http://java.sun.com/xml/ns/persistence">
        <persistence-unit name="p2ds-group-management" transaction-type="RESOURCE_LOCAL">
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
``hibernate.connection.password``. Change these to suit your database
setup.

The group management service's configuration can be found in the
``web.xml``. You only need to configure the ``group/adminKey`` option
which is the *password* for admin functionality.

.. code:: xml

    <?xml version="1.0" encoding="UTF-8"?>
    <!-- This web.xml file is not required when using Servlet 3.0 container,
         see implementation details http://jersey.java.net/nonav/documentation/latest/jax-rs.html#d4e194 -->
    <web-app version="2.5" xmlns="http://java.sun.com/xml/ns/javaee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd">
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
            <servlet-name>default</servlet-name>
            <url-pattern>/res/*</url-pattern>
        </servlet-mapping>
        <servlet-mapping>
            <servlet-name>Jersey Web Application</servlet-name>
            <url-pattern>/*</url-pattern>
        </servlet-mapping>

      <env-entry>
          <env-entry-name>peer/adminKey</env-entry-name>
          <env-entry-value>default-admin-key</env-entry-value>
          <env-entry-type> java.lang.String </env-entry-type>
        </env-entry>
    </web-app>

Additionally you may want to add some security constraints to disable
the GUI from being public. You should read up on tomcat's security
constraints documentation on how to setup security constraints, roles
and realms. We recommend using at least ``http basic auth``. In general
everything except ``/group-mgmt/*`` is something you might not want to
be public:

.. code:: xml

    <security-constraint>
      <web-resource-collection>
        <web-resource-name>GUI</web-resource-name>
        <description>all pages</description>
        <url-pattern>/*</url-pattern>
      </web-resource-collection>
      <auth-constraint>
         <role-name>admins</role-name>
      </auth-constraint>
    </security-constraint>
    <security-constraint>
      <web-resource-collection>
        <web-resource-name>API</web-resource-name>
        <description>REST-API</description>
        <url-pattern>/group-mgmt/*</url-pattern>
      </web-resource-collection>
      <!-- without auth-constraint == public -->
    </security-constraint>

Peer
----

The peer's database configuration is also described in the
``persistence.xml``:

.. code:: xml

    <?xml version="1.0"?>
    <persistence version="1.0" xmlns="http://java.sun.com/xml/ns/persistence">
        <persistence-unit name="p2ds-peer" transaction-type="RESOURCE_LOCAL">
            <provider>
                org.hibernate.ejb.HibernatePersistence
            </provider>
            <class>ch.zhaw.ficore.p2ds.peer.storage.PeerConfiguration</class>
            <properties>
                 <property name="hibernate.connection.driver_class" value="com.mysql.jdbc.Driver"/>
                    <property name="hibernate.connection.url" value="jdbc:mysql://localhost/p2ds_input?user=sepia&amp;password=8M07r8FlZZ"/>
                    <property name="hibernate.dialect" value="org.hibernate.dialect.MySQLDialect"/>
                    <property name="hibernate.hbm2ddl.auto" value="create"/>
                    <property name="hibernate.show_sql" value="true"/>
                    <property name="hibernate.format_sql" value="true"/>
            </properties>
        </persistence-unit>
    </persistence>

You may and should change the properties based on your setup.

The input peer's configuration is likewise in its ``web.xml``:

.. code:: xml

    <?xml version="1.0" encoding="UTF-8"?>
    <!-- This web.xml file is not required when using Servlet 3.0 container,
         see implementation details http://jersey.java.net/nonav/documentation/latest/jax-rs.html#d4e194 -->
    <web-app version="2.5" xmlns="http://java.sun.com/xml/ns/javaee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd">
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
            <env-entry-name>peer/url</env-entry-name>
            <env-entry-value>http://localhost:12001/p2ds-peer</env-entry-value>
            <env-entry-type> java.lang.String </env-entry-type>
        </env-entry>

        <env-entry>
          <env-entry-name>peer/adminKey</env-entry-name>
          <env-entry-value>default-admin-key</env-entry-value>
          <env-entry-type> java.lang.String </env-entry-type>
        </env-entry>
    </web-app>

You only need to configure the ``peer/url`` and ``peer/adminKey``
environment entries. ``peer/url`` is the url under which the peer
service can be contacted (the url you host it at) and ``peer/adminKey``
is the admin key for REST-API methods only to be used by the admin.

Note about persistence.xml
==========================

.. code:: xml

    <property name="hibernate.hbm2ddl.auto" value="create"/>

The ``hibernate.hbm2ddl.auto`` property set to ``create`` will re-create
the database (deleting existing entries) at every launch of the
services. This is a good setting if you are just experimenting with P2DS
but it's not a production setting. You may leave the property on
``create`` for the setup phase but once you go *live* you should
absolutely remove it.
