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


import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.commons.lang.NotImplementedException;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.kerberos.shared.crypto.encryption.EncryptionType;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptionKey;
import org.apache.directory.server.kerberos.shared.store.PrincipalStoreEntry;
import org.apache.directory.server.kerberos.shared.store.operations.GetPrincipal;
import org.apache.directory.server.newldap.LdapProtocolUtils;
import org.apache.directory.server.newldap.LdapServer;
import org.apache.directory.server.newldap.LdapSession;
import org.apache.directory.server.newldap.handlers.bind.MechanismHandler;
import org.apache.directory.server.protocol.shared.ServiceConfigurationException;
import org.apache.directory.shared.ldap.constants.SupportedSaslMechanisms;
import org.apache.directory.shared.ldap.message.BindRequest;
import org.apache.directory.shared.ldap.message.BindResponse;
import org.apache.directory.shared.ldap.message.LdapResult;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A single reply handler for {@link BindRequest}s.
 *
 * Implements server-side of RFC 2222, sections 4.2 and 4.3.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 664302 $, $Date: 2008-06-07 04:44:00 -0400 (Sat, 07 Jun 2008) $
 */
public class NewBindHandler extends LdapRequestHandler<BindRequest>
{
    private static final Logger LOG = LoggerFactory.getLogger( NewBindHandler.class );

    /** A Hashed Adapter mapping SASL mechanisms to their handlers. */
    private Map<String, MechanismHandler> handlers;

        
    /**
     * Set the mechanisms handler map.
     * 
     * @param handlers The associations btween a machanism and its handler
     */
    public void setSaslMechanismHandlers( Map<String, MechanismHandler> handlers )
    {
        this.handlers = handlers;
    }
    

