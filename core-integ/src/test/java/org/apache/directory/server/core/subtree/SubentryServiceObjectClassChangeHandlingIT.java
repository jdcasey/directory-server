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

package org.apache.directory.server.core.subtree;


import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.integ.CiRunner;
import static org.apache.directory.server.core.integ.IntegrationUtils.getSystemContext;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.AttributesImpl;
import org.apache.directory.shared.ldap.message.ModificationItemImpl;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import java.util.HashMap;
import java.util.Map;


/**
 * Testcases for the SubentryInterceptor. Investigation on handling Subtree Refinement
 * Selection Membership upon objectClass attribute value changes.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
@RunWith ( CiRunner.class )
public class SubentryServiceObjectClassChangeHandlingIT 
{
    public static DirectoryService service;


    public Attributes getTestEntry( String cn )
    {
        Attributes entry = new AttributesImpl();
        Attribute objectClass = new AttributeImpl( "objectClass" );
        objectClass.add( "top" );
        objectClass.add( "person" );
        entry.put( objectClass );
        entry.put( "cn", cn );
        entry.put( "sn", cn );
        return entry;
    }


    public Attributes getModsForIntroducingNewOC() throws NamingException
    {
        Attributes changes = new AttributesImpl();
        Attribute objectClass = new AttributeImpl( "objectClass" );
        objectClass.add( "organizationalPerson" );
        changes.put( objectClass );
        changes.put( "ou", "Test Organizational Unit" );
        return changes;
    }


    public Attributes getCollectiveAttributeTestSubentry( String cn )
    {
        Attributes subentry = new AttributesImpl();
        Attribute objectClass = new AttributeImpl( "objectClass" );
        objectClass.add( "top" );
        objectClass.add( SchemaConstants.SUBENTRY_OC );
        objectClass.add( "collectiveAttributeSubentry" );
        subentry.put( objectClass );
        subentry.put( "subtreeSpecification", "{ specificationFilter item:organizationalPerson }" );
        subentry.put( "c-o", "Test Org" );
        subentry.put( "cn", cn );
        return subentry;
    }


    public void addAdministrativeRoles() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        Attribute attribute = new AttributeImpl( "administrativeRole" );
        attribute.add( "autonomousArea" );
        attribute.add( "collectiveAttributeSpecificArea" );
        ModificationItemImpl item = new ModificationItemImpl( DirContext.ADD_ATTRIBUTE, attribute );
        sysRoot.modifyAttributes( "", new ModificationItemImpl[]
            { item } );
    }


    public Map<String, Attributes> getAllEntries() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );
        Map<String, Attributes> resultMap = new HashMap<String, Attributes>();
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setReturningAttributes( new String[]
            { "+", "*" } );
        NamingEnumeration<SearchResult> results = sysRoot.search( "", "(objectClass=*)", controls );
        
        while ( results.hasMore() )
        {
            SearchResult result = results.next();
            resultMap.put( result.getName(), result.getAttributes() );
        }
        return resultMap;
    }
    

    @Test
    public void testTrackingOfOCChangesInSubentryServiceModifyRoutine() throws Exception
    {
        LdapContext sysRoot = getSystemContext( service );
        addAdministrativeRoles();
        sysRoot.createSubcontext( "cn=collectiveAttributeTestSubentry",
            getCollectiveAttributeTestSubentry( "collectiveAttributeTestSubentry" ) );
        sysRoot.createSubcontext( "cn=testEntry", getTestEntry( "testEntry" ) );

        //----------------------------------------------------------------------

        Map<String, Attributes> results = getAllEntries();
        Attributes testEntry = results.get( "cn=testEntry,ou=system" );

        Attribute collectiveAttributeSubentries = testEntry.get( "collectiveAttributeSubentries" );
        
        assertNull( collectiveAttributeSubentries );

        //----------------------------------------------------------------------

        sysRoot.modifyAttributes( "cn=testEntry", DirContext.ADD_ATTRIBUTE, getModsForIntroducingNewOC() );

        results = getAllEntries();
        testEntry = ( Attributes ) results.get( "cn=testEntry,ou=system" );

        collectiveAttributeSubentries = testEntry.get( "collectiveAttributeSubentries" );
        assertNotNull( collectiveAttributeSubentries );
    }

}