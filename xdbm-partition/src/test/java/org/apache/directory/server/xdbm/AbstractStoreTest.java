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
package org.apache.directory.server.xdbm;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.directory.server.xdbm.impl.avl.AvlIndex;
import org.apache.directory.server.xdbm.impl.avl.AvlStore;
import org.apache.directory.server.xdbm.impl.avl.AvlStoreTest;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.csn.CsnFactory;
import org.apache.directory.shared.ldap.entry.DefaultEntry;
import org.apache.directory.shared.ldap.entry.DefaultEntryAttribute;
import org.apache.directory.shared.ldap.entry.DefaultModification;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Tests the {@link AbstractStore} class.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class AbstractStoreTest
{
    private static final Logger LOG = LoggerFactory.getLogger( AbstractStoreTest.class );

    private static File wkdir;
    private static Store<Entry, Long> store;
    private static SchemaManager schemaManager = null;
    private static DN EXAMPLE_COM;


    @BeforeClass
    public static void setup() throws Exception
    {
        String workingDirectory = System.getProperty( "workingDirectory" );

        if ( workingDirectory == null )
        {
            String path = AvlStoreTest.class.getResource( "" ).getPath();
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

        EXAMPLE_COM = new DN( "dc=example,dc=com" );
        EXAMPLE_COM.normalize( schemaManager.getNormalizerMapping() );
    }


    @Before
    public void createStore() throws Exception
    {
        destroyStore();

        // setup the working directory for the store
        wkdir = File.createTempFile( getClass().getSimpleName(), "db" );
        wkdir.delete();
        wkdir = new File( wkdir.getParentFile(), getClass().getSimpleName() );
        wkdir.mkdirs();

        // initialize the store
        store = new AvlStore<Entry>();
        store.setId( "example" );
        store.setSyncOnWrite( false );

        store.addIndex( new AvlIndex<String, Entry>( SchemaConstants.OU_AT_OID ) );
        store.addIndex( new AvlIndex<String, Entry>( SchemaConstants.UID_AT_OID ) );
        store.addIndex( new AvlIndex<String, Entry>( SchemaConstants.CN_AT_OID ) );
        StoreUtils.loadExampleData( store, schemaManager );
        LOG.debug( "Created new store" );
    }


    @After
    public void destroyStore() throws Exception
    {
    }


    @Test
    public void testExampleDataIndices() throws Exception
    {
        assertEquals( 11, store.getRdnIndex().count() );
        assertEquals( 11, store.getOneLevelIndex().count() );
        assertEquals( 19, store.getSubLevelIndex().count() );
        assertEquals( 3, store.getAliasIndex().count() );
        assertEquals( 3, store.getOneAliasIndex().count() );
        assertEquals( 3, store.getSubAliasIndex().count() );
        assertEquals( 15, store.getPresenceIndex().count() );
        assertEquals( 27, store.getObjectClassIndex().count() );
        assertEquals( 11, store.getEntryCsnIndex().count() );
        assertEquals( 11, store.getEntryUuidIndex().count() );
        assertEquals( 3, store.getUserIndices().size() );
        assertEquals( 9, store.getUserIndex( SchemaConstants.OU_AT_OID ).count() );
        assertEquals( 0, store.getUserIndex( SchemaConstants.UID_AT_OID ).count() );
        assertEquals( 6, store.getUserIndex( SchemaConstants.CN_AT_OID ).count() );
    }


    /**
     * Adding an objectClass value should also add it to the objectClass index.
     */
    @Test
    public void testModifyAddObjectClass() throws Exception
    {
        DN dn = new DN( "cn=JOhnny WAlkeR,ou=Sales,o=Good Times Co." );
        dn.normalize( schemaManager.getNormalizerMapping() );

        List<Modification> mods = new ArrayList<Modification>();
        EntryAttribute attrib = new DefaultEntryAttribute( SchemaConstants.OBJECT_CLASS_AT, schemaManager
            .lookupAttributeTypeRegistry( SchemaConstants.OBJECT_CLASS_AT ) );

        String attribVal = "uidObject";
        attrib.add( attribVal );

        Modification add = new DefaultModification( ModificationOperation.ADD_ATTRIBUTE, attrib );
        mods.add( add );

        Long entryId = store.getEntryId( dn );
        Entry lookedup = store.lookup( entryId );

        // before modification: no "uidObject" tuple in objectClass index
        assertFalse( store.getObjectClassIndex().forward( "uidObject", entryId ) );
        assertFalse( lookedup.get( "objectClass" ).contains( "uidObject" ) );

        store.modify( dn, mods );

        // after modification: expect "uidObject" tuple in objectClass index
        assertTrue( store.getObjectClassIndex().forward( "uidObject", entryId ) );
        assertTrue( lookedup.get( "objectClass" ).contains( "uidObject" ) );
    }


    /**
     * Removing a value of an indexed attribute should also remove it from the index.
     */
    @Test
    public void testModifyRemoveIndexedAttribute() throws Exception
    {
        DN dn = new DN( "cn=JOhnny WAlkeR,ou=Sales,o=Good Times Co." );
        dn.normalize( schemaManager.getNormalizerMapping() );

        List<Modification> mods = new ArrayList<Modification>();
        EntryAttribute attrib = new DefaultEntryAttribute( SchemaConstants.OU_AT, schemaManager
            .lookupAttributeTypeRegistry( SchemaConstants.OU_AT ) );

        String attribVal = "sales";
        attrib.add( attribVal );

        Modification add = new DefaultModification( ModificationOperation.REMOVE_ATTRIBUTE, attrib );
        mods.add( add );

        Long entryId = store.getEntryId( dn );
        Entry lookedup = store.lookup( entryId );

        // before modification: expect "sales" tuple in ou index
        Index<String, Entry, Long> ouIndex = ( Index<String, Entry, Long> ) store.getUserIndex( SchemaConstants.OU_AT );
        assertTrue( ouIndex.forward( "sales", entryId ) );
        assertTrue( lookedup.get( "ou" ).contains( "sales" ) );

        store.modify( dn, mods );

        // after modification: no "sales" tuple in ou index
        assertFalse( ouIndex.forward( "sales", entryId ) );
        assertNull( lookedup.get( "ou" ) );
    }


    /**
     * Removing all values of an indexed attribute should not leave any tuples in the index,
     * nor in the presence index.
     */
    @Test
    public void testModifyRemoveAllIndexedAttribute() throws Exception
    {
        DN dn = new DN( "cn=JOhnny WAlkeR,ou=Sales,o=Good Times Co." );
        dn.normalize( schemaManager.getNormalizerMapping() );

        List<Modification> mods = new ArrayList<Modification>();
        EntryAttribute attrib = new DefaultEntryAttribute( SchemaConstants.OU_AT, schemaManager
            .lookupAttributeTypeRegistry( SchemaConstants.OU_AT ) );

        Modification add = new DefaultModification( ModificationOperation.REMOVE_ATTRIBUTE, attrib );
        mods.add( add );

        Long entryId = store.getEntryId( dn );
        Entry lookedup = store.lookup( entryId );

        // before modification: expect "sales" tuple in ou index
        Index<String, Entry, Long> ouIndex = ( Index<String, Entry, Long> ) store.getUserIndex( SchemaConstants.OU_AT );
        assertTrue( store.getPresenceIndex().forward( SchemaConstants.OU_AT_OID, entryId ) );
        assertTrue( ouIndex.forward( "sales", entryId ) );
        assertTrue( lookedup.get( "ou" ).contains( "sales" ) );

        store.modify( dn, mods );

        // after modification: no "sales" tuple in ou index
        assertFalse( store.getPresenceIndex().forward( SchemaConstants.OU_AT_OID, entryId ) );
        assertFalse( ouIndex.reverse( entryId ) );
        assertFalse( ouIndex.forward( "sales", entryId ) );
        assertNull( lookedup.get( "ou" ) );
    }


    /**
     * Removing an objectClass value should also remove it from the objectClass index.
     */
    @Test
    public void testModifyRemoveObjectClass() throws Exception
    {
        DN dn = new DN( "cn=JOhnny WAlkeR,ou=Sales,o=Good Times Co." );
        dn.normalize( schemaManager.getNormalizerMapping() );

        List<Modification> mods = new ArrayList<Modification>();
        EntryAttribute attrib = new DefaultEntryAttribute( SchemaConstants.OBJECT_CLASS_AT, schemaManager
            .lookupAttributeTypeRegistry( SchemaConstants.OBJECT_CLASS_AT ) );

        String attribVal = "person";
        attrib.add( attribVal );

        Modification add = new DefaultModification( ModificationOperation.REMOVE_ATTRIBUTE, attrib );
        mods.add( add );

        Long entryId = store.getEntryId( dn );
        Entry lookedup = store.lookup( entryId );

        // before modification: expect "person" tuple in objectClass index
        assertTrue( store.getObjectClassIndex().forward( "person", entryId ) );
        assertTrue( lookedup.get( "objectClass" ).contains( "person" ) );

        store.modify( dn, mods );

        // after modification: no "person" tuple in objectClass index
        assertFalse( store.getObjectClassIndex().forward( "person", entryId ) );
        assertFalse( lookedup.get( "objectClass" ).contains( "person" ) );
    }


    /**
     * Removing all values of the objectClass attribute should not leave any tuples in index.
     */
    @Test
    public void testModifyRemoveAllObjectClass() throws Exception
    {
        DN dn = new DN( "cn=JOhnny WAlkeR,ou=Sales,o=Good Times Co." );
        dn.normalize( schemaManager.getNormalizerMapping() );

        List<Modification> mods = new ArrayList<Modification>();
        EntryAttribute attrib = new DefaultEntryAttribute( SchemaConstants.OBJECT_CLASS_AT, schemaManager
            .lookupAttributeTypeRegistry( SchemaConstants.OBJECT_CLASS_AT ) );

        Modification add = new DefaultModification( ModificationOperation.REMOVE_ATTRIBUTE, attrib );
        mods.add( add );

        Long entryId = store.getEntryId( dn );
        Entry lookedup = store.lookup( entryId );

        // before modification: expect "person" tuple in objectClass index
        assertTrue( store.getObjectClassIndex().reverse( entryId ) );
        assertTrue( store.getObjectClassIndex().forward( "person", entryId ) );
        assertTrue( lookedup.get( "objectClass" ).contains( "person" ) );

        store.modify( dn, mods );

        // after modification: no tuple in objectClass index
        assertFalse( store.getObjectClassIndex().reverse( entryId ) );
        assertFalse( store.getObjectClassIndex().forward( "person", entryId ) );
        assertNull( lookedup.get( "objectClass" ) );
    }

    
    @Test
    public void testCheckCsnIndexUpdate() throws Exception
    {
        DN dn = new DN( "cn=JOhnny WAlkeR,ou=Sales,o=Good Times Co." );
        dn.normalize( schemaManager.getNormalizerMapping() );

        List<Modification> mods = new ArrayList<Modification>();
        AttributeType csnAt = schemaManager.lookupAttributeTypeRegistry( SchemaConstants.ENTRY_CSN_AT );
        EntryAttribute attrib = new DefaultEntryAttribute( csnAt );
        
        CsnFactory csnF = new CsnFactory( 0 );
        String csn = csnF.newInstance().toString();
        attrib.add( csn );

        Modification add = new DefaultModification( ModificationOperation.REPLACE_ATTRIBUTE, attrib );
        mods.add( add );

        Long entryId = store.getEntryId( dn );
        Entry lookedup = store.lookup( entryId );
        
        assertNotSame( csn, lookedup.get( csnAt ).getString() );
        assertNotSame( csn, store.getEntryCsnIndex().reverseLookup( entryId ) );

        store.modify( dn, mods );
        
        String updateCsn = lookedup.get( csnAt ).getString();
        assertEquals( csn, updateCsn );
        assertEquals( csn, store.getEntryCsnIndex().reverseLookup( entryId ) );
        
        csn = csnF.newInstance().toString();
        
        Entry modEntry = new DefaultEntry( schemaManager );
        modEntry.add( csnAt, csn );
        
        assertNotSame( csn, updateCsn );
        assertNotSame( csn, store.getEntryCsnIndex().reverseLookup( entryId ) );
        
        store.modify( dn, ModificationOperation.REPLACE_ATTRIBUTE, modEntry );
        
        assertEquals( csn, lookedup.get( csnAt ).getString() );
        assertEquals( csn, store.getEntryCsnIndex().reverseLookup( entryId ) );
    }
}