    /**
     * Handle the SASL authentication.
     *
     * @param session The associated Session
     * @param message The BindRequest received
     * @throws Exception If the authentication cannot be done
     */
    public void handleSaslAuth( LdapSession session, BindRequest message ) throws Exception
    {
        Map<String, String> saslProps = new HashMap<String, String>();
        saslProps.put( Sasl.QOP, ldapServer.getSaslQopString() );
        saslProps.put( "com.sun.security.sasl.digest.realm", getActiveRealms( ldapServer ) );
        session.getIoSession().setAttribute( "saslProps", saslProps );

        session.getIoSession().setAttribute( "saslHost", ldapServer.getSaslHost() );
        session.getIoSession().setAttribute( "baseDn", ldapServer.getSearchBaseDn() );

        Set<String> activeMechanisms = ldapServer.getSupportedMechanisms();

        if ( activeMechanisms.contains( SupportedSaslMechanisms.GSSAPI ) )
        {
            try
            {
                Subject saslSubject = getSubject( ldapServer );
                session.getIoSession().setAttribute( "saslSubject", saslSubject );
            }
            catch ( ServiceConfigurationException sce )
            {
                activeMechanisms.remove( "GSSAPI" );
                LOG.warn( sce.getMessage() );
            }
        }

        BindRequest bindRequest = ( BindRequest ) message;

        // Guard clause:  Reject unsupported SASL mechanisms.
        if ( ! ldapServer.getSupportedMechanisms().contains( bindRequest.getSaslMechanism() ) )
        {
            LOG.error( "Bind error : {} mechanism not supported. Please check the server.xml " + 
                "configuration file (supportedMechanisms field)", 
                bindRequest.getSaslMechanism() );

            LdapResult bindResult = bindRequest.getResultResponse().getLdapResult();
            bindResult.setResultCode( ResultCodeEnum.AUTH_METHOD_NOT_SUPPORTED );
            bindResult.setErrorMessage( bindRequest.getSaslMechanism() + " is not a supported mechanism." );
            session.getIoSession().write( bindRequest.getResultResponse() );
            return;
        }

        handleSasl( session, bindRequest );
    }

    
    /**
     * Deal with a SASL bind request
     * 
     * @param session The IoSession for this Bind Request
     * @param bindRequest The BindRequest received
     * 
     * @exception Exception if the mechanism cannot handle the authentication
     */
    public void handleSasl( LdapSession session, BindRequest bindRequest ) throws Exception
    {
        DirectoryService ds = getLdapServer().getDirectoryService();
        String sessionMechanism = bindRequest.getSaslMechanism();

        if ( sessionMechanism.equals( SupportedSaslMechanisms.PLAIN ) )
        {
            // TODO - figure out what to provide for the saslAuthId here
            session.setCoreSession( ds.getSession( bindRequest.getName(), bindRequest.getCredentials(), 
                sessionMechanism, null ) );
        }
        else
        {
            MechanismHandler mechanismHandler = handlers.get( sessionMechanism );

            if ( mechanismHandler == null )
            {
                LOG.error( "Handler unavailable for " + sessionMechanism );
                throw new IllegalArgumentException( "Handler unavailable for " + sessionMechanism );
            }

            SaslServer ss = mechanismHandler.handleMechanism( session, bindRequest );
            LdapResult result = bindRequest.getResultResponse().getLdapResult();

            if ( ! ss.isComplete() )
            {
                try
                {
                    /*
                     * SaslServer will throw an exception if the credentials are null.
                     */
                    if ( bindRequest.getCredentials() == null )
                    {
                        bindRequest.setCredentials( new byte[0] );
                    }

                    byte[] tokenBytes = ss.evaluateResponse( bindRequest.getCredentials() );

                    if ( ss.isComplete() )
                    {
                        if ( tokenBytes != null )
                        {
                            /*
                             * There may be a token to return to the client.  We set it here
                             * so it will be returned in a SUCCESS message, after an LdapContext
                             * has been initialized for the client.
                             */
                            session.getIoSession().setAttribute( "saslCreds", tokenBytes );
                        }

                        /*
                         * If we got here, we're ready to try getting a core session.
                         */
                        // TODO - figure out what to provide for the saslAuthId here
                        session.setCoreSession( ds.getSession( bindRequest.getName(), bindRequest.getCredentials(), 
                            sessionMechanism, null ) );
                    }
                    else
                    {
                        LOG.info( "Continuation token had length " + tokenBytes.length );
                        result.setResultCode( ResultCodeEnum.SASL_BIND_IN_PROGRESS );
                        BindResponse resp = ( BindResponse ) bindRequest.getResultResponse();
                        resp.setServerSaslCreds( tokenBytes );
                        session.getIoSession().write( resp );
                        LOG.debug( "Returning final authentication data to client to complete context." );
                    }
                }
                catch ( SaslException se )
                {
                    LOG.error( se.getMessage() );
                    result.setResultCode( ResultCodeEnum.INVALID_CREDENTIALS );
                    result.setErrorMessage( se.getMessage() );
                    session.getIoSession().write( bindRequest.getResultResponse() );
                }
            }
        }
    }

    
    /**
     * Create a list of all the configured realms.
     * 
     * @param ldapServer the LdapServer for which we want to get the realms
     * @return a list of relms, separated by spaces
     */
    private String getActiveRealms( LdapServer ldapServer )
    {
        StringBuilder realms = new StringBuilder();
        boolean isFirst = true;

        for ( String realm:ldapServer.getSaslRealms() )
        {
            if ( isFirst )
            {
                isFirst = false;
            }
            else
            {
                realms.append( ' ' );
            }
            
            realms.append( realm );
        }

        return realms.toString();
    }


