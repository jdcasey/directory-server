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
package org.apache.directory.server.core.partition.ldif;


import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.util.Iterator;
import java.util.List;

import javax.naming.InvalidNameException;

import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.entry.DefaultServerEntry;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.UnbindOperationContext;
import org.apache.directory.server.core.partition.avl.AvlPartition;
import org.apache.directory.server.core.partition.impl.btree.BTreePartition;
import org.apache.directory.server.xdbm.Index;
import org.apache.directory.server.xdbm.IndexCursor;
import org.apache.directory.shared.ldap.ldif.LdifEntry;
import org.apache.directory.shared.ldap.ldif.LdifReader;
import org.apache.directory.shared.ldap.ldif.LdifUtils;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.registries.Registries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * TODO LdifPartition.
 * 
 * @org.apache.xbean.XBean
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class LdifPartition extends BTreePartition
{
    private LdifReader ldifParser = new LdifReader();

    private File configParentDirectory;

    private DirectoryService directoryService;

    private int ldifScanInterval;

    private FileFilter dirFilter = new FileFilter()
    {
        public boolean accept( File dir )
        {
            return dir.isDirectory();
        }
    };

    private static final String CONF_FILE_EXTN = ".ldif";

    private static Logger LOG = LoggerFactory.getLogger( LdifPartition.class );

    private AvlPartition wrappedPartition = new AvlPartition();

    public LdifPartition()
    {
    }



    public void initialize( ) throws Exception
    {
        wrappedPartition.initialize( );

        this.directoryService = directoryService;
        this.registries = directoryService.getRegistries();
        this.searchEngine = wrappedPartition.getSearchEngine();

        LOG.debug( "id is : {}", wrappedPartition.getId() );
        this.configParentDirectory = new File( directoryService.getWorkingDirectory().getPath() + File.separator
            + wrappedPartition.getId() );
        //        configParentDirectory.mkdir();
        // load the config 
        loadConfig();
    }


    @Override
    public void add( AddOperationContext addContext ) throws Exception
    {
        wrappedPartition.add( addContext );
        add( addContext.getEntry() );
    }


    @Override
    public void delete( Long id ) throws Exception
    {
        ServerEntry entry = lookup( id );

        wrappedPartition.delete( id );

        if ( entry != null )
        {
            File file = getFile( entry.getDn() ).getParentFile();
            boolean deleted = deleteFile( file );
            LOG.warn( "deleted file {} {}", file.getAbsoluteFile(), deleted );
        }

    }


    private void entryMoved( LdapDN entryDn, Long entryId ) throws Exception
    {
        File file = getFile( entryDn ).getParentFile();
        boolean deleted = deleteFile( file );
        LOG.warn( "move operation: deleted file {} {}", file.getAbsoluteFile(), deleted );

        add( lookup( entryId ) );

        IndexCursor<Long, ServerEntry> cursor = getSubLevelIndex().forwardCursor( entryId );
        while ( cursor.next() )
        {
            add( cursor.get().getObject() );
        }

        cursor.close();
    }


    @Override
    public void modify( ModifyOperationContext modifyContext ) throws Exception
    {
        wrappedPartition.modify( modifyContext );
        // just overwrite the existing file
        add( modifyContext.getEntry() );
    }


    @Override
    public void move( MoveOperationContext moveContext ) throws Exception
    {
        LdapDN oldDn = moveContext.getDn();
        Long id = getEntryId( oldDn.getNormName() );

        wrappedPartition.move( moveContext );

        entryMoved( oldDn, id );
    }


    @Override
    public void moveAndRename( MoveAndRenameOperationContext moveAndRenameContext ) throws Exception
    {
        LdapDN oldDn = moveAndRenameContext.getDn();
        Long id = getEntryId( oldDn.getNormName() );

        wrappedPartition.moveAndRename( moveAndRenameContext );

        entryMoved( oldDn, id );
    }


    @Override
    public void rename( RenameOperationContext renameContext ) throws Exception
    {
        LdapDN oldDn = renameContext.getDn();
        Long id = getEntryId( oldDn.getNormName() );

        wrappedPartition.rename( renameContext );

        entryMoved( oldDn, id );
    }


    /**
     * loads the configuration into the DIT from the file system
     * Note that it assumes the presence of a directory with the partition suffix's upname
     * under the partition's base dir
     * 
     * for ex. if 'config' is the partition's id and 'ou=config' is its suffix it looks for the dir with the path
     * 
     * <directory-service-working-dir>/config/ou=config
     * e.x example.com/config/ou=config
     * 
     * NOTE: this dir setup is just to ease the testing of this partition, this needs to be 
     * replaced with some kind of bootstrapping the default config from a jar file and
     * write to the FS in LDIF format
     * 
     * @throws Exception
     */
    public void loadConfig() throws Exception
    {
        File dir = new File( configParentDirectory, wrappedPartition.getSuffixDn().getUpName() );

        //        if( ! dir.exists() )
        //        {
        //            throw new Exception( "The specified configuration dir " + getSuffix().toLowerCase() + " doesn't exist under " + configParentDirectory.getAbsolutePath() );
        //        }

        loadEntry( dir );
    }


    /*
     * recursively load the configuration entries
     */
    private void loadEntry( File entryDir ) throws Exception
    {
        LOG.error( "processing dir {}", entryDir.getName() );

        File ldifFile = new File( entryDir, entryDir.getName() + CONF_FILE_EXTN );

        if ( ldifFile.exists() )
        {
            LOG.debug( "parsing ldif file {}", ldifFile.getName() );
            List<LdifEntry> entries = ldifParser.parseLdifFile( ldifFile.getAbsolutePath() );
            if ( entries != null && !entries.isEmpty() )
            {
                // this ldif will have only one entry
                LdifEntry ldifEntry = entries.get( 0 );
                LOG.debug( "adding entry {}", ldifEntry );

                ServerEntry serverEntry = new DefaultServerEntry( registries, ldifEntry.getEntry() );

                // call add on the wrapped partition not on the self
                wrappedPartition.getStore().add( serverEntry );
            }
        }
        else
        {
            // TODO do we need to bomb out if the expected LDIF file doesn't exist
            LOG.warn( "ldif file doesn't exist {}", ldifFile.getAbsolutePath() );
        }

        File[] dirs = entryDir.listFiles( dirFilter );
        if ( dirs != null )
        {
            for ( File f : dirs )
            {
                loadEntry( f );
            }
        }
    }


    private File getFile( LdapDN entryDn )
    {
        int size = entryDn.size();

        StringBuilder filePath = new StringBuilder();
        filePath.append( configParentDirectory.getAbsolutePath() ).append( File.separator );

        for ( int i = 0; i < size; i++ )
        {
            filePath.append( entryDn.getRdn( i ).getUpName().toLowerCase() ).append( File.separator );
        }

        File dir = new File( filePath.toString() );
        dir.mkdirs();

        return new File( dir, entryDn.getRdn().getUpName().toLowerCase() + CONF_FILE_EXTN );
    }


    private void add( ServerEntry entry ) throws Exception
    {
        FileWriter fw = new FileWriter( getFile( entry.getDn() ) );
        fw.write( LdifUtils.convertEntryToLdif( entry ) );
        fw.close();
    }


    private boolean deleteFile( File file )
    {
        if ( file.isDirectory() )
        {
            File[] files = file.listFiles();
            for ( File f : files )
            {
                deleteFile( f );
            }

            return file.delete();
        }
        else
        {
            return file.delete();
        }
    }


    @Override
    public void addIndexOn( Index<Long, ServerEntry> index ) throws Exception
    {
        wrappedPartition.addIndexOn( index );
    }


    @Override
    public int count() throws Exception
    {
        return wrappedPartition.count();
    }


    @Override
    public void destroy() throws Exception
    {
        wrappedPartition.destroy();
    }


    @Override
    public Index<String, ServerEntry> getAliasIndex()
    {
        return wrappedPartition.getAliasIndex();
    }


    @Override
    public int getChildCount( Long id ) throws Exception
    {
        return wrappedPartition.getChildCount( id );
    }


    @Override
    public String getEntryDn( Long id ) throws Exception
    {
        return wrappedPartition.getEntryDn( id );
    }


    @Override
    public Long getEntryId( String dn ) throws Exception
    {
        return wrappedPartition.getEntryId( dn );
    }


    @Override
    public String getEntryUpdn( Long id ) throws Exception
    {
        return wrappedPartition.getEntryUpdn( id );
    }


    @Override
    public String getEntryUpdn( String dn ) throws Exception
    {
        return wrappedPartition.getEntryUpdn( dn );
    }


    @Override
    public Index<String, ServerEntry> getNdnIndex()
    {
        return wrappedPartition.getNdnIndex();
    }


    @Override
    public Index<Long, ServerEntry> getOneAliasIndex()
    {
        return wrappedPartition.getOneAliasIndex();
    }


    @Override
    public Index<Long, ServerEntry> getOneLevelIndex()
    {
        return wrappedPartition.getOneLevelIndex();
    }


    @Override
    public Long getParentId( Long childId ) throws Exception
    {
        return wrappedPartition.getParentId( childId );
    }


    @Override
    public Long getParentId( String dn ) throws Exception
    {
        return wrappedPartition.getParentId( dn );
    }


    @Override
    public Index<String, ServerEntry> getPresenceIndex()
    {
        return wrappedPartition.getPresenceIndex();
    }


    @Override
    public String getProperty( String propertyName ) throws Exception
    {
        return wrappedPartition.getProperty( propertyName );
    }


    @Override
    public Index<Long, ServerEntry> getSubAliasIndex()
    {
        return wrappedPartition.getSubAliasIndex();
    }


    @Override
    public Index<Long, ServerEntry> getSubLevelIndex()
    {
        return wrappedPartition.getSubLevelIndex();
    }


    @Override
    public Index<?, ServerEntry> getSystemIndex( String id ) throws Exception
    {
        return wrappedPartition.getSystemIndex( id );
    }


    @Override
    public Iterator<String> getSystemIndices()
    {
        return wrappedPartition.getSystemIndices();
    }


    @Override
    public Index<String, ServerEntry> getUpdnIndex()
    {
        return wrappedPartition.getUpdnIndex();
    }


    @Override
    public Index<?, ServerEntry> getUserIndex( String id ) throws Exception
    {
        return wrappedPartition.getUserIndex( id );
    }


    @Override
    public Iterator<String> getUserIndices()
    {
        return wrappedPartition.getUserIndices();
    }


    @Override
    public boolean hasSystemIndexOn( String id ) throws Exception
    {
        return wrappedPartition.hasSystemIndexOn( id );
    }


    @Override
    public boolean hasUserIndexOn( String id ) throws Exception
    {
        return wrappedPartition.hasUserIndexOn( id );
    }


    @Override
    public boolean isInitialized()
    {
        return wrappedPartition.isInitialized();
    }


    @Override
    public IndexCursor<Long, ServerEntry> list( Long id ) throws Exception
    {
        return wrappedPartition.list( id );
    }


    @Override
    public ClonedServerEntry lookup( Long id ) throws Exception
    {
        return wrappedPartition.lookup( id );
    }


    @Override
    public void setAliasIndexOn( Index<String, ServerEntry> index ) throws Exception
    {
        wrappedPartition.setAliasIndexOn( index );
    }


    @Override
    public void setNdnIndexOn( Index<String, ServerEntry> index ) throws Exception
    {
        wrappedPartition.setNdnIndexOn( index );
    }


    @Override
    public void setOneAliasIndexOn( Index<Long, ServerEntry> index ) throws Exception
    {
        wrappedPartition.setOneAliasIndexOn( index );
    }


    @Override
    public void setOneLevelIndexOn( Index<Long, ServerEntry> index ) throws Exception
    {
        wrappedPartition.setOneLevelIndexOn( index );
    }


    @Override
    public void setPresenceIndexOn( Index<String, ServerEntry> index ) throws Exception
    {
        wrappedPartition.setPresenceIndexOn( index );
    }


    @Override
    public void setProperty( String propertyName, String propertyValue ) throws Exception
    {
        wrappedPartition.setProperty( propertyName, propertyValue );
    }


    @Override
    public void setRegistries( Registries registries )
    {
        super.setRegistries( registries );
        wrappedPartition.setRegistries( registries );
    }


    @Override
    public void setSubAliasIndexOn( Index<Long, ServerEntry> index ) throws Exception
    {
        wrappedPartition.setSubAliasIndexOn( index );
    }


    @Override
    public void setUpdnIndexOn( Index<String, ServerEntry> index ) throws Exception
    {
        wrappedPartition.setUpdnIndexOn( index );
    }


    @Override
    public void sync() throws Exception
    {
        wrappedPartition.sync();
        //TODO implement the File I/O here to push the update to entries to the corresponding LDIF file
    }


    public void bind( BindOperationContext bindContext ) throws Exception
    {
        wrappedPartition.bind( bindContext );
    }

    
    public String getSuffix()
    {
        return wrappedPartition.getSuffix();
    }
    

    public LdapDN getSuffixDn()
    {
        return wrappedPartition.getSuffixDn();
    }


    public void unbind( UnbindOperationContext unbindContext ) throws Exception
    {
        wrappedPartition.unbind( unbindContext );
    }


    @Override
    public String getId()
    {
        // TODO Auto-generated method stub
        return super.getId();
    }


    @Override
    public void setId( String id )
    {
        super.setId( id );
        wrappedPartition.setId( id );
    }


    @Override
    public void setSuffix( String suffix ) throws InvalidNameException
    {
        super.setSuffix( suffix );
        wrappedPartition.setSuffix( suffix );
    }


    /**
     * the interval at which the config directory containing LDIF files
     * should be scanned, default value is 10 min
     * 
     * @param ldifScanInterval the scan interval time in minutes
     */
    public void setLdifScanInterval( int ldifScanInterval )
    {
        this.ldifScanInterval = ldifScanInterval;
    }
}