/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.core.changelog;


import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.directory.server.constants.ApacheSchemaConstants;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.ServerEntryUtils;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.OperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.partition.ByPassConstants;
import org.apache.directory.server.core.schema.SchemaService;
import org.apache.directory.shared.ldap.codec.controls.ManageDsaITControl;
import org.apache.directory.shared.ldap.entry.DefaultEntry;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.ldif.ChangeType;
import org.apache.directory.shared.ldap.ldif.LdifEntry;
import org.apache.directory.shared.ldap.ldif.LdifRevertor;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * An interceptor which intercepts write operations to the directory and
 * logs them with the server's ChangeLog service.
 * Note: Adding/deleting a tag is not recorded as a change
 */
public class ChangeLogInterceptor extends BaseInterceptor
{
    /** for debugging */
    private static final Logger LOG = LoggerFactory.getLogger( ChangeLogInterceptor.class );
    
    /** used to ignore modify operations to tombstone entries */
    private AttributeType entryDeleted;
    
    /** the changelog service to log changes to */
    private ChangeLog changeLog;
    
    /** we need the schema service to deal with special conditions */
    private SchemaService schemaService;

    /** OID of the 'rev' attribute used in changeLogEvent and tag objectclasses */
    private static final String REV_AT_OID = "1.3.6.1.4.1.18060.0.4.1.2.47";
    
    // -----------------------------------------------------------------------
    // Overridden init() and destroy() methods
    // -----------------------------------------------------------------------


    /**
     * The init method will initialize the local variables and load the 
     * entryDeleted AttributeType.
     */
    public void init( DirectoryService directoryService ) throws LdapException
    {
        super.init( directoryService );

        changeLog = directoryService.getChangeLog();
        schemaService = directoryService.getSchemaService();
        entryDeleted = directoryService.getSchemaManager()
                .lookupAttributeTypeRegistry( ApacheSchemaConstants.ENTRY_DELETED_AT_OID );
    }


    // -----------------------------------------------------------------------
    // Overridden (only change inducing) intercepted methods
    // -----------------------------------------------------------------------
    

    public void add( NextInterceptor next, AddOperationContext addContext ) throws LdapException
    {
        next.add( addContext );

        if ( ! changeLog.isEnabled() || ! addContext.isFirstOperation() )
        {
            return;
        }

        Entry addEntry = addContext.getEntry();

        // we don't want to record addition of a tag as a change
        if( addEntry.get( REV_AT_OID ) != null )
        {
           return; 
        }
        
        LdifEntry forward = new LdifEntry();
        forward.setChangeType( ChangeType.Add );
        forward.setDn( addContext.getDn() );

        Set<AttributeType> list = addEntry.getAttributeTypes();
        
        for ( AttributeType attributeType:list )
        {
            forward.addAttribute( addEntry.get( attributeType).clone() );
        }
        
        LdifEntry reverse = LdifRevertor.reverseAdd( addContext.getDn() );
        addContext.setChangeLogEvent( changeLog.log( getPrincipal(), forward, reverse ) );
    }


    /**
     * The delete operation has to be stored with a way to restore the deleted element.
     * There is no way to do that but reading the entry and dump it into the LOG.
     */
    public void delete( NextInterceptor next, DeleteOperationContext deleteContext ) throws LdapException
    {
        // @todo make sure we're not putting in operational attributes that cannot be user modified
        // must save the entry if change log is enabled
        Entry serverEntry = null;

        if ( changeLog.isEnabled() && deleteContext.isFirstOperation() )
        {
            serverEntry = getAttributes( deleteContext );
        }

        next.delete( deleteContext );

        if ( ! changeLog.isEnabled() || ! deleteContext.isFirstOperation() )
        {
            return;
        }

        // we don't want to record deleting a tag as a change
        if( serverEntry.get( REV_AT_OID ) != null )
        {
           return; 
        }

        LdifEntry forward = new LdifEntry();
        forward.setChangeType( ChangeType.Delete );
        forward.setDn( deleteContext.getDn() );
        
        Entry reverseEntry = new DefaultEntry( serverEntry.getDn() );

        for ( EntryAttribute attribute : serverEntry )
        {
            // filter collective attributes, they can't be added by the revert operation
            AttributeType at = schemaService.getSchemaManager().getAttributeTypeRegistry().lookup( attribute.getId() );
            if ( !at.isCollective() )
            {
                reverseEntry.add( attribute.clone() );
            }
        }

        LdifEntry reverse = LdifRevertor.reverseDel( deleteContext.getDn(), reverseEntry );
        deleteContext.setChangeLogEvent( changeLog.log( getPrincipal(), forward, reverse ) );
    }


    /**
     * Gets attributes required for modifications.
     *
     * @param dn the dn of the entry to get
     * @return the entry's attributes (may be immutable if the schema subentry)
     * @throws Exception on error accessing the entry's attributes
     */
    private Entry getAttributes( OperationContext opContext ) throws LdapException
    {
        DN dn = opContext.getDn();
        Entry serverEntry;

        // @todo make sure we're not putting in operational attributes that cannot be user modified
        if ( schemaService.isSchemaSubentry( dn.getNormName() ) )
        {
            return schemaService.getSubschemaEntryCloned();
        }
        else
        {
            serverEntry = opContext.lookup( dn, ByPassConstants.LOOKUP_BYPASS );
        }

        return serverEntry;
    }


