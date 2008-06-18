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
package org.apache.directory.server.newldap.handlers.extended;


import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.naming.ldap.LdapContext;
import javax.swing.JFrame;

import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.authn.LdapPrincipal;
import org.apache.directory.server.core.interceptor.context.ListSuffixOperationContext;
import org.apache.directory.server.core.jndi.ServerLdapContext;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.core.partition.impl.btree.BTreePartition;
import org.apache.directory.server.core.partition.impl.btree.gui.PartitionFrame;
import org.apache.directory.server.newldap.ExtendedOperationHandler;
import org.apache.directory.server.newldap.LdapServer;
import org.apache.directory.server.newldap.gui.SessionsFrame;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.message.ExtendedRequest;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.message.extended.LaunchDiagnosticUiRequest;
import org.apache.directory.shared.ldap.message.extended.LaunchDiagnosticUiResponse;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.mina.common.IoSession;


/**
 * @org.apache.xbean.XBean
 * 
 */
public class LaunchDiagnosticUiHandler implements ExtendedOperationHandler
{
    public static final Set<String> EXTENSION_OIDS;

    static
    {
        Set<String> set = new HashSet<String>( 3 );
        set.add( LaunchDiagnosticUiRequest.EXTENSION_OID );
        set.add( LaunchDiagnosticUiResponse.EXTENSION_OID );
        EXTENSION_OIDS = Collections.unmodifiableSet( set );
    }

    private LdapServer ldapProvider;


    public String getOid()
    {
        return LaunchDiagnosticUiRequest.EXTENSION_OID;
    }


    public void handleExtendedOperation( IoSession requestor, SessionRegistry registry, ExtendedRequest req )
        throws Exception
    {
        LdapContext ctx = registry.getLdapContext( requestor, null, false );
        ctx = ( LdapContext ) ctx.lookup( "" );

        if ( ctx instanceof ServerLdapContext )
        {
            ServerLdapContext slc = ( ServerLdapContext ) ctx;
            DirectoryService service = slc.getService();

            if ( !slc.getSession().getEffectivePrincipal().getName()
                    .equalsIgnoreCase( ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED ) )
            {
                requestor.write( new LaunchDiagnosticUiResponse( req.getMessageId(),
                    ResultCodeEnum.INSUFFICIENT_ACCESS_RIGHTS ) );
                return;
            }

            requestor.write( new LaunchDiagnosticUiResponse( req.getMessageId() ) );

            PartitionNexus nexus = service.getPartitionNexus();
            LdapDN adminDn = new LdapDN( ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
            adminDn.normalize( service.getRegistries().getAttributeTypeRegistry().getNormalizerMapping() );
            LdapPrincipal principal = new LdapPrincipal( adminDn, AuthenticationLevel.STRONG );
            CoreSession session = service.getSession( principal );
            Iterator<String> list = nexus.listSuffixes( new ListSuffixOperationContext( session ) );
            int launchedWindowCount = 0;
            
            while ( list.hasNext() )
            {
                LdapDN dn = new LdapDN( list.next() );
                Partition partition = nexus.getPartition( dn );
                
                if ( partition instanceof BTreePartition )
                {
                    BTreePartition btPartition = ( BTreePartition ) partition;
                    PartitionFrame frame = new PartitionFrame( btPartition, service.getRegistries() );
                    Point pos = getCenteredPosition( frame );
                    pos.y = launchedWindowCount * 20 + pos.y;
                    double multiplier = getAspectRatio() * 20.0;
                    pos.x = ( int ) ( launchedWindowCount * multiplier ) + pos.x;
                    frame.setLocation( pos );
                    frame.setVisible( true );
                    launchedWindowCount++;
                }
            }

            SessionsFrame sessions = new SessionsFrame( ldapProvider.getRegistry() );
            sessions.setRequestor( requestor );
            sessions.setLdapProvider( ldapProvider.getHandler() );
            Point pos = getCenteredPosition( sessions );
            pos.y = launchedWindowCount * 20 + pos.y;
            double multiplier = getAspectRatio() * 20.0;
            pos.x = ( int ) ( launchedWindowCount * multiplier ) + pos.x;
            sessions.setLocation( pos );
            sessions.setVisible( true );
            return;
        }

        requestor.write( new LaunchDiagnosticUiResponse( req.getMessageId(), ResultCodeEnum.OPERATIONS_ERROR ) );
    }


    public double getAspectRatio()
    {
        Toolkit tk = Toolkit.getDefaultToolkit();
        Dimension screenSize = tk.getScreenSize();
        return screenSize.getWidth() / screenSize.getHeight();
    }


    public Point getCenteredPosition( JFrame frame )
    {
        Point pt = new Point();
        Toolkit tk = Toolkit.getDefaultToolkit();
        Dimension screenSize = tk.getScreenSize();
        pt.x = ( screenSize.width - frame.getWidth() ) / 2;
        pt.y = ( screenSize.height - frame.getHeight() ) / 2;
        return pt;
    }


    public Set<String> getExtensionOids()
    {
        return EXTENSION_OIDS;
    }


    public void setLdapProvider( LdapServer provider )
    {
        this.ldapProvider = provider;
    }
}