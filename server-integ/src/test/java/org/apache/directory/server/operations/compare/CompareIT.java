/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */
package org.apache.directory.server.operations.compare;


import javax.naming.NamingEnumeration;
import javax.naming.ReferralException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import netscape.ldap.LDAPAttribute;
import netscape.ldap.LDAPConnection;
import netscape.ldap.LDAPConstraints;
import netscape.ldap.LDAPControl;
import netscape.ldap.LDAPException;
import netscape.ldap.LDAPResponse;
import netscape.ldap.LDAPResponseListener;

import org.apache.directory.server.core.integ.Level;
import org.apache.directory.server.core.integ.annotations.ApplyLdifs;
import org.apache.directory.server.core.integ.annotations.CleanupLevel;
import org.apache.directory.server.integ.SiRunner;
import org.apache.directory.server.ldap.LdapService;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.directory.server.integ.ServerIntegrationUtils.getWiredConnection;
import static org.apache.directory.server.integ.ServerIntegrationUtils.getWiredContextThrowOnRefferal;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * Tests the server to make sure standard compare operations work properly.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
@RunWith ( SiRunner.class ) 
@CleanupLevel ( Level.SUITE )
@ApplyLdifs( {
    // Entry # 1
    "dn: uid=akarasulu,ou=users,ou=system\n" +
    "objectClass: uidObject\n" +
    "objectClass: person\n" +
    "objectClass: top\n" +
    "uid: akarasulu\n" +
    "cn: Alex Karasulu\n" +
    "sn: karasulu\n\n" + 
    // Entry # 2
    "dn: ou=Computers,uid=akarasulu,ou=users,ou=system\n" +
    "objectClass: organizationalUnit\n" +
    "objectClass: top\n" +
    "ou: computers\n" +
    "description: Computers for Alex\n" +
    "seeAlso: ou=Machines,uid=akarasulu,ou=users,ou=system\n\n" + 
    // Entry # 3
    "dn: uid=akarasuluref,ou=users,ou=system\n" +
    "objectClass: uidObject\n" +
    "objectClass: referral\n" +
    "objectClass: top\n" +
    "uid: akarasuluref\n" +
    "ref: ldap://localhost:10389/uid=akarasulu,ou=users,ou=system\n" + 
    "ref: ldap://foo:10389/uid=akarasulu,ou=users,ou=system\n" +
    "ref: ldap://bar:10389/uid=akarasulu,ou=users,ou=system\n\n"
    }
)
public class CompareIT
{
    private static final Logger LOG = LoggerFactory.getLogger( CompareIT.class );
    
    public static LdapService ldapService;
    