    /**
     * 
     */
    public void modify( NextInterceptor next, ModifyOperationContext modifyContext ) throws LdapException
    {
        Entry serverEntry = null;
        Modification modification = ServerEntryUtils.getModificationItem( modifyContext.getModItems(), entryDeleted );
        boolean isDelete = ( modification != null );

        if ( ! isDelete && ( changeLog.isEnabled() && modifyContext.isFirstOperation() ) )
        {
            // @todo make sure we're not putting in operational attributes that cannot be user modified
            serverEntry = getAttributes( modifyContext );
        }
        
        // Duplicate modifications so that the reverse does not contain the operational attributes
        List<Modification> clonedMods = new ArrayList<Modification>(); 

        for ( Modification mod : modifyContext.getModItems() )
        {
            clonedMods.add( mod.clone() );
        }

        // Call the next interceptor
        next.modify( modifyContext );

        // @TODO: needs big consideration!!!
        // NOTE: perhaps we need to log this as a system operation that cannot and should not be reapplied?
        if ( 
            isDelete ||   
            ! changeLog.isEnabled() || 
            ! modifyContext.isFirstOperation() ||
            
         // if there are no modifications due to stripping out bogus non-
         // existing attributes then we will have no modification items and
         // should ignore not this without registering it with the changelog
         
            modifyContext.getModItems().size() == 0 )  
        {
            if ( isDelete )
            {
                LOG.debug( "Bypassing changelog on modify of entryDeleted attribute." );
            }
            
            return;
        }

        LdifEntry forward = new LdifEntry();
        forward.setChangeType( ChangeType.Modify );
        forward.setDn( modifyContext.getDn() );
        
        List<Modification> mods = new ArrayList<Modification>( clonedMods.size() );
        
        for ( Modification modItem : clonedMods )
        {
            // TODO: handle correctly http://issues.apache.org/jira/browse/DIRSERVER-1198
            mods.add( modItem );
            
            forward.addModificationItem( modItem );
        }
        
        Entry clientEntry = new DefaultEntry( serverEntry.getDn() );
        
        for ( EntryAttribute attribute:serverEntry )
        {
            clientEntry.add( attribute.clone() );
        }

        LdifEntry reverse = LdifRevertor.reverseModify( 
            modifyContext.getDn(), 
            mods, 
            clientEntry );
        
        modifyContext.setChangeLogEvent( changeLog.log( getPrincipal(), forward, reverse ) );
    }


    // -----------------------------------------------------------------------
    // Though part left as an exercise (Not Any More!)
    // -----------------------------------------------------------------------


    public void rename ( NextInterceptor next, RenameOperationContext renameContext ) throws LdapException
    {
        Entry serverEntry = null;
        
        if ( renameContext.getEntry() != null )
        {
            serverEntry = renameContext.getEntry().getOriginalEntry();
        }
        
        next.rename( renameContext );
        
        // After this point, the entry has been modified. The cloned entry contains
        // the modified entry, the originalEntry has changed

        if ( ! changeLog.isEnabled() || ! renameContext.isFirstOperation() )
        {
            return;
        }

        LdifEntry forward = new LdifEntry();
        forward.setChangeType( ChangeType.ModRdn );
        forward.setDn( renameContext.getDn() );
        forward.setNewRdn( renameContext.getNewRdn().getName() );
        forward.setDeleteOldRdn( renameContext.getDeleteOldRdn() );

        List<LdifEntry> reverses = LdifRevertor.reverseRename( 
            serverEntry, renameContext.getNewRdn(), renameContext.getDeleteOldRdn() );
        
        renameContext.setChangeLogEvent( changeLog.log( getPrincipal(), forward, reverses ) );
    }


    public void moveAndRename( NextInterceptor next, MoveAndRenameOperationContext moveAndRenameContext )
        throws LdapException
    {
        Entry serverEntry = null;
        
        if ( changeLog.isEnabled() && moveAndRenameContext.isFirstOperation() )
        {
            // @todo make sure we're not putting in operational attributes that cannot be user modified
            serverEntry = moveAndRenameContext.getOriginalEntry();
        }

        next.moveAndRename( moveAndRenameContext );

        if ( ! changeLog.isEnabled() || ! moveAndRenameContext.isFirstOperation() )
        {
            return;
        }

        LdifEntry forward = new LdifEntry();
        forward.setChangeType( ChangeType.ModDn );
        forward.setDn( moveAndRenameContext.getDn() );
        forward.setDeleteOldRdn( moveAndRenameContext.getDeleteOldRdn() );
        forward.setNewRdn( moveAndRenameContext.getNewRdn().getName() );
        forward.setNewSuperior( moveAndRenameContext.getNewSuperiorDn().getName() );
        
        List<LdifEntry> reverses = LdifRevertor.reverseMoveAndRename(  
            serverEntry, moveAndRenameContext.getNewSuperiorDn(), moveAndRenameContext.getNewRdn(), false );
        
        if ( moveAndRenameContext.isReferralIgnored() )
        {
            forward.addControl( new ManageDsaITControl() );
            LdifEntry reversedEntry = reverses.get( 0 );
            reversedEntry.addControl( new ManageDsaITControl() );
        }
        
        moveAndRenameContext.setChangeLogEvent( changeLog.log( getPrincipal(), forward, reverses ) );
    }


    /**
     * {@inheritDoc}
     */
    public void move( NextInterceptor next, MoveOperationContext moveContext ) throws LdapException
    {
        next.move( moveContext );

        if ( ! changeLog.isEnabled() || ! moveContext.isFirstOperation() )
        {
            return;
        }

        LdifEntry forward = new LdifEntry();
        forward.setChangeType( ChangeType.ModDn );
        forward.setDn( moveContext.getDn() );
        forward.setNewSuperior( moveContext.getNewSuperior().getName() );

        LdifEntry reverse = LdifRevertor.reverseMove( moveContext.getNewSuperior(), moveContext.getDn() );
        moveContext.setChangeLogEvent( changeLog.log( getPrincipal(), forward, reverse ) );
    }
}
