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
package org.apache.directory.server.kerberos.shared.store.operations;

import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.entry.ServerStringValue;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.kerberos.shared.crypto.encryption.EncryptionType;
import org.apache.directory.server.kerberos.shared.io.encoder.EncryptionKeyEncoder;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptionKey;
import org.apache.directory.server.kerberos.shared.store.KerberosAttribute;
import org.apache.directory.server.kerberos.shared.store.PrincipalStoreEntry;
import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Commonly used store utility operations.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class StoreUtils
{
    private static final Logger LOG = LoggerFactory.getLogger( StoreUtils.class );
    
    
    /**
     * Creates a ServerEntry for a PrincipalStoreEntry, doing what a state 
     * factory does but for ServerEntry instead of Attributes.
     *
     * @param session the session to use to access the directory's registries
     * @param dn the distinguished name of the principal to be 
     * @param principalEntry the principal entry to convert into a ServerEntry
     * @return the resultant server entry for the PrincipalStoreEntry argument
     * @throws Exception if there are problems accessing registries
     */
    public static ServerEntry toServerEntry( CoreSession session, LdapDN dn, PrincipalStoreEntry principalEntry ) 
        throws Exception
    {
        ServerEntry outAttrs = session.getDirectoryService().newEntry( dn );
        
        // process the objectClass attribute
        outAttrs.add( SchemaConstants.OBJECT_CLASS_AT, 
            SchemaConstants.TOP_OC, SchemaConstants.UID_OBJECT_AT, 
            "uidObject", SchemaConstants.EXTENSIBLE_OBJECT_OC, 
            SchemaConstants.PERSON_OC, SchemaConstants.ORGANIZATIONAL_PERSON_OC,
            SchemaConstants.INET_ORG_PERSON_OC, SchemaConstants.KRB5_PRINCIPAL_OC,
            "krb5KDCEntry" );

        outAttrs.add( SchemaConstants.UID_AT, principalEntry.getUserId() );
        outAttrs.add( KerberosAttribute.APACHE_SAM_TYPE_AT, "7" );
        outAttrs.add( SchemaConstants.SN_AT, principalEntry.getUserId() );
        outAttrs.add( SchemaConstants.CN_AT, principalEntry.getCommonName() );
        
        EncryptionKey encryptionKey = principalEntry.getKeyMap().get( EncryptionType.DES_CBC_MD5 );
        outAttrs.add( KerberosAttribute.KRB5_KEY_AT, EncryptionKeyEncoder.encode( encryptionKey ) );

        int keyVersion = encryptionKey.getKeyVersion();

        outAttrs.add( KerberosAttribute.KRB5_PRINCIPAL_NAME_AT, principalEntry.getPrincipal().getName() );
        outAttrs.add( KerberosAttribute.KRB5_KEY_VERSION_NUMBER_AT, Integer.toString( keyVersion ) );

        return outAttrs;
    }
    
    
    /**
     * Constructs a filter expression tree for the filter used to search the 
     * directory.
     * 
     * @param registry the registry to use for attribute lookups
     * @param principal the principal to use for building the filter
     * @return the filter expression tree
     * @throws Exception if there are problems while looking up attributes
     */
    private static ExprNode getFilter( AttributeTypeRegistry registry, String principal ) throws Exception
    {
        AttributeType type = registry.lookup( "krb5Principal" );
        Value<String> value = new ServerStringValue( type, principal );
        return new EqualityNode<String>( "krb5Principal", value );
    }
    

    /**
     * Finds the ServerEntry associated with the Kerberos principal name.
     *
     * @param session the session to use for the search
     * @param searchBaseDn the base to use while searching
     * @param principal the name of the principal to search for
     * @return the server entry for the principal or null if non-existent
     * @throws Exception if there are problems while searching the directory
     */
    public static ServerEntry findPrincipalEntry( CoreSession session, LdapDN searchBaseDn, String principal ) 
        throws Exception
    {
        EntryFilteringCursor cursor = null;
        
        try
        {
            AttributeTypeRegistry registry = session.getDirectoryService().getRegistries().getAttributeTypeRegistry();
            cursor = session.search( searchBaseDn, SearchScope.SUBTREE, 
                getFilter( registry, principal ), AliasDerefMode.DEREF_ALWAYS, null );
    
            cursor.beforeFirst();
            if ( cursor.next() )
            {
                ServerEntry entry = cursor.get();
                LOG.debug( "Found entry {} for kerberos principal name {}", entry, principal );
                
                while ( cursor.next() )
                {
                    LOG.error( "More than one server entry found for kerberos principal name {}: ", 
                        principal, cursor.next() );
                }
                
                return entry;
            }
            else
            {
                LOG.warn( "No server entry found for kerberos principal name {}", principal );
                return null;
            }
        }
        finally
        {
            if ( cursor != null )
            {
                cursor.close();
            }
        }
    }
}