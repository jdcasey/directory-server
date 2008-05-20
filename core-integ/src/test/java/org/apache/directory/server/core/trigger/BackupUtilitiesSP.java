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
package org.apache.directory.server.core.trigger;

import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;

import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.entry.ServerEntryUtils;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BackupUtilitiesSP
{
    private static final Logger LOG = LoggerFactory.getLogger( BackupUtilitiesSP.class );


    public static void backupDeleted( LdapContext ctx, LdapDN deletedEntryName,
                                      LdapDN operationPrincipal, ServerEntry deletedEntry ) throws NamingException
    {
        LOG.info( "User \"" + operationPrincipal + "\" has deleted entry \"" + deletedEntryName + "\"" );
        LOG.info( "Entry content was: " + deletedEntry );
        LdapContext backupCtx = ( LdapContext ) ctx.lookup( "ou=backupContext,ou=system" );
        String deletedEntryRdn = deletedEntryName.get( deletedEntryName.size() - 1 );
        backupCtx.createSubcontext( deletedEntryRdn, ServerEntryUtils.toAttributesImpl( deletedEntry ) );
        LOG.info( "Backed up deleted entry to \"" +
                ( ( LdapContext ) backupCtx.lookup( deletedEntryRdn ) ).getNameInNamespace() + "\"" );
    }
}