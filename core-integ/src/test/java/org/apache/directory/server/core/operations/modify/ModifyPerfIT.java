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
package org.apache.directory.server.core.operations.modify;


import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.message.ModifyRequest;
import org.apache.directory.server.core.annotations.ContextEntry;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreateIndex;
import org.apache.directory.server.core.annotations.CreatePartition;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.server.core.integ.IntegrationUtils;
import org.apache.directory.shared.ldap.entry.DefaultEntry;
import org.apache.directory.shared.ldap.entry.DefaultEntryAttribute;
import org.apache.directory.shared.ldap.entry.DefaultModification;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.name.DN;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Test the modify operation performances
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
@RunWith(FrameworkRunner.class)
@CreateDS(name = "ModifyPerfDS", partitions =
    { @CreatePartition(name = "example", suffix = "dc=example,dc=com", contextEntry = @ContextEntry(entryLdif = "dn: dc=example,dc=com\n"
        + "dc: example\n" + "objectClass: top\n" + "objectClass: domain\n\n"), indexes =
        { @CreateIndex(attribute = "objectClass", cacheSize = 1000), @CreateIndex(attribute = "sn", cacheSize = 1000),
            @CreateIndex(attribute = "cn", cacheSize = 1000) })

    }, enableChangeLog = false)
public class ModifyPerfIT extends AbstractLdapTestUnit
{
    /**
     * Test an modify operation performance
     */
    @Test
    public void testModifyPerf() throws Exception
    {
        LdapConnection connection = IntegrationUtils.getAdminConnection( service );

        DN dn = new DN( "cn=test,ou=system" );
        Entry entry = new DefaultEntry( service.getSchemaManager(), dn );
        entry.add( "ObjectClass", "top", "person" );
        entry.add( "sn", "TEST" );
        entry.add( "cn", "test" );

        connection.add( entry );

        int nbIterations = 150000;

        long t0 = System.currentTimeMillis();
        long t00 = 0L;
        long tt0 = System.currentTimeMillis();

        for ( int i = 0; i < nbIterations; i++ )
        {
            if ( i % 100 == 0 )
            {
                long tt1 = System.currentTimeMillis();

                System.out.println( i + ", " + ( tt1 - tt0 ) );
                tt0 = tt1;
            }

            if ( i == 5000 )
            {
                t00 = System.currentTimeMillis();
            }

            ModifyRequest modRequest = new ModifyRequest( dn );
            Modification modification = new DefaultModification();
            EntryAttribute attribute = new DefaultEntryAttribute( "sn" );

            attribute.add( "test" + i );

            modification.setAttribute( attribute );
            modification.setOperation( ModificationOperation.REPLACE_ATTRIBUTE );
            modRequest.addModification( modification );

            long ttt0 = System.nanoTime();
            connection.modify( modRequest );
            long ttt1 = System.nanoTime();
            //System.out.println("added " + i + ", delta = " + (ttt1-ttt0)/1000);
        }

        long t1 = System.currentTimeMillis();

        Long deltaWarmed = ( t1 - t00 );
        System.out.println( "Delta : " + deltaWarmed + "( " + ( ( ( nbIterations - 5000 ) * 1000 ) / deltaWarmed ) + " per s ) /" + ( t1 - t0 ) );
        connection.close();
    }
}
