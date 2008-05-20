/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server;


import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.OperationNotSupportedException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;

import netscape.ldap.LDAPAttribute;
import netscape.ldap.LDAPConnection;
import netscape.ldap.LDAPException;

import org.apache.directory.server.core.entry.DefaultServerEntry;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.entry.ServerEntryUtils;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.impl.btree.Index;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.unit.AbstractServerTest;
import org.apache.directory.shared.asn1.util.Asn1StringUtils;
import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.AttributesImpl;
import org.apache.directory.shared.ldap.message.MutableControl;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.util.ArrayUtils;
import org.apache.directory.shared.ldap.util.EmptyEnumeration;


/**
 * A set of miscellanous tests.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class MiscTest extends AbstractServerTest
{
    /**
     * Cleans up old database files on creation.
     */
    public MiscTest()
    {
    }


    /**
     * Customizes setup for each test case.
     *
     * @throws Exception
     */
    public void setUp() throws Exception
    {
        super.setUp();
        if ( this.getName().equals( "testDisableAnonymousBinds" ) ||
                this.getName().equals( "testCompareWithoutAuthentication" ) ||
                this.getName().equals( "testEnableAnonymousBindsOnRootDSE" ) )
        {
            setAllowAnonymousAccess( false );
        } else if ( this.getName().equals( "testAnonymousBindsEnabledBaseSearch" ) )
        {
            setAllowAnonymousAccess( true );
        }
    }


    @Override
    protected void configureDirectoryService() throws NamingException
    {
        if ( this.getName().equals( "testUserAuthOnMixedCaseSuffix" ) )
        {
            Set<Partition> partitions = new HashSet<Partition>();
            partitions.addAll( directoryService.getPartitions() );
            JdbmPartition partition = new JdbmPartition();
            partition.setSuffix( "dc=aPache,dc=org" );
            
            LdapDN apacheDn = new LdapDN( "dc=aPache,dc=org" );
            ServerEntry serverEntry = new DefaultServerEntry( directoryService.getRegistries(), apacheDn );
            serverEntry.put( "dc", "aPache" );
            serverEntry.put( "objectClass", "top", "domain" );

            partition.setId( "apache" );
            partition.setContextEntry( serverEntry );
            Set<Index> indexedAttributes = new HashSet<Index>();
            indexedAttributes.add( new JdbmIndex( "dc" ) );
            partition.setIndexedAttributes( indexedAttributes );
            partitions.add( partition );
            directoryService.setPartitions( partitions );
        } 
        else if ( this.getName().equals( "testAnonymousBindsEnabledBaseSearch" ) )
        {
            // create a partition to search
            Set partitions = new HashSet();
            partitions.addAll( directoryService.getPartitions() );
            JdbmPartition partition = new JdbmPartition();
            partition.setSuffix( "dc=apache,dc=org" );
            
            LdapDN apacheDn = new LdapDN( "dc=apache,dc=org" );
            ServerEntry serverEntry = new DefaultServerEntry( directoryService.getRegistries(), apacheDn );
            serverEntry.put( "dc", "apache" );
            serverEntry.put( "objectClass", "top", "domain" );
            
            partition.setId( "apache" );
            partition.setContextEntry( serverEntry );
            Set<Index> indexedAttributes = new HashSet<Index>();
            indexedAttributes.add( new JdbmIndex( "dc" ) );
            partition.setIndexedAttributes( indexedAttributes );
            partitions.add( partition );
            directoryService.setPartitions( partitions );
        }
    }

    public void testCompareWithoutAuthentication() throws LDAPException
    {
        LDAPConnection conn = new LDAPConnection();
        conn.connect( "localhost", super.port );
        LDAPAttribute attr = new LDAPAttribute( "uid", "admin" );
        try
        {
            conn.compare( "uid=admin,ou=system", attr );
            fail( "Compare success without authentication" );
        }
        catch ( LDAPException e )
        {
            assertEquals( "no permission exception", 50, e.getLDAPResultCode() );
        }
    }


    /**
     * Test to make sure anonymous binds are disabled when going through
     * the wire protocol.
     *
     * @throws Exception if anything goes wrong
     */
    public void testDisableAnonymousBinds() throws Exception
    {
        // Use the SUN JNDI provider to hit server port and bind as anonymous
        InitialDirContext ic = null;
        final Hashtable<String, Object> env = new Hashtable<String, Object>();

        env.put( Context.PROVIDER_URL, "ldap://localhost:" + port + "/ou=system" );
        env.put( Context.SECURITY_AUTHENTICATION, "none" );
        env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );

        boolean connected = false;
        while ( !connected )
        {
            try
            {
                ic = new InitialDirContext( env );
                connected = true;
            }
            catch ( Exception e )
            {
            }
        }

        try
        {
            ic.search( "", "(objectClass=*)", new SearchControls() );
            fail( "If anonymous binds are disabled we should never get here!" );
        }
        catch ( NoPermissionException e )
        {
        }

        Attributes attrs = new AttributesImpl( true );
        Attribute oc = new AttributeImpl( "objectClass" );
        attrs.put( oc );
        oc.add( "top" );
        oc.add( "organizationalUnit" );

        try
        {
            ic.createSubcontext( "ou=blah", attrs );
        }
        catch ( NoPermissionException e )
        {
        }
    }


    /**
     * Test to make sure anonymous binds are allowed on the RootDSE even when disabled
     * in general when going through the wire protocol.
     *
     * @throws Exception if anything goes wrong
     */
    public void testEnableAnonymousBindsOnRootDSE() throws Exception
    {
        // Use the SUN JNDI provider to hit server port and bind as anonymous

        final Hashtable env = new Hashtable();

        env.put( Context.PROVIDER_URL, "ldap://localhost:" + port + "/" );
        env.put( Context.SECURITY_AUTHENTICATION, "none" );
        env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );

        InitialDirContext ctx = new InitialDirContext( env );
        SearchControls cons = new SearchControls();
        cons.setSearchScope( SearchControls.OBJECT_SCOPE );
        NamingEnumeration list = ctx.search( "", "(objectClass=*)", cons );
        SearchResult result = null;
        if ( list.hasMore() )
        {
            result = ( SearchResult ) list.next();
        }
        assertFalse( list.hasMore() );
        list.close();

        assertNotNull( result );
        assertEquals( "", result.getName().trim() );
    }


    /**
     * Test to make sure that if anonymous binds are allowed a user may search
     * within a a partition.
     *
     * @throws Exception if anything goes wrong
     */
    public void testAnonymousBindsEnabledBaseSearch() throws Exception
    {
        // Use the SUN JNDI provider to hit server port and bind as anonymous

        final Hashtable env = new Hashtable();

        env.put( Context.PROVIDER_URL, "ldap://localhost:" + port + "/" );
        env.put( Context.SECURITY_AUTHENTICATION, "none" );
        env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );

        InitialDirContext ctx = new InitialDirContext( env );
        SearchControls cons = new SearchControls();
        cons.setSearchScope( SearchControls.OBJECT_SCOPE );
        NamingEnumeration list = ctx.search( "dc=apache,dc=org", "(objectClass=*)", cons );
        SearchResult result = null;
        if ( list.hasMore() )
        {
            result = ( SearchResult ) list.next();
        }
        assertFalse( list.hasMore() );
        list.close();

        assertNotNull( result );
        assertNotNull( result.getAttributes().get( "dc" ) );
    }


    /**
     * Reproduces the problem with
     * <a href="http://issues.apache.org/jira/browse/DIREVE-239">DIREVE-239</a>.
     *
     * @throws Exception if anything goes wrong
     */
    public void testAdminAccessBug() throws Exception
    {
        // Use the SUN JNDI provider to hit server port and bind as anonymous

        final Hashtable env = new Hashtable();

        env.put( Context.PROVIDER_URL, "ldap://localhost:" + port );
        env.put( "java.naming.ldap.version", "3" );
        env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );

        Attributes attributes = new AttributesImpl();
        Attribute objectClass = new AttributeImpl( "objectClass" );
        objectClass.add( "top" );
        objectClass.add( "organizationalUnit" );
        attributes.put( objectClass );
        attributes.put( "ou", "blah" );
        InitialDirContext ctx = new InitialDirContext( env );
        ctx.createSubcontext( "ou=blah,ou=system", attributes );
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.OBJECT_SCOPE );
        controls.setReturningAttributes( new String[]
                {"+"} );
        NamingEnumeration list = ctx.search( "ou=blah,ou=system", "(objectClass=*)", controls );
        SearchResult result = ( SearchResult ) list.next();
        list.close();
        Attribute creatorsName = result.getAttributes().get( "creatorsName" );
        assertEquals( "", creatorsName.get() );
    }


    /**
     * Test case for <a href="http://issues.apache.org/jira/browse/DIREVE-284" where users in
     * mixed case partitions were not able to authenticate properly.  This test case creates
     * a new partition under dc=aPache,dc=org, it then creates the example user in the JIRA
     * issue and attempts to authenticate as that user.
     *
     * @throws Exception if the user cannot authenticate or test fails
     */
    public void testUserAuthOnMixedCaseSuffix() throws Exception
    {
        final Hashtable env = new Hashtable();

        env.put( Context.PROVIDER_URL, "ldap://localhost:" + port + "/dc=aPache,dc=org" );
        env.put( "java.naming.ldap.version", "3" );
        env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );
        InitialDirContext ctx = new InitialDirContext( env );
        Attributes attrs = ctx.getAttributes( "" );
        assertTrue( attrs.get( "dc" ).get().equals( "aPache" ) );

        Attributes user = new AttributesImpl( "cn", "Kate Bush", true );
        Attribute oc = new AttributeImpl( "objectClass" );
        oc.add( "top" );
        oc.add( "person" );
        oc.add( "organizationalPerson" );
        oc.add( "inetOrgPerson" );
        user.put( oc );
        user.put( "sn", "Bush" );
        user.put( "userPassword", "Aerial" );
        ctx.createSubcontext( "cn=Kate Bush", user );

        env.put( Context.SECURITY_AUTHENTICATION, "simple" );
        env.put( Context.SECURITY_CREDENTIALS, "Aerial" );
        env.put( Context.SECURITY_PRINCIPAL, "cn=Kate Bush,dc=aPache,dc=org" );

        InitialDirContext userCtx = new InitialDirContext( env );
        assertNotNull( userCtx );
    }


    /**
     * Tests to make sure undefined attributes in filter assertions are pruned and do not
     * result in exceptions.
     */
    public void testBogusAttributeInSearchFilter() throws Exception
    {
        SearchControls cons = new SearchControls();
        NamingEnumeration<SearchResult> e = sysRoot.search( "", "(bogusAttribute=abc123)", cons );
        assertNotNull( e );
        assertEquals( e.getClass(), EmptyEnumeration.class );
        
        e = sysRoot.search( "", "(!(bogusAttribute=abc123))", cons );
        assertNotNull( e );
        assertFalse( e.hasMore() );
        assertEquals( e.getClass(), EmptyEnumeration.class );
        
        e = sysRoot.search( "", "(|(bogusAttribute=abc123)(bogusAttribute=abc123))", cons );
        assertNotNull( e );
        assertFalse( e.hasMore() );
        assertEquals( e.getClass(), EmptyEnumeration.class );
        
        e = sysRoot.search( "", "(|(bogusAttribute=abc123)(ou=abc123))", cons );
        assertNotNull( e );
        assertFalse( e.hasMore() );
        assertFalse( e.getClass().equals( EmptyEnumeration.class ) );

        e = sysRoot.search( "", "(OBJECTclass=*)", cons );
        assertNotNull( e );
        assertTrue( e.hasMore() );
        assertFalse( e.getClass().equals( EmptyEnumeration.class ) );

        e = sysRoot.search( "", "(objectclass=*)", cons );
        assertNotNull( e );
        assertFalse( e.getClass().equals( EmptyEnumeration.class ) );
    }


    public void testFailureWithUnsupportedControl() throws Exception
    {
        MutableControl unsupported = new MutableControl()
        {
            boolean isCritical = true;
            private static final long serialVersionUID = 1L;


            public String getType()
            {
                return "1.1.1.1";
            }


            public void setID( String oid )
            {
            }


            public byte[] getValue()
            {
                return new byte[0];
            }


            public void setValue( byte[] value )
            {
            }


            public boolean isCritical()
            {
                return isCritical;
            }


            public void setCritical( boolean isCritical )
            {
                this.isCritical = isCritical;
            }


            public String getID()
            {
                return "1.1.1.1";
            }


            public byte[] getEncodedValue()
            {
                return new byte[0];
            }
        };
        final Hashtable env = new Hashtable();

        env.put( Context.PROVIDER_URL, "ldap://localhost:" + port + "/ou=system" );
        env.put( "java.naming.ldap.version", "3" );
        env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );
        env.put( Context.SECURITY_AUTHENTICATION, "simple" );
        env.put( Context.SECURITY_CREDENTIALS, "secret" );
        env.put( Context.SECURITY_PRINCIPAL, "uid=admin,ou=system" );
        InitialLdapContext ctx = new InitialLdapContext( env, null );

        Attributes user = new AttributesImpl( "cn", "Kate Bush", true );
        Attribute oc = new AttributeImpl( "objectClass" );
        oc.add( "top" );
        oc.add( "person" );
        oc.add( "organizationalPerson" );
        oc.add( "inetOrgPerson" );
        user.put( oc );
        user.put( "sn", "Bush" );
        user.put( "userPassword", "Aerial" );
        ctx.setRequestControls( new MutableControl[]
                {unsupported} );

        try
        {
            ctx.createSubcontext( "cn=Kate Bush", user );
        }
        catch ( OperationNotSupportedException e )
        {
        }

        unsupported.setCritical( false );
        DirContext kate = ctx.createSubcontext( "cn=Kate Bush", user );
        assertNotNull( kate );
        assertTrue( ArrayUtils.isEquals( Asn1StringUtils.getBytesUtf8( "Aerial" ), kate.getAttributes( "" ).get(
                "userPassword" ).get() ) );
    }
}