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
package org.apache.directory.server.core.integ;


import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.shared.ldap.name.DN;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith( FrameworkRunner.class )
public class TestClassC extends AbstractLdapTestUnit
{
    @Test
    public void testWithoutMethodOrClassLevelFactory() throws Exception
    {
        if ( isRunInSuite )
        {
            assertTrue( service.getAdminSession().exists( new DN( "cn=testSuite,ou=system" ) ) );
        }

        assertFalse( service.getAdminSession().exists( new DN( "cn=testClassC,ou=system" ) ) );
        
        // the below DN will be injected in TestClassB when ran as suite, but that DN
        // shouldn't be present in the suite level DS cause of revert operation
        assertFalse( service.getAdminSession().exists( new DN( "cn=testClassB,ou=system" ) ) );
    }
    
    
    @Test
    @ApplyLdifFiles( "test-entry.ldif" )
    public void testWithApplyLdifFiles() throws Exception
    {
        assertTrue( service.getAdminSession().exists( new DN( "cn=testPerson1,ou=system" ) ) );
        assertTrue( service.getAdminSession().exists( new DN( "cn=testPerson2,ou=system" ) ) );
    }
}
