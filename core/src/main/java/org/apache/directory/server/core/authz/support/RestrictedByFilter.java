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
package org.apache.directory.server.core.authz.support;


import java.util.Collection;
import java.util.Iterator;

import org.apache.directory.server.core.interceptor.context.OperationContext;
import org.apache.directory.shared.ldap.aci.ACITuple;
import org.apache.directory.shared.ldap.aci.MicroOperation;
import org.apache.directory.shared.ldap.aci.ProtectedItem;
import org.apache.directory.shared.ldap.aci.protectedItem.RestrictedByElem;
import org.apache.directory.shared.ldap.aci.protectedItem.RestrictedByItem;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.SchemaManager;


/**
 * An {@link ACITupleFilter} that discards all tuples that doesn't satisfy
 * {@link org.apache.directory.shared.ldap.aci.ProtectedItem.RestrictedByItem} constraint if available. (18.8.3.3, X.501)
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class RestrictedByFilter implements ACITupleFilter
{
    public Collection<ACITuple> filter( 
            SchemaManager schemaManager, 
            Collection<ACITuple> tuples, 
            OperationScope scope, 
            OperationContext opContext,
            Collection<DN> userGroupNames, 
            DN userName, 
            Entry userEntry, 
            AuthenticationLevel authenticationLevel,
            DN entryName, 
            AttributeType attributeType, 
            Value<?> attrValue, 
            Entry entry, 
            Collection<MicroOperation> microOperations,
            Entry entryView )
        throws LdapException
    {
        if ( scope != OperationScope.ATTRIBUTE_TYPE_AND_VALUE )
        {
            return tuples;
        }

        if ( tuples.size() == 0 )
        {
            return tuples;
        }

        for ( Iterator<ACITuple> ii = tuples.iterator() ; ii.hasNext(); )
        {
            ACITuple tuple = ii.next();
            
            if ( !tuple.isGrant() )
            {
                continue;
            }

            if ( isRemovable( tuple, attributeType, attrValue, entry ) )
            {
                ii.remove();
            }
        }

        return tuples;
    }


    public boolean isRemovable( ACITuple tuple, AttributeType attributeType, Value<?> attrValue, Entry entry ) throws LdapException
    {
        for ( ProtectedItem item : tuple.getProtectedItems() )
        {
            if ( item instanceof RestrictedByItem )
            {
                RestrictedByItem rb = ( RestrictedByItem ) item;
            
                for ( Iterator<RestrictedByElem> k = rb.iterator(); k.hasNext(); )
                {
                    RestrictedByElem rbItem = k.next();
                
                    // TODO Fix DIRSEVER-832 
                    if ( attributeType.equals( rbItem.getAttributeType() ) )
                    {
                        EntryAttribute attr = entry.get( rbItem.getValuesIn() );
                        
                        // TODO Fix DIRSEVER-832
                        if ( ( attr == null ) || !attr.contains( attrValue ) )
                        {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
}
