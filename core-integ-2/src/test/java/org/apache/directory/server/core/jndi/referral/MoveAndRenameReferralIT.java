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
package org.apache.directory.server.core.jndi.referral;

import static org.apache.directory.server.core.integ.IntegrationUtils.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Set;

import javax.naming.NameAlreadyBoundException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.ReferralException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.ldap.LdapContext;

import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.DefaultServerEntry;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.integ.CiRunner;
import org.apache.directory.server.core.integ.annotations.ApplyLdifs;
import org.apache.directory.shared.ldap.exception.LdapNamingException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Tests the referral handling functionality for the Modify operation 
 * within the server's core.
 * 
 * All the tests are described on this page :
 * http://cwiki.apache.org/confluence/display/DIRxSRVx11/Referral+Handling+Changes
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 691179 $
 */
@RunWith ( CiRunner.class )
@ApplyLdifs( {
    // Root
    "dn: c=WW,ou=system\n" +
    "objectClass: country\n" +
    "objectClass: top\n" +
    "c: WW\n\n" +
    
    // Sub-root #1
    "dn: o=MNN,c=WW,ou=system\n" +
    "objectClass: organization\n" +
    "objectClass: top\n" +
    "o: MNN\n\n" +
    
    // Sub-root #2
    "dn: o=PNN,c=WW,ou=system\n" +
    "objectClass: organization\n" +
    "objectClass: top\n" +
    "o: PNN\n\n" +

    // Referral #1
    "dn: ou=Roles,o=MNN,c=WW,ou=system\n" +
    "objectClass: extensibleObject\n" +
    "objectClass: referral\n" +
    "objectClass: top\n" +
    "ou: Roles\n" +
    "ref: ldap://hostd/ou=Roles,dc=apache,dc=org\n\n" +
    
    // Referral #2
    "dn: ou=People,o=MNN,c=WW,ou=system\n" +
    "objectClass: extensibleObject\n" +
    "objectClass: referral\n" +
    "objectClass: top\n" +
    "ou: People\n" +
    "ref: ldap://hostb/OU=People,DC=example,DC=com\n" +
    "ref: ldap://hostc/OU=People,O=MNN,C=WW\n\n" +
    
    // Entry # 1
    "dn: cn=Alex Karasulu,o=MNN,c=WW,ou=system\n" +
    "objectClass: person\n" +
    "objectClass: top\n" +
    "cn: Alex Karasulu\n" +
    "sn: akarasulu\n\n" +
    
    // Entry # 2
    "dn: cn=Alex,o=MNN,c=WW,ou=system\n" +
    "objectClass: person\n" +
    "objectClass: top\n" +
    "cn: Alex\n" +
    "sn: akarasulu\n\n" +
    
    // Entry # 3
    "dn: cn=Emmanuel,o=PNN,c=WW,ou=system\n" +
    "objectClass: person\n" +
    "objectClass: top\n" +
    "cn: Emmanuel\n" +
    "sn: elecharny\n\n"
    }
)
public class MoveAndRenameReferralIT
{
    /** The directory service */
    public static DirectoryService service;

    /** The Context we are using to inject entries with JNDI */
    LdapContext MNNCtx;
    LdapContext PNNCtx;
    LdapContext WWCtx;
    
    /** The entries we are using to do the tests */
    Attributes userEntry;
    ServerEntry serverEntry;
    
    @Before
    public void setUp() throws Exception
    {
        MNNCtx = getContext( ServerDNConstants.ADMIN_SYSTEM_DN, service, "o=MNN,c=WW,ou=system" );
        PNNCtx = getContext( ServerDNConstants.ADMIN_SYSTEM_DN, service, "o=PNN,c=WW,ou=system" );
        WWCtx = getContext( ServerDNConstants.ADMIN_SYSTEM_DN, service, "c=WW,ou=system" );

        // JNDI entry
        userEntry = new BasicAttributes( "objectClass", "top", true );
        userEntry.get( "objectClass" ).add( "person" );
        userEntry.put( "sn", "elecharny" );
        userEntry.put( "cn", "Emmanuel Lecharny" );
        
        // Core API entry
        LdapDN dn = new LdapDN( "cn=Emmanuel Lecharny, ou=apache, ou=people, o=MNN, c=WW, ou=system" );
        serverEntry = new DefaultServerEntry( service.getSchemaManager(), dn );

        serverEntry.put( "ObjectClass", "top", "person" );
        serverEntry.put( "sn", "elecharny" );
        serverEntry.put( "cn", "Emmanuel Lecharny" );
    }

