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


import javax.naming.NamingException;
import javax.naming.ReferralException;

import org.apache.directory.server.newldap.LdapSession;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.message.CompareRequest;
import org.apache.directory.shared.ldap.message.LdapResult;
import org.apache.directory.shared.ldap.message.ReferralImpl;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.util.ExceptionUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A single reply handler for {@link CompareRequest}s.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 664302 $
 */
public class NewCompareHandler extends LdapRequestHandler<CompareRequest>
{
    private static final Logger LOG = LoggerFactory.getLogger( NewCompareHandler.class );
    

    /** Speedup for logs */
    private static final boolean IS_DEBUG = LOG.isDebugEnabled();

    
    public void handle( LdapSession session, CompareRequest req ) throws Exception
    {
        LdapResult result = req.getResultResponse().getLdapResult();

        try
        {
            if ( session.getCoreSession().compare( req ) )
            {
                result.setResultCode( ResultCodeEnum.COMPARE_TRUE );
            }
            else
            {
                result.setResultCode( ResultCodeEnum.COMPARE_FALSE );
            }

            result.setMatchedDn( req.getName() );
            session.getIoSession().write( req.getResultResponse() );
        }
        catch ( ReferralException e )
        {
            ReferralImpl refs = new ReferralImpl();
            result.setReferral( refs );
            result.setResultCode( ResultCodeEnum.REFERRAL );
            result.setErrorMessage( "Encountered referral attempting to handle compare request." );

            result.setMatchedDn( (LdapDN)e.getResolvedName() );

            do
            {
                refs.addLdapUrl( ( String ) e.getReferralInfo() );
            }
            while ( e.skipReferral() );
            session.getIoSession().write( req.getResultResponse() );
        }
        catch ( Exception e )
        {
            String msg = "failed to compare entry " + req.getName() + ": " + e.getMessage();

            if ( IS_DEBUG )
            {
                msg += ":\n" + ExceptionUtils.getStackTrace( e );
            }

            ResultCodeEnum code;

            if ( e instanceof LdapException )
            {
                code = ( ( LdapException ) e ).getResultCode();
            }
            else
            {
                code = ResultCodeEnum.getBestEstimate( e, req.getType() );
            }

            result.setResultCode( code );
            result.setErrorMessage( msg );

            if ( e instanceof NamingException )
            {
                NamingException ne = ( NamingException ) e;

                if ( ( ne.getResolvedName() != null )
                    && ( ( code == ResultCodeEnum.NO_SUCH_OBJECT ) || ( code == ResultCodeEnum.ALIAS_PROBLEM )
                        || ( code == ResultCodeEnum.INVALID_DN_SYNTAX ) || ( code == ResultCodeEnum.ALIAS_DEREFERENCING_PROBLEM ) ) )
                {
                    result.setMatchedDn( (LdapDN)ne.getResolvedName() );
                }
            }

            session.getIoSession().write( req.getResultResponse() );
        }
    }
}