    private Subject getSubject( LdapServer ldapServer ) throws Exception
    {
        String servicePrincipalName = ldapServer.getSaslPrincipal();

        KerberosPrincipal servicePrincipal = new KerberosPrincipal( servicePrincipalName );
        GetPrincipal getPrincipal = new GetPrincipal( servicePrincipal );

        PrincipalStoreEntry entry = null;

        try
        {
            entry = findPrincipal( ldapServer, getPrincipal );
        }
        catch ( ServiceConfigurationException sce )
        {
            String message = "Service principal " + servicePrincipalName + " not found at search base DN "
                + ldapServer.getSearchBaseDn() + ".";
            throw new ServiceConfigurationException( message, sce );
        }

        if ( entry == null )
        {
            String message = "Service principal " + servicePrincipalName + " not found at search base DN "
                + ldapServer.getSearchBaseDn() + ".";
            throw new ServiceConfigurationException( message );
        }

        Subject subject = new Subject();

        for ( EncryptionType encryptionType:entry.getKeyMap().keySet() )
        {
            EncryptionKey key = entry.getKeyMap().get( encryptionType );

            byte[] keyBytes = key.getKeyValue();
            int type = key.getKeyType().getOrdinal();
            int kvno = key.getKeyVersion();

            KerberosKey serviceKey = new KerberosKey( servicePrincipal, keyBytes, type, kvno );

            subject.getPrivateCredentials().add( serviceKey );
        }

        return subject;
    }
    

    private PrincipalStoreEntry findPrincipal( LdapServer ldapServer, GetPrincipal getPrincipal ) throws Exception
    {
//        if ( ctx == null )
//        {
//            try
//            {
//                LdapDN adminDN = new LdapDN( ServerDNConstants.ADMIN_SYSTEM_DN );
//                
//                adminDN.normalize( 
//                    ldapServer.getDirectoryService().getRegistries().getAttributeTypeRegistry().getNormalizerMapping() );
//                LdapPrincipal principal = new LdapPrincipal( adminDN, AuthenticationLevel.SIMPLE );
//
//                CoreSession adminSession = getLdapServer().getDirectoryService().getAdminSession();
//                
//                ctx = new ServerLdapContext( ldapServer.getDirectoryService(), principal, 
//                    new LdapDN( ldapServer.getSearchBaseDn() ) );
//            }
//            catch ( NamingException ne )
//            {
//                String message = "Failed to get initial context " + ldapServer.getSearchBaseDn();
//                throw new ServiceConfigurationException( message, ne );
//            }
//        }
//
//        return ( PrincipalStoreEntry ) getPrincipal.execute( ctx, null );
        throw new NotImplementedException();
    }    
    

    /**
     * Deal with a received BindRequest
     * 
     * @param session The current session
     * @param bindRequest The received BindRequest
     * @throws Exception If the authentication cannot be handled
     */
    @Override
    public void handle( LdapSession session, BindRequest bindRequest ) throws Exception
    {
        LOG.debug( "Received: {}", bindRequest );

        // Guard clause:  LDAP version 3
        if ( ! bindRequest.getVersion3() )
        {
            LOG.error( "Bind error : Only LDAP v3 is supported." );
            LdapResult bindResult = bindRequest.getResultResponse().getLdapResult();
            bindResult.setResultCode( ResultCodeEnum.PROTOCOL_ERROR );
            bindResult.setErrorMessage( "Only LDAP v3 is supported." );
            session.getIoSession().write( bindRequest.getResultResponse() );
            return;
        }


        // Deal with the two kinds of authentication :
        // - if it's simple, handle it in this class for speed
        // - for SASL, we go through a chain right now (but it may change in the near future)
        if ( bindRequest.isSimple() )
        {
            BindOperationContext opContext = new BindOperationContext( null );
            opContext.setCredentials( bindRequest.getCredentials() );
            opContext.setDn( bindRequest.getName() );
            LdapProtocolUtils.setRequestControls( opContext, bindRequest );
            getLdapServer().getDirectoryService().getOperationManager().bind( opContext );
            session.setCoreSession( opContext.getSession() );
            
            BindResponse response = ( BindResponse ) bindRequest.getResultResponse();
            response.getLdapResult().setResultCode( ResultCodeEnum.SUCCESS );
            LdapProtocolUtils.setResponseControls( opContext, response );
            
            session.getIoSession().write( response );
            LOG.debug( "Returned SUCCESS message." );
        }
        else
        {
            handleSaslAuth( session, bindRequest );
        }
    }
}