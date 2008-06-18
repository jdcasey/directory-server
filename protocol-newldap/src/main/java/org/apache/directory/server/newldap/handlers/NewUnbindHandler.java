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
package org.apache.directory.server.newldap.handlers;


import org.apache.directory.server.newldap.LdapSession;
import org.apache.directory.shared.ldap.message.UnbindRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A no reply protocol handler implementation for LDAP {@link
 * org.apache.directory.shared.ldap.message.UnbindRequest}s.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class NewUnbindHandler extends LdapRequestHandler<UnbindRequest>
{
    private static final Logger LOG = LoggerFactory.getLogger( NewUnbindHandler.class );


    public void handle( LdapSession session, UnbindRequest request ) throws Exception
    {
        try
        {
            session.getCoreSession().unbind( request );
            session.getIoSession().close();
            ldapServer.removeLdapSession( session.getIoSession() );
        }
        catch ( Throwable t )
        {
            LOG.error( "failed to unbind session properly", t );
        }
    }
}