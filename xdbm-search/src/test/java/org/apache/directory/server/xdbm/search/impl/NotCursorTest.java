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
package org.apache.directory.server.xdbm.search.impl;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmStore;
import org.apache.directory.server.xdbm.ForwardIndexEntry;
import org.apache.directory.server.xdbm.IndexCursor;
import org.apache.directory.server.xdbm.Store;
import org.apache.directory.server.xdbm.search.Evaluator;
import org.apache.directory.server.xdbm.tools.StoreUtils;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.cursor.InvalidCursorPositionException;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.FilterParser;
import org.apache.directory.shared.ldap.filter.NotNode;
import org.apache.directory.shared.ldap.filter.SubstringNode;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.comparators.SerializableComparator;
import org.apache.directory.shared.ldap.schema.ldif.extractor.SchemaLdifExtractor;
import org.apache.directory.shared.ldap.schema.registries.AttributeTypeRegistry;
import org.apache.directory.shared.ldap.schema.registries.Registries;
import org.apache.directory.shared.ldap.util.ExceptionUtils;
import org.apache.directory.shared.schema.DefaultSchemaManager;
import org.apache.directory.shared.schema.loader.ldif.LdifSchemaLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 
 * Test cases for NotCursor.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class NotCursorTest
{
    private static final Logger LOG = LoggerFactory.getLogger( NotCursorTest.class.getSimpleName() );

    File wkdir;
    Store<ServerEntry> store;
    static Registries registries = null;
    static AttributeTypeRegistry attributeRegistry;
    EvaluatorBuilder evaluatorBuilder;
    CursorBuilder cursorBuilder;

    @BeforeClass
    static public void setup() throws Exception
    {
        // setup the standard registries
    	String workingDirectory = System.getProperty( "workingDirectory" );

        if ( workingDirectory == null )
        {
            String path = NotCursorTest.class.getResource( "" ).getPath();
            int targetPos = path.indexOf( "target" );
            workingDirectory = path.substring( 0, targetPos + 6 );
        }

        File schemaRepository = new File( workingDirectory, "schema" );
        SchemaLdifExtractor extractor = new SchemaLdifExtractor( new File( workingDirectory ) );
        extractor.extractOrCopy();
        LdifSchemaLoader loader = new LdifSchemaLoader( schemaRepository );
        SchemaManager sm = new DefaultSchemaManager( loader );

        boolean loaded = sm.loadAllEnabled();

        if ( !loaded )
        {
            fail( "Schema load failed : " + ExceptionUtils.printErrors( sm.getErrors() ) );
        }
        
        loaded = sm.loadWithDeps( loader.getSchema( "collective" ) );
        
        if ( !loaded )
        {
            fail( "Schema load failed : " + ExceptionUtils.printErrors( sm.getErrors() ) );
        }
        
        registries = sm.getRegistries();

        SerializableComparator.setRegistry( registries.getComparatorRegistry() );

        attributeRegistry = registries.getAttributeTypeRegistry();
    }


    @Before
    public void createStore() throws Exception
    {
        destryStore();

        // setup the working directory for the store
        wkdir = File.createTempFile( getClass().getSimpleName(), "db" );
        wkdir.delete();
        wkdir = new File( wkdir.getParentFile(), getClass().getSimpleName() );
        wkdir.mkdirs();

        // initialize the store
        store = new JdbmStore<ServerEntry>();
        store.setName( "example" );
        store.setCacheSize( 10 );
        store.setWorkingDirectory( wkdir );
        store.setSyncOnWrite( false );

        store.addIndex( new JdbmIndex( SchemaConstants.OU_AT_OID ) );
        store.addIndex( new JdbmIndex( SchemaConstants.CN_AT_OID ) );
        StoreUtils.loadExampleData( store, registries );
        
        evaluatorBuilder = new EvaluatorBuilder( store, registries );
        cursorBuilder = new CursorBuilder( store, evaluatorBuilder );
        
        LOG.debug( "Created new store" );
    }

    
    @After
    public void destryStore() throws Exception
    {
        if ( store != null )
        {
            store.destroy();
        }

        store = null;
        if ( wkdir != null )
        {
            FileUtils.deleteDirectory( wkdir );
        }

        wkdir = null;
    }

    
    @Test
    public void testNotCursor() throws Exception
    {
        String filter = "(!(cn=J*))";

        ExprNode exprNode = FilterParser.parse( filter );
        
        IndexCursor<?, ServerEntry> cursor = cursorBuilder.build( exprNode );
        
        assertFalse( cursor.available() );
        
        cursor.beforeFirst();
        
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 1, ( long ) cursor.get().getId() );
        assertEquals( "2.5.4.10=good times co.", cursor.get().getValue() );
        
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 7, ( long ) cursor.get().getId() );
        assertEquals( "2.5.4.11=apache,2.5.4.11=board of directors,2.5.4.10=good times co.", cursor.get().getValue() );
        
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 3, ( long ) cursor.get().getId() );
        assertEquals( "2.5.4.11=board of directors,2.5.4.10=good times co.", cursor.get().getValue() );
        
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 4, ( long ) cursor.get().getId() );
        assertEquals( "2.5.4.11=engineering,2.5.4.10=good times co.", cursor.get().getValue() );
        
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 2, ( long ) cursor.get().getId() );
        assertEquals( "2.5.4.11=sales,2.5.4.10=good times co.", cursor.get().getValue() );
        
        assertFalse( cursor.next() );
        assertFalse( cursor.available() );
        
        cursor.close();
        assertTrue( cursor.isClosed() );
    }
    
    
    @Test
    public void testNotCursorWithManualFilter() throws Exception
    {
        NotNode notNode = new NotNode();
        
        ExprNode exprNode = new SubstringNode( "cn", "J", null );
        Evaluator<? extends ExprNode, ServerEntry> eval = new SubstringEvaluator( ( SubstringNode ) exprNode, store, registries );
        notNode.addNode( exprNode );
        
        NotCursor cursor = new NotCursor( store, eval ); //cursorBuilder.build( andNode );
        
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 1, ( long ) cursor.get().getId() );
        assertEquals( "2.5.4.10=good times co.", cursor.get().getValue() );
        
        cursor.first();
        
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 7, ( long ) cursor.get().getId() );
        assertEquals( "2.5.4.11=apache,2.5.4.11=board of directors,2.5.4.10=good times co.", cursor.get().getValue() );
        
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 3, ( long ) cursor.get().getId() );
        assertEquals( "2.5.4.11=board of directors,2.5.4.10=good times co.", cursor.get().getValue() );
        
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 4, ( long ) cursor.get().getId() );
        assertEquals( "2.5.4.11=engineering,2.5.4.10=good times co.", cursor.get().getValue() );
        
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 2, ( long ) cursor.get().getId() );
        assertEquals( "2.5.4.11=sales,2.5.4.10=good times co.", cursor.get().getValue() );
        
        assertFalse( cursor.next() );
        assertFalse( cursor.available() );

        cursor.afterLast();
        
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 2, ( long ) cursor.get().getId() );
        assertEquals( "2.5.4.11=sales,2.5.4.10=good times co.", cursor.get().getValue() );
        
        cursor.last();
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 4, ( long ) cursor.get().getId() );
        assertEquals( "2.5.4.11=engineering,2.5.4.10=good times co.", cursor.get().getValue() );
        
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 3, ( long ) cursor.get().getId() );
        assertEquals( "2.5.4.11=board of directors,2.5.4.10=good times co.", cursor.get().getValue() );
        
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 7, ( long ) cursor.get().getId() );
        assertEquals( "2.5.4.11=apache,2.5.4.11=board of directors,2.5.4.10=good times co.", cursor.get().getValue() );
        
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 1, ( long ) cursor.get().getId() );
        assertEquals( "2.5.4.10=good times co.", cursor.get().getValue() );
        
        assertFalse( cursor.previous() );
        assertFalse( cursor.available() );
        
        assertTrue( cursor.isElementReused() );
 
        try 
        {
            cursor.get();
            fail( "should fail with InvalidCursorPositionException" );
        }
        catch( InvalidCursorPositionException ice ) { }
        
        try
        {
            cursor.after( new ForwardIndexEntry<Object,ServerEntry>() );
            fail( "should fail with UnsupportedOperationException " );
        }
        catch( UnsupportedOperationException uoe ) {}
        
        try
        {
            cursor.before( new ForwardIndexEntry<Object,ServerEntry>() );
            fail( "should fail with UnsupportedOperationException " );
        }
        catch( UnsupportedOperationException uoe ) {}
    }

}
