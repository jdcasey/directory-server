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
package org.apache.directory.server.core.interceptor.context;


import org.apache.directory.server.core.CoreSession;
import org.apache.directory.shared.ldap.codec.MessageTypeEnum;
import org.apache.directory.shared.ldap.name.DN;


/**
 * A Entry context used for Interceptors. It contains all the informations
 * needed for the hasEntry operation, and used by all the interceptors
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class EntryOperationContext extends AbstractOperationContext
{
    /**
     * Creates a new instance of EntryOperationContext.
     */
    public EntryOperationContext( CoreSession session )
    {
        super( session );
    }
    
    /**
     * Creates a new instance of EntryOperationContext.
     *
     * @param entryDn The Entry DN to unbind
     */
    public EntryOperationContext( CoreSession session, DN entryDn )
    {
        super( session, entryDn );
    }
    

    /**
     * @return the operation name
     */
    public String getName()
    {
        return MessageTypeEnum.ADD_REQUEST.name();
    }

    
    /**
     * @see Object#toString()
     */
    public String toString()
    {
        return "EntryContext for DN '" + getDn().getName() + "'";
    }
}
