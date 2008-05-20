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


import java.util.Hashtable;

import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import org.apache.directory.server.unit.AbstractServerTest;
import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.AttributesImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * Testcase with different modify DN operations on a person entry.
 * Originally created to demonstrate DIREVE-173.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class ModifyRdnTest extends AbstractServerTest
{

    private LdapContext ctx = null;


    /**
     * Create attributes for a person entry.
     */
    protected Attributes getPersonAttributes( String sn, String cn )
    {
        Attributes attributes = new AttributesImpl();
        Attribute attribute = new AttributeImpl( "objectClass" );
        attribute.add( "top" );
        attribute.add( "person" );
        attributes.put( attribute );
        attributes.put( "cn", cn );
        attributes.put( "sn", sn );
        attributes.put( "description", cn + " is a person." );

        return attributes;
    }


    /**
     * Create attributes for a organizational unit entry.
     */
    protected Attributes getOrganizationalUnitAttributes( String ou )
    {
        Attributes attributes = new AttributesImpl();
        Attribute attribute = new AttributeImpl( "objectClass" );
        attribute.add( "top" );
        attribute.add( "organizationalUnit" );
        attributes.put( attribute );
        attributes.put( "ou", ou );
        attributes.put( "description", ou + " is an organizational unit." );
   
        return attributes;
    }
   
   
    /**
     * Create context
     */
    @Before public void setUp() throws Exception
    {
        super.setUp();

        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put( "java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory" );
        env.put( "java.naming.provider.url", "ldap://localhost:" + port + "/ou=system" );
        env.put( "java.naming.security.principal", "uid=admin,ou=system" );
        env.put( "java.naming.security.credentials", "secret" );
        env.put( "java.naming.security.authentication", "simple" );
        ctx = new InitialLdapContext( env, null );
        assertNotNull( ctx );
    }


    /**
     * Close context
     */
    @After public void tearDown() throws Exception
    {
        ctx.close();
        ctx = null;

        super.tearDown();
    }


    /**
     * Just a little test to check wether opening the connection succeeds.
     *
    @Test public void testSetUpTearDown()
    {
        assertNotNull( ctx );
    }


    /**
     * Modify Rdn of an entry, delete its old rdn value.
     * 
     * @throws NamingException
     *
    @Test public void testModifyRdnAndDeleteOld() throws NamingException
    {
        // Create a person, cn value is rdn
        String oldCn = "Myra Ellen Amos";
        String oldRdn = "cn=" + oldCn;
        Attributes attributes = this.getPersonAttributes( "Amos", oldCn );
        ctx.createSubcontext( oldRdn, attributes );

        // modify Rdn
        String newCn = "Tori Amos";
        String newRdn = "cn=" + newCn;
        ctx.addToEnvironment( "java.naming.ldap.deleteRDN", "true" );
        ctx.rename( oldRdn, newRdn );

        // Check, whether old Entry does not exists
        try
        {
            ctx.lookup( oldRdn );
            fail( "Entry must not exist" );
        }
        catch ( NameNotFoundException ignored )
        {
            // expected behaviour
            assertTrue( true );
        }

        // Check, whether new Entry exists
        DirContext tori = ( DirContext ) ctx.lookup( newRdn );
        assertNotNull( tori );

        // Check values of cn
        Attribute cn = tori.getAttributes( "" ).get( "cn" );
        assertTrue( cn.contains( newCn ) );
        assertTrue( !cn.contains( oldCn ) ); // old value is gone
        assertEquals( 1, cn.size() );

        // Remove entry (use new rdn)
        ctx.unbind( newRdn );
    }

    /**
     * Modify Rdn of an entry, without deleting its old rdn value.
     * 
     * The JNDI property is set with 'False'
     * 
     * @throws NamingException
     *
    @Test public void testModifyRdnAndDontDeleteOldFalse() throws NamingException
    {
        // Create a person, cn value is rdn
        String oldCn = "Myra Ellen Amos";
        String oldRdn = "cn=" + oldCn;
        Attributes attributes = this.getPersonAttributes( "Amos", oldCn );
        ctx.createSubcontext( oldRdn, attributes );

        // modify Rdn
        String newCn = "Tori Amos";
        String newRdn = "cn=" + newCn;
        ctx.addToEnvironment( "java.naming.ldap.deleteRDN", "False" );
        ctx.rename( oldRdn, newRdn );

        // Check, whether old Entry does not exists
        try
        {
            ctx.lookup( oldRdn );
            fail( "Entry must not exist" );
        }
        catch ( NameNotFoundException ignored )
        {
            // expected behaviour
            assertTrue( true );
        }

        // Check, whether new Entry exists
        DirContext tori = ( DirContext ) ctx.lookup( newRdn );
        assertNotNull( tori );

        // Check values of cn
        Attribute cn = tori.getAttributes( "" ).get( "cn" );
        assertTrue( cn.contains( newCn ) );
        assertTrue( cn.contains( oldCn ) ); // old value is gone
        assertEquals( 2, cn.size() );

        // Remove entry (use new rdn)
        ctx.unbind( newRdn );
    }

    /**
     * Modify Rdn of an entry, keep its old rdn value.
     * 
     * @throws NamingException
     *
    @Test public void testModifyRdnAndKeepOld() throws NamingException
    {
        // Create a person, cn value is rdn
        String oldCn = "Myra Ellen Amos";
        String oldRdn = "cn=" + oldCn;
        Attributes attributes = this.getPersonAttributes( "Amos", oldCn );
        ctx.createSubcontext( oldRdn, attributes );

        // modify Rdn
        String newCn = "Tori Amos";
        String newRdn = "cn=" + newCn;
        ctx.addToEnvironment( "java.naming.ldap.deleteRDN", "false" );
        ctx.rename( oldRdn, newRdn );

        // Check, whether old entry does not exist
        try
        {
            ctx.lookup( oldRdn );
            fail( "Entry must not exist" );
        }
        catch ( NameNotFoundException ignored )
        {
            // expected behaviour
            assertTrue( true );
        }

        // Check, whether new entry exists
        DirContext tori = ( DirContext ) ctx.lookup( newRdn );
        assertNotNull( tori );

        // Check values of cn
        Attribute cn = tori.getAttributes( "" ).get( "cn" );
        assertTrue( cn.contains( newCn ) );
        assertTrue( cn.contains( oldCn ) ); // old value is still there
        assertEquals( 2, cn.size() );

        // Remove entry (use new rdn)
        ctx.unbind( newRdn );
    }


    /**
     * Modify Rdn of an entry, delete its old rdn value. Here, the rdn attribute
     * cn has another value as well.
     * 
     * @throws NamingException
     *
    @Test public void testModifyRdnAndDeleteOldVariant() throws NamingException
    {
        // Create a person, cn value is rdn
        String oldCn = "Myra Ellen Amos";
        String oldRdn = "cn=" + oldCn;
        Attributes attributes = this.getPersonAttributes( "Amos", oldCn );

        // add a second cn value
        String alternateCn = "Myra E. Amos";
        Attribute cn = attributes.get( "cn" );
        cn.add( alternateCn );
        assertEquals( 2, cn.size() );

        ctx.createSubcontext( oldRdn, attributes );

        // modify Rdn
        String newCn = "Tori Amos";
        String newRdn = "cn=" + newCn;
        ctx.addToEnvironment( "java.naming.ldap.deleteRDN", "true" );
        ctx.rename( oldRdn, newRdn );

        // Check, whether old Entry does not exist anymore
        try
        {
            ctx.lookup( oldRdn );
            fail( "Entry must not exist" );
        }
        catch ( NameNotFoundException ignored )
        {
            // expected behaviour
            assertTrue( true );
        }

        // Check, whether new Entry exists
        DirContext tori = ( DirContext ) ctx.lookup( newRdn );
        assertNotNull( tori );

        // Check values of cn
        cn = tori.getAttributes( "" ).get( "cn" );
        assertTrue( cn.contains( newCn ) );
        assertTrue( !cn.contains( oldCn ) ); // old value is gone
        assertTrue( cn.contains( alternateCn ) ); // alternate value is still available
        assertEquals( 2, cn.size() );

        // Remove entry (use new rdn)
        ctx.unbind( newRdn );
    }


    /**
     * Modify DN of an entry, changing RDN from cn to sn.
     * 
     * @throws NamingException
     *
    @Test public void testModifyRdnDifferentAttribute() throws NamingException
    {

        // Create a person, cn value is rdn
        String cnVal = "Tori Amos";
        String snVal = "Amos";
        String oldRdn = "cn=" + cnVal;
        Attributes attributes = this.getPersonAttributes( snVal, cnVal );
        ctx.createSubcontext( oldRdn, attributes );

        // modify Rdn from cn=... to sn=...
        String newRdn = "sn=" + snVal;
        ctx.addToEnvironment( "java.naming.ldap.deleteRDN", "false" );
        ctx.rename( oldRdn, newRdn );

        // Check, whether old Entry does not exists
        try
        {
            ctx.lookup( oldRdn );
            fail( "Entry must not exist" );
        }
        catch ( NameNotFoundException ignored )
        {
            // expected behaviour
        }

        // Check, whether new Entry exists
        DirContext tori = ( DirContext ) ctx.lookup( newRdn );
        assertNotNull( tori );

        // Check values of cn and sn
        // especially the number of cn and sn occurences
        Attribute cn = tori.getAttributes( "" ).get( "cn" );
        assertTrue( cn.contains( cnVal ) );
        assertEquals( "Number of cn occurences", 1, cn.size() );
        Attribute sn = tori.getAttributes( "" ).get( "sn" );
        assertTrue( sn.contains( snVal ) );
        assertEquals( "Number of sn occurences", 1, sn.size() );

        // Remove entry (use new rdn)
        ctx.unbind( newRdn );
    }
    
    
              
    /**
     * Test for DIRSERVER-1086.
     * Modify Rdn of an entry that has a child entry, delete its old rdn value.
     * Ensure that the tree is not broken.
     *
     * @throws NamingException
     */
    @Test
    public void testModifyRdnAndDeleteOldWithChild() throws NamingException
    {
        // Create an organizational unit, ou value is rdn
        String oldOu = "Writers";
        String oldRdn = "ou=" + oldOu;
        Attributes attributes = this.getOrganizationalUnitAttributes( oldOu );
        DirContext createdCtx = ctx.createSubcontext( oldRdn, attributes );

        // Create a child
        String childCn = "Tori Amos";
        String childRdn = "cn=" + childCn;
        Attributes childAttributes = this.getPersonAttributes( "Amos", childCn );
        createdCtx.createSubcontext( childRdn, childAttributes );

        // modify Rdn
        String newOu = "Singers";
        String newRdn = "ou=" + newOu;
        ctx.addToEnvironment( "java.naming.ldap.deleteRDN", "true" );
        ctx.rename( oldRdn, newRdn );

        // Check, whether old Entry does not exists
        try
        {
            ctx.lookup( oldRdn );
            fail( "Entry must not exist" );
        }
        catch ( NameNotFoundException ignored )
        {
            // expected behaviour
            assertTrue( true );
        }

        // Check, whether new Entry exists
        DirContext org = ( DirContext ) ctx.lookup( newRdn );
        assertNotNull( org );

        // Check values of ou
        Attribute ou = org.getAttributes( "" ).get( "ou" );
        assertTrue( ou.contains( newOu ) );
        assertTrue( !ou.contains( oldOu ) ); // old value is gone
        assertEquals( 1, ou.size() );

        // Perform a search under renamed ou and check whether exactly one child entry exist
        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        searchControls.setReturningAttributes( new String[]
            { "objectClass" } );
        NamingEnumeration<SearchResult> results = org.search( "", "(objectClass=*)", searchControls );
        assertTrue( results.hasMore() );
        results.next();
        assertTrue( !results.hasMore() );

        // Check whether Tori exists
        DirContext tori = ( DirContext ) org.lookup( childRdn );
        assertNotNull( tori );

        // Remove entry (use new rdn)
        ctx.unbind( childRdn + "," + newRdn );
        ctx.unbind( newRdn );
    }


    /**
     * Test for DIRSERVER-1096.
     * Modify the RDN of an entry with an escaped new RDN. 
     * Ensure that the attribute itself contains the not-escaped value.
     *
     * @throws Exception
     *
    @Test
    public void testModifyRdnWithEncodedNewRdn() throws Exception
    {
        // Create a person, cn value is rdn
        String cnVal = "Tori Amos";
        String snVal = "Amos";
        String oldRdn = "cn=" + cnVal;
        Attributes attributes = this.getPersonAttributes( snVal, cnVal );
        ctx.createSubcontext( oldRdn, attributes );

        // modify Rdn from cn=Tori Amos to cn=<A Umlaut>\+
        String newCnVal = new String( new byte[]
            { ( byte ) 0xC3, ( byte ) 0x84, '+' }, "UTF-8" );
        String newCnEscapedVal = new String( new byte[]
            { ( byte ) 0xC3, ( byte ) 0x84, '\\', '+' }, "UTF-8" );
        ctx.addToEnvironment( "java.naming.ldap.deleteRDN", "true" );
        String newRdn = "cn=" + newCnEscapedVal;
        ctx.rename( oldRdn, newRdn );

        // Check, whether old Entry does not exists
        try
        {
            ctx.lookup( oldRdn );
            fail( "Entry must not exist" );
        }
        catch ( NameNotFoundException ignored )
        {
            // expected behaviour
        }

        // Check, whether new Entry exists
        DirContext newCtx = ( DirContext ) ctx.lookup( newRdn );
        assertNotNull( newCtx );

        // Check that cn contains the unecnoded value
        Attribute cn = newCtx.getAttributes( "" ).get( "cn" );
        assertEquals( "Number of cn occurences", 1, cn.size() );
        assertTrue( cn.contains( newCnVal ) );

        // Remove entry (use new rdn)
        ctx.unbind( newRdn );
    }
*/
}
