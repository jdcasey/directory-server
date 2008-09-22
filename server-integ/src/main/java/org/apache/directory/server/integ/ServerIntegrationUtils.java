/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.integ;


import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import netscape.ldap.LDAPConnection;

import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.integ.IntegrationUtils;
import org.apache.directory.server.ldap.LdapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ServerIntegrationUtils extends IntegrationUtils
{
    /** The class logger */
    private static final Logger LOG = LoggerFactory.getLogger( ServerIntegrationUtils.class );
    private static final String CTX_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 10389;
    private static final String DEFAULT_ADMIN = ServerDNConstants.ADMIN_SYSTEM_DN;
    private static final String DEFAULT_PASSWORD = "secret";



    /**
     * Creates a JNDI LdapContext with a connection over the wire using the 
     * SUN LDAP provider.  The connection is made using the administrative 
     * user as the principalDN.  The context is to the rootDSE.
     *
     * @param ldapService the LDAP server to get the connection to
     * @return an LdapContext as the administrative user to the RootDSE
     * @throws Exception if there are problems creating the context
     */
    public static LdapContext getWiredContext( LdapService ldapService ) throws Exception
    {
        return getWiredContext( ldapService, null );
    }


    /**
     * Creates a JNDI LdapContext with a connection over the wire using the 
     * SUN LDAP provider.  The connection is made using the administrative 
     * user as the principalDN.  The context is to the rootDSE.
     *
     * @param ldapService the LDAP server to get the connection to
     * @return an LdapContext as the administrative user to the RootDSE
     * @throws Exception if there are problems creating the context
     */
    public static LdapContext getWiredContext( LdapService ldapService, String principalDn, String password ) 
        throws Exception
    {
        LOG.debug( "Creating a wired context to local LDAP server on port {}", ldapService.getIpPort() );
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put( Context.INITIAL_CONTEXT_FACTORY, CTX_FACTORY );
        env.put( Context.PROVIDER_URL, "ldap://localhost:" + ldapService.getIpPort() );
        env.put( Context.SECURITY_PRINCIPAL, principalDn );
        env.put( Context.SECURITY_CREDENTIALS, password );
        env.put( Context.SECURITY_AUTHENTICATION, "simple" );
        return new InitialLdapContext( env, null );
    }


    /**
     * Creates a JNDI LdapContext with a connection over the wire using the 
     * SUN LDAP provider.  The connection is made using the administrative 
     * user as the principalDN.  The context is to the rootDSE.
     *
     * @param ldapService the LDAP server to get the connection to
     * @return an LdapContext as the administrative user to the RootDSE
     * @throws Exception if there are problems creating the context
     */
    public static LdapContext getWiredContext( LdapService ldapService, Control[] controls ) throws Exception
    {
        LOG.debug( "Creating a wired context to local LDAP server on port {}", ldapService.getIpPort() );
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put( Context.INITIAL_CONTEXT_FACTORY, CTX_FACTORY );
        env.put( Context.PROVIDER_URL, "ldap://localhost:" + ldapService.getIpPort() );
        env.put( Context.SECURITY_PRINCIPAL, ServerDNConstants.ADMIN_SYSTEM_DN );
        env.put( Context.SECURITY_CREDENTIALS, "secret" );
        env.put( Context.SECURITY_AUTHENTICATION, "simple" );
        return new InitialLdapContext( env, controls );
    }

    
    /**
     * Creates a JNDI LdapContext with a connection over the wire using the 
     * SUN LDAP provider.  The connection is made using the administrative 
     * user as the principalDN.  The context is to the rootDSE.
     *
     * @param ldapService the LDAP server to get the connection to
     * @return an LdapContext as the administrative user to the RootDSE
     * @throws Exception if there are problems creating the context
     */
    public static LdapContext getWiredContextThrowOnRefferal( LdapService ldapService ) throws Exception
    {
        LOG.debug( "Creating a wired context to local LDAP server on port {}", ldapService.getIpPort() );
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put( Context.INITIAL_CONTEXT_FACTORY, CTX_FACTORY );
        env.put( Context.PROVIDER_URL, "ldap://localhost:" + ldapService.getIpPort() );
        env.put( Context.SECURITY_PRINCIPAL, ServerDNConstants.ADMIN_SYSTEM_DN );
        env.put( Context.SECURITY_CREDENTIALS, "secret" );
        env.put( Context.SECURITY_AUTHENTICATION, "simple" );
        env.put( Context.REFERRAL, "throw" );
        return new InitialLdapContext( env, null );
    }

    
    /**
     * Creates a JNDI LdapContext with a connection over the wire using the 
     * SUN LDAP provider.  The connection is made using the administrative 
     * user as the principalDN.  The context is to the rootDSE.
     *
     * @param ldapService the LDAP server to get the connection to
     * @return an LdapContext as the administrative user to the RootDSE
     * @throws Exception if there are problems creating the context
     */
    public static LdapContext getWiredContextRefferalIgnore( LdapService ldapService ) throws Exception
    {
        LOG.debug( "Creating a wired context to local LDAP server on port {}", ldapService.getIpPort() );
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put( Context.INITIAL_CONTEXT_FACTORY, CTX_FACTORY );
        env.put( Context.PROVIDER_URL, "ldap://localhost:" + ldapService.getIpPort() );
        env.put( Context.SECURITY_PRINCIPAL, ServerDNConstants.ADMIN_SYSTEM_DN );
        env.put( Context.SECURITY_CREDENTIALS, "secret" );
        env.put( Context.SECURITY_AUTHENTICATION, "simple" );
        env.put( Context.REFERRAL, "ignore" );
        return new InitialLdapContext( env, null );
    }

    
    /**
     * Creates a JNDI LdapContext with a connection over the wire using the 
     * SUN LDAP provider.  The connection is made using the administrative 
     * user as the principalDN.  The context is to the rootDSE.
     *
     * @param ldapService the LDAP server to get the connection to
     * @return an LdapContext as the administrative user to the RootDSE
     * @throws Exception if there are problems creating the context
     */
    public static LdapContext getWiredContextFollowOnRefferal( LdapService ldapService ) throws Exception
    {
        LOG.debug( "Creating a wired context to local LDAP server on port {}", ldapService.getIpPort() );
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put( Context.INITIAL_CONTEXT_FACTORY, CTX_FACTORY );
        env.put( Context.PROVIDER_URL, "ldap://localhost:" + ldapService.getIpPort() );
        env.put( Context.SECURITY_PRINCIPAL, ServerDNConstants.ADMIN_SYSTEM_DN );
        env.put( Context.SECURITY_CREDENTIALS, "secret" );
        env.put( Context.SECURITY_AUTHENTICATION, "simple" );
        env.put( Context.REFERRAL, "follow" );
        return new InitialLdapContext( env, null );
    }

    
    public static LDAPConnection getWiredConnection( LdapService ldapService ) throws Exception
    {
        String testServer = System.getProperty( "ldap.test.server", null );
        
        if ( testServer == null )
        {
            return getWiredConnection( ldapService, ServerDNConstants.ADMIN_SYSTEM_DN, "secret" );
        }
        
        LOG.debug( "ldap.test.server = " + testServer );
        
        String admin = System.getProperty( testServer + ".admin", DEFAULT_ADMIN );
        LOG.debug( testServer + ".admin = " + admin );

        String password = System.getProperty( testServer + ".password", DEFAULT_PASSWORD );
        LOG.debug( testServer + ".password = " + password );

        String host = System.getProperty( testServer + ".host", DEFAULT_HOST );
        LOG.debug( testServer + ".host = " + host );

        int port = Integer.parseInt( System.getProperty( testServer + ".port", Integer.toString( DEFAULT_PORT ) ) );
        LOG.debug( testServer + ".port = " + port );
        
        LDAPConnection conn = new LDAPConnection();
        conn.connect( 3, host, port, admin, password );
        return conn;
    }

    
    public static LDAPConnection getWiredConnection( LdapService ldapService, String principalDn, String password ) 
        throws Exception
    {
        LDAPConnection conn = new LDAPConnection();
        conn.connect( 3, "localhost", ldapService.getIpPort(), principalDn, password );
        return conn;
    }
}