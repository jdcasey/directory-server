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
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.SchemaManager;


/**
 * An {@link ACITupleFilter} that discards all tuples having a precedence less
 * than the highest remaining precedence. (18.8.4.1, X.501)
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class HighestPrecedenceFilter implements ACITupleFilter
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
        if ( tuples.size() <= 1 )
        {
            return tuples;
        }

        int maxPrecedence = -1;

        // Find the maximum precedence for all tuples.
        for ( ACITuple tuple:tuples )
        {
            if ( ( tuple.getPrecedence() != null ) && (tuple.getPrecedence() > maxPrecedence ) )
            {
                maxPrecedence = tuple.getPrecedence();
            }
        }

        // Remove all tuples whose precedences are not the maximum one.
        for ( Iterator<ACITuple> i = tuples.iterator(); i.hasNext(); )
        {
            ACITuple tuple = i.next();
            
            if ( ( tuple.getPrecedence() != null ) && ( tuple.getPrecedence() != maxPrecedence ) )
            {
                i.remove();
            }
        }

        return tuples;
    }
}