    /**
     * Test a move and rename of a non existing entry (not a referral), with no referral 
     * in its ancestor.
     */
    @Test
    public void testMoveAndRenNotExistingSuperiorNoReferralAncestor() throws Exception
    {
        try
        {
            WWCtx.rename( "cn=Emmanuel Lecharny,o=MNN", "cn=Emmanuel,o=PNN" );
            fail();
        }
        catch ( NameNotFoundException nnfe )
        {
            assertTrue( true );
        }
    }

    
    /**
     * Test a move and rename of an entry having some referral ancestor,
     * using JNDI throw.
     */
    @Test
    public void testMoveAndRenameSuperiorHasReferralAncestorJNDIThrow() throws Exception
    {
        try
        {
            MNNCtx.addToEnvironment( DirContext.REFERRAL, "throw" );
            MNNCtx.rename( "cn=Emmanuel Lecharny,ou=apache,ou=Roles", "cn=Alex,o=PNN,c=WW,ou=system" );
            fail();
        }
        catch ( ReferralException re )
        {
            int nbRefs = 0;
            Set<String> expectedRefs = new HashSet<String>();
            expectedRefs.add( "ldap://hostd/cn=Emmanuel%20Lecharny,ou=apache,ou=Roles,dc=apache,dc=org" );
            
            do 
            {
                String ref = (String)re.getReferralInfo();
                
                assertTrue( expectedRefs.contains( ref ) );
                nbRefs ++;
            }
            while ( re.skipReferral() );
            
            assertEquals( 1, nbRefs );
        }
    }

    
    /**
     * Test a move and rename of an entry having some referral ancestor,
     * using JNDI ignore.
     */
    @Test
    public void testMoveAndRenameSuperiorHasReferralAncestorJNDIIgnore() throws Exception
    {
        try
        {
            MNNCtx.addToEnvironment( DirContext.REFERRAL, "ignore" );
            MNNCtx.rename( "cn=Emmanuel Lecharny,ou=apache,ou=Roles", "cn=Alex,o=PNN,c=WW,ou=system" );
            fail();
        }
        catch ( PartialResultException pre )
        {
            assertTrue( true );
        }
    }

    
    /**
     * Test a move and rename of an entry having some referral ancestor, 
     * using the Core API without the ManageDsaIT flag.
     */
    @Test
    public void testMoveAndRenameEntrySuperiorHasReferralAncestorCoreAPIWithoutManageDsaIT() throws Exception
    {
        CoreSession coreSession = service.getAdminSession();
        LdapDN dn = new LdapDN( "cn=Emmanuel Lecharny,ou=apache,ou=Roles,o=MNN,c=WW,ou=system" );
        LdapDN newParent = new LdapDN( "o=PNN,c=WW,ou=system" );
        Rdn newRdn = new Rdn( "cn=Alex" );
        
        try
        {
            coreSession.moveAndRename( dn, newParent, newRdn, false, false );
            fail();
        }
        catch ( ReferralException re )
        {
            int nbRefs = 0;
            Set<String> expectedRefs = new HashSet<String>();
            expectedRefs.add( "ldap://hostd/cn=Emmanuel%20Lecharny,ou=apache,ou=Roles,dc=apache,dc=org" );
            
            do 
            {
                String ref = (String)re.getReferralInfo();
                
                assertTrue( expectedRefs.contains( ref ) );
                nbRefs ++;
            }
            while ( re.skipReferral() );
            
            assertEquals( 1, nbRefs );
        }
    }

    
    /**
     * Test a move and rename of an entry having some referral ancestor, 
     * using the Core API with the ManageDsaIT flag
     */
    @Test
    public void testMoveAndRenameSuperiorHasReferralAncestorCoreAPIWithManageDsaIT() throws Exception
    {
        CoreSession coreSession = service.getAdminSession();
        LdapDN dn = new LdapDN( "cn=Emmanuel Lecharny,ou=apache,ou=Roles,o=MNN,c=WW,ou=system" );
        LdapDN newParent = new LdapDN( "o=PNN,c=WW,ou=system" ); 
        Rdn newRdn = new Rdn( "cn=Alex" );
        
        try
        {
            coreSession.moveAndRename( dn, newParent, newRdn, false, true );
            fail();
        }
        catch ( PartialResultException pre )
        {
            assertTrue( true );
        }
    }
    
    
    /**
     * Test a move and rename of an existing entry (not a referral), with no referral 
     * in its ancestor.
     */
    @Test
    public void testMoveAndRenameExistingSuperiorNoReferralAncestor() throws Exception
    {
        // First check that the object exists
        Object moved = MNNCtx.lookup( "cn=Alex" );
        assertNotNull( moved );

        // and that the target entry is not present
        try
        {
            moved = PNNCtx.lookup( "cn=BugsBunny" );
            fail();
        }
        catch ( NameNotFoundException nnfe )
        {
            assertTrue( true );
        }

        WWCtx.rename( "cn=Alex,o=MNN", "cn=BugsBunny,o=PNN" );
        
        // Check that the entry has been moved
        moved = PNNCtx.lookup( "cn=BugsBunny" );
        assertNotNull( moved );

        // and that the original entry is not present anymore
        try
        {
            moved = MNNCtx.lookup( "cn=Alex" );
            fail();
        }
        catch ( NameNotFoundException nnfe )
        {
            assertTrue( true );
        }
    }

    
    /**
     * Test a move and rename of an existing entry (not a referral), to an 
     * entry with a referral in its ancestor
     */
    @Test
    public void testMoveAndRenameExistingToEntryWithReferralAncestor() throws Exception
    {
        try
        {
            MNNCtx.rename( "cn=Alex", "cn=Emmanuel,ou=Roles" );
        }
        catch ( NamingException ne )
        {
            assertTrue( true );
            assertEquals( ResultCodeEnum.AFFECTS_MULTIPLE_DSAS, ((LdapNamingException)ne).getResultCode() );
        }
    }

    
    /**
     * Test a move and rename of an existing entry (not a referral), to an 
     * entry which already exists.
     */
    @Test
    public void testMoveAndRenameExistingToAnotherExistingEntry() throws Exception
    {
        try
        {
            WWCtx.rename( "cn=Alex,o=MNN", "cn=Emmanuel,o=PNN" );
        }
        catch ( NameAlreadyBoundException ne )
        {
            assertTrue( true );
        }
    }
    /**
     * Test a move and rename of a referral entry, using JNDI throw.
     */
    @Test
    public void testMoveAndRenameIsReferralJNDIThrow() throws Exception
    {
        try
        {
            MNNCtx.addToEnvironment( DirContext.REFERRAL, "throw" );
            MNNCtx.rename( "cn=Emmanuel Lecharny,ou=Roles", "cn=Alex,o=PNN,c=WW,ou=system" );
            fail();
        }
        catch ( ReferralException re )
        {
            int nbRefs = 0;
            Set<String> expectedRefs = new HashSet<String>();
            expectedRefs.add( "ldap://hostd/cn=Emmanuel%20Lecharny,ou=Roles,dc=apache,dc=org" );
            
            do 
            {
                String ref = (String)re.getReferralInfo();
                
                assertTrue( expectedRefs.contains( ref ) );
                nbRefs ++;
            }
            while ( re.skipReferral() );
            
            assertEquals( 1, nbRefs );
        }
    }

    
    /**
     * Test a move and rename of a referral entry, using JNDI ignore.
     */
    @Test
    public void testMoveAndRenameIsReferralJNDIIgnore() throws Exception
    {
        try
        {
            MNNCtx.addToEnvironment( DirContext.REFERRAL, "ignore" );
            MNNCtx.rename( "cn=Emmanuel Lecharny,ou=Roles", "cn=Alex,o=PNN,c=WW,ou=system" );
            fail();
        }
        catch ( PartialResultException pre )
        {
            assertTrue( true );
        }
    }

    
    /**
     * Test a move and rename of a referral entry, using the Core API 
     * without the ManageDsaIT flag.
     */
    @Test
    public void testMoveAndRenameIsReferralCoreAPIWithoutManageDsaIT() throws Exception
    {
        CoreSession coreSession = service.getAdminSession();
        LdapDN dn = new LdapDN( "cn=Emmanuel Lecharny,ou=Roles,o=MNN,c=WW,ou=system" );
        LdapDN newParent = new LdapDN( "o=PNN,c=WW,ou=system" );
        Rdn newRdn = new Rdn( "cn=Alex" );
        
        try
        {
            coreSession.moveAndRename( dn, newParent, newRdn, false, false );
            fail();
        }
        catch ( ReferralException re )
        {
            int nbRefs = 0;
            Set<String> expectedRefs = new HashSet<String>();
            expectedRefs.add( "ldap://hostd/cn=Emmanuel%20Lecharny,ou=Roles,dc=apache,dc=org" );
            
            do 
            {
                String ref = (String)re.getReferralInfo();
                
                assertTrue( expectedRefs.contains( ref ) );
                nbRefs ++;
            }
            while ( re.skipReferral() );
            
            assertEquals( 1, nbRefs );
        }
    }

    
    /**
     * Test a move and rename of a referral entry, using the Core API 
     * with the ManageDsaIT flag
     */
    @Test
    public void testMoveAndRenameIsReferralCoreAPIWithManageDsaIT() throws Exception
    {
        CoreSession coreSession = service.getAdminSession();
        LdapDN dn = new LdapDN( "cn=Emmanuel Lecharny,ou=apache,ou=Roles,o=MNN,c=WW,ou=system" );
        LdapDN newParent = new LdapDN( "o=PNN,c=WW,ou=system" ); 
        Rdn newRdn = new Rdn( "cn=Alex" );
        
        try
        {
            coreSession.moveAndRename( dn, newParent, newRdn, false, true );
            fail();
        }
        catch ( PartialResultException pre )
        {
            assertTrue( true );
        }
    }
}