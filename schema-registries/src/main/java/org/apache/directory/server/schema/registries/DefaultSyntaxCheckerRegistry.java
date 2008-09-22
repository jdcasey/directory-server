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
package org.apache.directory.server.schema.registries;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;

import org.apache.directory.shared.ldap.schema.syntax.SyntaxChecker;
import org.apache.directory.shared.ldap.schema.syntax.SyntaxCheckerDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The POJO implementation for the SyntaxCheckerRegistry service.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class DefaultSyntaxCheckerRegistry implements SyntaxCheckerRegistry
{
    /** static class logger */
    private static final Logger LOG = LoggerFactory.getLogger( DefaultSyntaxCheckerRegistry.class );
    /** a map by OID of SyntaxCheckers */
    private final Map<String, SyntaxChecker> byOid;
    /** maps an OID to a syntaxCheckerDescription */
    private final Map<String, SyntaxCheckerDescription> oidToDescription;


    // ------------------------------------------------------------------------
    // C O N S T R U C T O R S
    // ------------------------------------------------------------------------


    /**
     * Creates an instance of a DefaultSyntaxRegistry.
     */
    public DefaultSyntaxCheckerRegistry()
    {
        this.byOid = new HashMap<String, SyntaxChecker>();
        this.oidToDescription = new HashMap<String, SyntaxCheckerDescription>();
    }


    // ------------------------------------------------------------------------
    // Service Methods
    // ------------------------------------------------------------------------

    
    public void register( SyntaxCheckerDescription syntaxCheckerDescription, SyntaxChecker syntaxChecker ) throws NamingException
    {
        if ( byOid.containsKey( syntaxChecker.getSyntaxOid() ) )
        {
            throw new NamingException( "SyntaxChecker with OID " + syntaxChecker.getSyntaxOid()
                + " already registered!" );
        }

        byOid.put( syntaxChecker.getSyntaxOid(), syntaxChecker );
        oidToDescription.put( syntaxChecker.getSyntaxOid(), syntaxCheckerDescription );
        if ( LOG.isDebugEnabled() )
        {
            LOG.debug( "registered syntaxChecher for OID " + syntaxChecker.getSyntaxOid() );
        }
    }


    public SyntaxChecker lookup( String oid ) throws NamingException
    {
        if ( !byOid.containsKey( oid ) )
        {
            throw new NamingException( "SyntaxChecker for OID " + oid + " not found!" );
        }

        SyntaxChecker syntaxChecker = byOid.get( oid );
        if ( LOG.isDebugEnabled() )
        {
            LOG.debug( "looked up syntaxChecher with OID " + oid );
        }
        return syntaxChecker;
    }


    public boolean hasSyntaxChecker( String oid )
    {
        return byOid.containsKey( oid );
    }


    public String getSchemaName( String oid ) throws NamingException
    {
        if ( ! Character.isDigit( oid.charAt( 0 ) ) )
        {
            throw new NamingException( "Looks like the arg is not a numeric OID" );
        }

        if ( oidToDescription.containsKey( oid ) )
        {
            return getSchema( oidToDescription.get( oid ) );
        }

        throw new NamingException( "OID " + oid + " not found in oid to " + "schema name map!" );
    }
    
    
    private static String getSchema( SyntaxCheckerDescription desc ) 
    {
        List<String> ext = desc.getExtensions().get( "X-SCHEMA" );
        
        if ( ext == null || ext.size() == 0 )
        {
            return "other";
        }
        
        return ext.get( 0 );
    }


    public Iterator<SyntaxChecker> iterator()
    {
        return byOid.values().iterator();
    }


    public void unregister( String numericOid ) throws NamingException
    {
        if ( ! Character.isDigit( numericOid.charAt( 0 ) ) )
        {
            throw new NamingException( "Looks like the arg is not a numeric OID" );
        }

        byOid.remove( numericOid );
        oidToDescription.remove( numericOid );
    }
    
    
    public void unregisterSchemaElements( String schemaName )
    {
        List<String> oids = new ArrayList<String>( byOid.keySet() );
        for ( String oid : oids )
        {
            SyntaxCheckerDescription description = oidToDescription.get( oid );
            String schemaNameForOid = getSchema( description );
            if ( schemaNameForOid.equalsIgnoreCase( schemaName ) )
            {
                byOid.remove( oid );
                oidToDescription.remove( oid );
            }
        }
    }


    public void renameSchema( String originalSchemaName, String newSchemaName )
    {
        List<String> oids = new ArrayList<String>( byOid.keySet() );
        for ( String oid : oids )
        {
            SyntaxCheckerDescription description = oidToDescription.get( oid );
            String schemaNameForOid = getSchema( description );
            if ( schemaNameForOid.equalsIgnoreCase( originalSchemaName ) )
            {
                List<String> values = description.getExtensions().get( "X-SCHEMA" );
                values.clear();
                values.add( newSchemaName );
            }
        }
    }


    public Iterator<SyntaxCheckerDescription> syntaxCheckerDescriptionIterator()
    {
        return oidToDescription.values().iterator();
    }
}