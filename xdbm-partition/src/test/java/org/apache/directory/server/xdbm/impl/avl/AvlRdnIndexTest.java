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
package org.apache.directory.server.xdbm.impl.avl;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.directory.server.constants.ApacheSchemaConstants;
import org.apache.directory.server.xdbm.Index;
import org.apache.directory.server.xdbm.IndexEntry;
import org.apache.directory.server.xdbm.ParentIdAndRdn;
import org.apache.directory.server.xdbm.impl.avl.AvlRdnIndex;
import org.apache.directory.shared.ldap.cursor.Cursor;
import org.apache.directory.shared.ldap.name.RDN;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.ldif.extractor.SchemaLdifExtractor;
import org.apache.directory.shared.ldap.schema.ldif.extractor.impl.DefaultSchemaLdifExtractor;
import org.apache.directory.shared.ldap.schema.loader.ldif.LdifSchemaLoader;
import org.apache.directory.shared.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.shared.ldap.util.LdapExceptionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * Tests the AvlRdnIndex.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class AvlRdnIndexTest
{
    private static File dbFileDir;
    Index<ParentIdAndRdn<Long>, Long, Long> idx;
    private static SchemaManager schemaManager;


    @BeforeClass
    public static void init() throws Exception
    {
        String workingDirectory = System.getProperty( "workingDirectory" );

        if ( workingDirectory == null )
        {
            String path = AvlRdnIndexTest.class.getResource( "" ).getPath();
            int targetPos = path.indexOf( "target" );
            workingDirectory = path.substring( 0, targetPos + 6 );
        }

        File schemaRepository = new File( workingDirectory, "schema" );
        SchemaLdifExtractor extractor = new DefaultSchemaLdifExtractor( new File( workingDirectory ) );
        extractor.extractOrCopy( true );
        LdifSchemaLoader loader = new LdifSchemaLoader( schemaRepository );
        schemaManager = new DefaultSchemaManager( loader );

        boolean loaded = schemaManager.loadAllEnabled();

        if ( !loaded )
        {
            fail( "Schema load failed : " + LdapExceptionUtils.printErrors( schemaManager.getErrors() ) );
        }
    }


    @Before
    public void setup() throws IOException
    {

        File tmpIndexFile = File.createTempFile( AvlRdnIndexTest.class.getSimpleName(), "db" );
        tmpIndexFile.deleteOnExit();
        dbFileDir = new File( tmpIndexFile.getParentFile(), AvlRdnIndexTest.class.getSimpleName() );

        dbFileDir.mkdirs();
    }


    @After
    public void teardown() throws Exception
    {
        destroyIndex();

        if ( ( dbFileDir != null ) && dbFileDir.exists() )
        {
            FileUtils.deleteDirectory( dbFileDir );
        }
    }


    void destroyIndex() throws Exception
    {
        if ( idx != null )
        {
            idx.sync();
            idx.close();
        }

        idx = null;
    }


    void initIndex() throws Exception
    {
        initIndex( new AvlRdnIndex() );
    }


    void initIndex( AvlRdnIndex avlIdx ) throws Exception
    {
        if ( avlIdx == null )
        {
            avlIdx = new AvlRdnIndex();
        }

        avlIdx.init( schemaManager, schemaManager
            .lookupAttributeTypeRegistry( ApacheSchemaConstants.APACHE_RDN_AT_OID ) );
        this.idx = avlIdx;
    }


    // -----------------------------------------------------------------------
    // Property Test Methods
    // -----------------------------------------------------------------------

    @Test(expected = UnsupportedOperationException.class)
    public void testCacheSize() throws Exception
    {
        // uninitialized index
        AvlRdnIndex AvlRdnIndex = new AvlRdnIndex();
        AvlRdnIndex.setCacheSize( 337 );
    }


    @Test(expected = UnsupportedOperationException.class)
    public void testWkDirPath() throws Exception
    {
        // uninitialized index
        AvlRdnIndex AvlRdnIndex = new AvlRdnIndex();
        AvlRdnIndex.setWkDirPath( new File( dbFileDir, "foo" ) );
    }


    @Test
    public void testGetAttribute() throws Exception
    {
        // uninitialized index
        AvlRdnIndex rdnIndex = new AvlRdnIndex();
        assertNull( rdnIndex.getAttribute() );

        initIndex();
        assertEquals( schemaManager.lookupAttributeTypeRegistry( ApacheSchemaConstants.APACHE_RDN_AT ), idx
            .getAttribute() );
    }


    @Test
    public void testIsCountExact() throws Exception
    {
        assertFalse( new AvlRdnIndex().isCountExact() );
    }


    // -----------------------------------------------------------------------
    // Count Test Methods
    // -----------------------------------------------------------------------

    @Test
    public void testCount() throws Exception
    {
        initIndex();
        assertEquals( 0, idx.count() );

        ParentIdAndRdn<Long> key = new ParentIdAndRdn<Long>( 0L, new RDN( "cn=key" ) );

        idx.add( key, 0l );
        assertEquals( 1, idx.count() );

        // setting a different parentId should make this key a different key
        key = new ParentIdAndRdn<Long>( 1L, new RDN( "cn=key" ) );

        idx.add( key, 1l );
        assertEquals( 2, idx.count() );

        //count shouldn't get affected cause of inserting the same key
        idx.add( key, 2l );
        assertEquals( 2, idx.count() );

        key = new ParentIdAndRdn<Long>( 2L, new RDN( "cn=key" ) );
        idx.add( key, 3l );
        assertEquals( 3, idx.count() );
    }


    @Test
    public void testCountOneArg() throws Exception
    {
        initIndex();

        ParentIdAndRdn<Long> key = new ParentIdAndRdn<Long>( 0L, new RDN( "cn=key" ) );

        assertEquals( 0, idx.count( key ) );

        idx.add( key, 0l );
        assertEquals( 1, idx.count( key ) );
    }


    // -----------------------------------------------------------------------
    // Add, Drop and Lookup Test Methods
    // -----------------------------------------------------------------------

    @Test
    public void testLookups() throws Exception
    {
        initIndex();

        ParentIdAndRdn<Long> key = new ParentIdAndRdn<Long>( 0L, new RDN( "cn=key" ) );

        assertNull( idx.forwardLookup( key ) );

        idx.add( key, 0l );
        assertEquals( 0, ( long ) idx.forwardLookup( key ) );
        assertEquals( key, idx.reverseLookup( 0l ) );
    }


    @Test
    public void testAddDropById() throws Exception
    {
        initIndex();

        ParentIdAndRdn<Long> key = new ParentIdAndRdn<Long>( 0L, new RDN( "cn=key" ) );

        assertNull( idx.forwardLookup( key ) );

        // test add/drop without adding any duplicates
        idx.add( key, 0l );
        assertEquals( 0, ( long ) idx.forwardLookup( key ) );

        idx.drop( key, 0l );
        assertNull( idx.forwardLookup( key ) );
        assertNull( idx.reverseLookup( 0l ) );
    }


    // -----------------------------------------------------------------------
    // Miscellaneous Test Methods
    // -----------------------------------------------------------------------

    @Test
    public void testCursors() throws Exception
    {
        initIndex();

        ParentIdAndRdn<Long> key = new ParentIdAndRdn<Long>( 0L, new RDN( "cn=key" ) );

        assertEquals( 0, idx.count() );

        idx.add( key, 0l );
        assertEquals( 1, idx.count() );

        for ( long i = 1; i < 5; i++ )
        {
            key = new ParentIdAndRdn<Long>( i, new RDN( "cn=key" + i ) );

            idx.add( key, ( long ) i );
        }

        assertEquals( 5, idx.count() );

        // use forward index's cursor
        Cursor<IndexEntry<ParentIdAndRdn<Long>, Long, Long>> cursor = idx.forwardCursor();
        cursor.beforeFirst();

        cursor.next();
        IndexEntry<ParentIdAndRdn<Long>, Long, Long> e1 = cursor.get();
        assertEquals( 0, ( long ) e1.getId() );
        assertEquals( "cn=key", e1.getValue().getRdns()[0].getName() );
        assertEquals( 0, e1.getValue().getParentId().longValue() );

        cursor.next();
        IndexEntry<ParentIdAndRdn<Long>, Long, Long> e2 = cursor.get();
        assertEquals( 1, ( long ) e2.getId() );
        assertEquals( "cn=key1", e2.getValue().getRdns()[0].getName() );
        assertEquals( 1, e2.getValue().getParentId().longValue() );

        cursor.next();
        IndexEntry<ParentIdAndRdn<Long>, Long, Long> e3 = cursor.get();
        assertEquals( 2, ( long ) e3.getId() );
        assertEquals( "cn=key2", e3.getValue().getRdns()[0].getName() );
        assertEquals( 2, e3.getValue().getParentId().longValue() );
    }

    //    @Test
    //    public void testStoreRdnWithTwoATAVs() throws Exception
    //    {
    //        initIndex();
    //        
    //        DN dn = new DN( "dc=example,dc=com" );
    //        dn.normalize( schemaManager.getNormalizerMapping() );
    //        
    //        RDN rdn = new RDN( dn.getName() );
    //        rdn._setParentId( 1 );
    //        idx.add( rdn, 0l );
    //        
    //        RDN rdn2 = idx.reverseLookup( 0l );
    //        System.out.println( rdn2 );
    //        InternalRdnComparator rdnCom = new InternalRdnComparator( "" );
    //        assertEquals( 0, rdnCom.compare( rdn, rdn2 ) );
    //    }
}
