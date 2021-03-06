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

package org.apache.directory.server.core.partition.impl.btree.jdbm;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import jdbm.helper.MRU;
import jdbm.recman.BaseRecordManager;
import jdbm.recman.CacheRecordManager;

import org.apache.directory.server.core.partition.impl.btree.LongComparator;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.xdbm.ParentIdAndRdn;
import org.apache.directory.server.xdbm.ParentIdAndRdnComparator;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.MatchingRule;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.util.SynchronizedLRUMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A special index which stores RDN objects.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class JdbmRdnIndex<E> extends JdbmIndex<ParentIdAndRdn<Long>, E>
{

    /** A logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger( JdbmRdnIndex.class );


    public JdbmRdnIndex()
    {
        initialized = false;
    }


    public JdbmRdnIndex( String attributeId )
    {
        initialized = false;
        setAttributeId( attributeId );
    }


    public void init( SchemaManager schemaManager, AttributeType attributeType, File wkDirPath ) throws IOException
    {
        LOG.debug( "Initializing an Index for attribute '{}'", attributeType.getName() );
        keyCache = new SynchronizedLRUMap( cacheSize );
        attribute = attributeType;

        if ( attributeId == null )
        {
            setAttributeId( attribute.getName() );
        }

        if ( this.wkDirPath == null )
        {
            this.wkDirPath = wkDirPath;
        }

        File file = new File( this.wkDirPath.getPath() + File.separator + attribute.getOid() );
        String path = file.getAbsolutePath();
        BaseRecordManager base = new BaseRecordManager( path );
        base.disableTransactions();
        this.recMan = new CacheRecordManager( base, new MRU( cacheSize ) );

        try
        {
            initTables( schemaManager );
        }
        catch ( IOException e )
        {
            // clean up
            close();
            throw e;
        }

        // finally write a text file in the format <OID>-<attribute-name>.txt
        FileWriter fw = new FileWriter( new File( this.wkDirPath.getPath() + File.separator + attribute.getOid() + "-" + attribute.getName() + ".txt" ) );
        // write the AttributeType description
        fw.write( attribute.toString() );
        fw.close();
        
        initialized = true;
    }


    /**
     * Initializes the forward and reverse tables used by this Index.
     * 
     * @throws IOException if we cannot initialize the forward and reverse
     * tables
     * @throws NamingException 
     */
    private void initTables( SchemaManager schemaManager ) throws IOException
    {
        MatchingRule mr = attribute.getEquality();

        if ( mr == null )
        {
            throw new IOException( I18n.err( I18n.ERR_574, attribute.getName() ) );
        }

        ParentIdAndRdnComparator<Long> comp = new ParentIdAndRdnComparator<Long>( mr.getOid() );

        LongComparator.INSTANCE.setSchemaManager( schemaManager );

        forward = new JdbmTable<ParentIdAndRdn<Long>, Long>( schemaManager, attribute.getOid() + FORWARD_BTREE,
            recMan, comp, null, LongSerializer.INSTANCE );
        reverse = new JdbmTable<Long, ParentIdAndRdn<Long>>( schemaManager, attribute.getOid() + REVERSE_BTREE,
            recMan, LongComparator.INSTANCE, LongSerializer.INSTANCE, null );
    }


    public void add( ParentIdAndRdn<Long> rdn, Long entryId ) throws Exception
    {
        forward.put( rdn, entryId );
        reverse.put( entryId, rdn );
    }


    public void drop( Long entryId ) throws Exception
    {
        ParentIdAndRdn<Long> rdn = reverse.get( entryId );
        forward.remove( rdn );
        reverse.remove( entryId );
    }


    public void drop( ParentIdAndRdn<Long> rdn, Long id ) throws Exception
    {
        long val = forward.get( rdn );
        if ( val == id )
        {
            forward.remove( rdn );
            reverse.remove( val );
        }
    }


    public ParentIdAndRdn<Long> getNormalized( ParentIdAndRdn<Long> rdn ) throws Exception
    {
        return rdn;
    }
}