    /**
     * Tests normal compare operation on normal non-referral entries without 
     * the ManageDsaIT control.
     */
    @Test
    public void testNormalCompare() throws Exception
    {
        LDAPConnection conn = getWiredConnection( ldapService );
        
        // comparison success
        LDAPAttribute attribute = new LDAPAttribute( "sn", "karasulu" );
        assertTrue( conn.compare( "uid=akarasulu,ou=users,ou=system", attribute ) );

        // comparison failure
        attribute = new LDAPAttribute( "sn", "lecharny" );
        assertFalse( conn.compare( "uid=akarasulu,ou=users,ou=system", attribute ) );
        
        conn.disconnect();
    }
    
    
    /**
     * Tests normal compare operation on normal non-referral entries without 
     * the ManageDsaIT control using an attribute that does not exist in the
     * entry.
     */
    @Test
    public void testNormalCompareMissingAttribute() throws Exception
    {
        LDAPConnection conn = getWiredConnection( ldapService );
        
        // comparison success
        LDAPAttribute attribute = new LDAPAttribute( "sn", "karasulu" );
        assertTrue( conn.compare( "uid=akarasulu,ou=users,ou=system", attribute ) );

        // non-existing attribute
        attribute = new LDAPAttribute( "mail", "akarasulu@apache.org" );
        
        try
        {
            conn.compare( "uid=akarasulu,ou=users,ou=system", attribute );
            fail( "Should never get here" );
        }
        catch ( LDAPException e )
        {
            assertEquals( ResultCodeEnum.NO_SUCH_ATTRIBUTE.getValue(), e.getLDAPResultCode() ); 
        }
        
        conn.disconnect();
    }
    
    
    /**
     * Tests compare operation on referral entry with the ManageDsaIT control.
     */
    @Test
    public void testOnReferralWithManageDsaITControl() throws Exception
    {
        LDAPConnection conn = getWiredConnection( ldapService );
        LDAPConstraints constraints = new LDAPConstraints();
        constraints.setClientControls( new LDAPControl( LDAPControl.MANAGEDSAIT, true, new byte[0] ) );
        constraints.setServerControls( new LDAPControl( LDAPControl.MANAGEDSAIT, true, new byte[0] ) );
        conn.setConstraints( constraints );
        
        // comparison success
        LDAPAttribute attribute = new LDAPAttribute( "uid", "akarasuluref" );
        assertTrue( conn.compare( "uid=akarasuluref,ou=users,ou=system", attribute, constraints ) );

        // comparison failure
        attribute = new LDAPAttribute( "uid", "elecharny" );
        assertFalse( conn.compare( "uid=akarasuluref,ou=users,ou=system", attribute, constraints ) );
        
        conn.disconnect();
    }
    
    
    /**
     * Tests compare operation on normal and referral entries without the 
     * ManageDsaIT control. Referrals are sent back to the client with a
     * non-success result code.
     */
    @Test
    public void testOnReferral() throws Exception
    {
        LDAPConnection conn = getWiredConnection( ldapService );
        LDAPConstraints constraints = new LDAPConstraints();
        constraints.setReferrals( false );
        conn.setConstraints( constraints );
        
        // comparison success
        LDAPAttribute attribute = new LDAPAttribute( "uid", "akarasulu" );
        assertTrue( conn.compare( "uid=akarasulu,ou=users,ou=system", attribute, constraints ) );

        // referrals failure
        attribute = new LDAPAttribute( "uid", "akarasulu" );
        LDAPResponseListener listener = null;
        LDAPResponse response = null;

        listener = conn.compare( "uid=akarasuluref,ou=users,ou=system", attribute, null, constraints );
        response = listener.getResponse();
        assertEquals( ResultCodeEnum.REFERRAL.getValue(), response.getResultCode() );

        assertEquals( "ldap://localhost:10389/uid=akarasulu,ou=users,ou=system", response.getReferrals()[0] );
        assertEquals( "ldap://foo:10389/uid=akarasulu,ou=users,ou=system", response.getReferrals()[1] );
        assertEquals( "ldap://bar:10389/uid=akarasulu,ou=users,ou=system", response.getReferrals()[2] );

        conn.disconnect();
    }
    
    
    /**
     * Tests compare operation on normal and referral entries without the 
     * ManageDsaIT control using JNDI instead of the Netscape API. Referrals 
     * are sent back to the client with a non-success result code.
     */
    @Test
    public void testThrowOnReferralWithJndi() throws Exception
    {
        LdapContext ctx = getWiredContextThrowOnRefferal( ldapService );
        SearchControls controls = new SearchControls();
        controls.setReturningAttributes( new String[0] );
        controls.setSearchScope( SearchControls.OBJECT_SCOPE );
        
        // comparison success
        NamingEnumeration<SearchResult> answer = ctx.search( "uid=akarasulu,ou=users,ou=system", 
            "(uid=akarasulu)", controls );
        assertTrue( answer.hasMore() );
        SearchResult result = answer.next();
        assertEquals( "", result.getName() );
        assertEquals( 0, result.getAttributes().size() );
        assertFalse( answer.hasMore() );
        answer.close();

        // referrals failure
        try
        {
            answer = ctx.search( "uid=akarasuluref,ou=users,ou=system", 
                "(uid=akarasuluref)", controls );
            fail( "Should never get here" );
        }
        catch ( ReferralException e )
        {
            // seems JNDI only returns the first referral URL and not all so we test for it
            assertEquals( "ldap://localhost:10389/uid=akarasulu,ou=users,ou=system", e.getReferralInfo() );
        }

        ctx.close();
    }
    
    
    /**
     * Check that operation are not executed if we are now allowed to bind
     * anonymous
     * @throws LDAPException
     */
    @Test
    public void testCompareWithoutAuthentication() throws LDAPException
    {
        ldapService.getDirectoryService().setAllowAnonymousAccess( false );
        LDAPConnection conn = new LDAPConnection();
        conn.connect( "localhost", ldapService.getPort() );
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
     * Tests referral handling when an ancestor is a referral.
     */
    @Test 
    public void testAncestorReferral() throws Exception
    {
        LOG.debug( "" );

        LDAPConnection conn = getWiredConnection( ldapService );
        LDAPConstraints constraints = new LDAPConstraints();
        conn.setConstraints( constraints );

        // referrals failure
        LDAPAttribute attribute = new LDAPAttribute( "ou", "Computers" );
        LDAPResponseListener listener = null;
        LDAPResponse response = null;

        listener = conn.compare( "ou=Computers,uid=akarasuluref,ou=users,ou=system", attribute, null, constraints );
        response = listener.getResponse();
        assertEquals( ResultCodeEnum.REFERRAL.getValue(), response.getResultCode() );

        assertEquals( "ldap://localhost:10389/ou=Computers,uid=akarasulu,ou=users,ou=system", response.getReferrals()[0] );
        assertEquals( "ldap://foo:10389/ou=Computers,uid=akarasulu,ou=users,ou=system", response.getReferrals()[1] );
        assertEquals( "ldap://bar:10389/ou=Computers,uid=akarasulu,ou=users,ou=system", response.getReferrals()[2] );

        conn.disconnect();
    }
}