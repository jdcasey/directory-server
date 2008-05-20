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
package org.apache.directory.server.core.subtree;


import org.apache.directory.server.core.interceptor.BaseInterceptor;

import org.apache.directory.server.constants.ApacheSchemaConstants;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.DefaultServerAttribute;
import org.apache.directory.server.core.entry.DefaultServerEntry;
import org.apache.directory.server.core.entry.ServerAttribute;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.entry.ServerModification;
import org.apache.directory.server.core.entry.ServerSearchResult;
import org.apache.directory.server.core.enumeration.SearchResultFilter;
import org.apache.directory.server.core.enumeration.SearchResultFilteringEnumeration;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.invocation.Invocation;
import org.apache.directory.server.core.invocation.InvocationStack;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.server.schema.registries.OidRegistry;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.entry.client.ClientStringValue;
import org.apache.directory.shared.ldap.exception.LdapInvalidAttributeValueException;
import org.apache.directory.shared.ldap.exception.LdapNoSuchAttributeException;
import org.apache.directory.shared.ldap.exception.LdapSchemaViolationException;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.message.SubentriesControl;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.NormalizerMappingResolver;
import org.apache.directory.shared.ldap.schema.OidNormalizer;
import org.apache.directory.shared.ldap.subtree.SubtreeSpecification;
import org.apache.directory.shared.ldap.subtree.SubtreeSpecificationParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * The Subentry interceptor service which is responsible for filtering
 * out subentries on search operations and injecting operational attributes
 *
 * @org.apache.xbean.XBean
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class SubentryInterceptor extends BaseInterceptor
{
    /** the subentry control OID */
    private static final String SUBENTRY_CONTROL = SubentriesControl.CONTROL_OID;

    public static final String AC_AREA = "accessControlSpecificArea";
    public static final String AC_INNERAREA = "accessControlInnerArea";

    public static final String SCHEMA_AREA = "subschemaAdminSpecificArea";

    public static final String COLLECTIVE_AREA = "collectiveAttributeSpecificArea";
    public static final String COLLECTIVE_INNERAREA = "collectiveAttributeInnerArea";

    public static final String TRIGGER_AREA = "triggerExecutionSpecificArea";
    public static final String TRIGGER_INNERAREA = "triggerExecutionInnerArea";

    public static final String[] SUBENTRY_OPATTRS =
        { SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT, SchemaConstants.SUBSCHEMA_SUBENTRY_AT,
            SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT, SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT };

    private static final Logger LOG = LoggerFactory.getLogger( SubentryInterceptor.class );

    /** the hash mapping the DN of a subentry to its SubtreeSpecification/types */
    private final SubentryCache subentryCache = new SubentryCache();

    private SubtreeSpecificationParser ssParser;
    private SubtreeEvaluator evaluator;
    private PartitionNexus nexus;

    /** The global registries */
    private Registries registries;

    /** The AttributeType registry */
    private AttributeTypeRegistry atRegistry;

    /** The OID registry */
    private OidRegistry oidRegistry;

    private AttributeType objectClassType;


    public void init( DirectoryService directoryService ) throws NamingException
    {
        super.init( directoryService );
        nexus = directoryService.getPartitionNexus();
        registries = directoryService.getRegistries();
        atRegistry = registries.getAttributeTypeRegistry();
        oidRegistry = registries.getOidRegistry();

        // setup various attribute type values
        objectClassType = atRegistry.lookup( oidRegistry.getOid( SchemaConstants.OBJECT_CLASS_AT ) );

        ssParser = new SubtreeSpecificationParser( new NormalizerMappingResolver()
        {
            public Map<String, OidNormalizer> getNormalizerMapping() throws NamingException
            {
                return atRegistry.getNormalizerMapping();
            }
        }, atRegistry.getNormalizerMapping() );
        evaluator = new SubtreeEvaluator( oidRegistry, atRegistry );

        // prepare to find all subentries in all namingContexts
        Iterator<String> suffixes = this.nexus.listSuffixes( null );
        ExprNode filter = new EqualityNode( SchemaConstants.OBJECT_CLASS_AT, new ClientStringValue(
            SchemaConstants.SUBENTRY_OC ) );
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setReturningAttributes( new String[]
            { SchemaConstants.SUBTREE_SPECIFICATION_AT, SchemaConstants.OBJECT_CLASS_AT } );

        // search each namingContext for subentries
        while ( suffixes.hasNext() )
        {
            LdapDN suffix = new LdapDN( suffixes.next() );
            //suffix = LdapDN.normalize( suffix, registry.getNormalizerMapping() );
            suffix.normalize( atRegistry.getNormalizerMapping() );

            NamingEnumeration<ServerSearchResult> subentries = nexus.search( new SearchOperationContext( registries,
                suffix, AliasDerefMode.NEVER_DEREF_ALIASES, filter, controls ) );

            while ( subentries.hasMore() )
            {
                ServerSearchResult result = subentries.next();
                LdapDN dnName = new LdapDN( result.getDn() );

                ServerEntry subentry = result.getServerEntry();
                String subtree = subentry.get( SchemaConstants.SUBTREE_SPECIFICATION_AT ).getString();
                SubtreeSpecification ss;

                try
                {
                    ss = ssParser.parse( subtree );
                }
                catch ( Exception e )
                {
                    LOG.warn( "Failed while parsing subtreeSpecification for " + dnName );
                    continue;
                }

                dnName.normalize( atRegistry.getNormalizerMapping() );
                subentryCache.setSubentry( dnName.toString(), ss, getSubentryTypes( subentry ) );
            }
        }
    }


    private int getSubentryTypes( ServerEntry subentry ) throws NamingException
    {
        int types = 0;

        EntryAttribute oc = subentry.get( SchemaConstants.OBJECT_CLASS_AT );

        if ( oc == null )
        {
            throw new LdapSchemaViolationException( "A subentry must have an objectClass attribute",
                ResultCodeEnum.OBJECT_CLASS_VIOLATION );
        }

        if ( oc.contains( SchemaConstants.ACCESS_CONTROL_SUBENTRY_OC ) )
        {
            types |= Subentry.ACCESS_CONTROL_SUBENTRY;
        }

        if ( oc.contains( "subschema" ) )
        {
            types |= Subentry.SCHEMA_SUBENTRY;
        }

        if ( oc.contains( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRY_OC ) )
        {
            types |= Subentry.COLLECTIVE_SUBENTRY;
        }

        if ( oc.contains( ApacheSchemaConstants.TRIGGER_EXECUTION_SUBENTRY_OC ) )
        {
            types |= Subentry.TRIGGER_SUBENTRY;
        }

        return types;
    }


    // -----------------------------------------------------------------------
    // Methods/Code dealing with Subentry Visibility
    // -----------------------------------------------------------------------

    public NamingEnumeration<ServerSearchResult> list( NextInterceptor nextInterceptor, ListOperationContext opContext )
        throws NamingException
    {
        NamingEnumeration<ServerSearchResult> result = nextInterceptor.list( opContext );
        Invocation invocation = InvocationStack.getInstance().peek();

        if ( !isSubentryVisible( invocation ) )
        {
            return new SearchResultFilteringEnumeration( result, new SearchControls(), invocation,
                new HideSubentriesFilter(), "List Subentry filter" );
        }

        return result;
    }


    public NamingEnumeration<ServerSearchResult> search( NextInterceptor nextInterceptor,
        SearchOperationContext opContext ) throws NamingException
    {
        NamingEnumeration<ServerSearchResult> result = nextInterceptor.search( opContext );
        Invocation invocation = InvocationStack.getInstance().peek();
        SearchControls searchCtls = opContext.getSearchControls();

        // object scope searches by default return subentries
        if ( searchCtls.getSearchScope() == SearchControls.OBJECT_SCOPE )
        {
            return result;
        }

        // for subtree and one level scope we filter
        if ( !isSubentryVisible( invocation ) )
        {
            return new SearchResultFilteringEnumeration( result, searchCtls, invocation, new HideSubentriesFilter(),
                "Search Subentry filter hide subentries" );
        }
        else
        {
            return new SearchResultFilteringEnumeration( result, searchCtls, invocation, new HideEntriesFilter(),
                "Search Subentry filter hide entries" );
        }
    }


    /**
     * Checks to see if subentries for the search and list operations should be
     * made visible based on the availability of the search request control
     *
     * @param invocation the invocation object to use for determining subentry visibility
     * @return true if subentries should be visible, false otherwise
     * @throws NamingException if there are problems accessing request controls
     */
    private boolean isSubentryVisible( Invocation invocation ) throws NamingException
    {
        Control[] reqControls = ( ( LdapContext ) invocation.getCaller() ).getRequestControls();

        if ( reqControls == null || reqControls.length <= 0 )
        {
            return false;
        }

        // check all request controls to see if subentry control is present
        for ( Control reqControl : reqControls )
        {
            // found the subentry request control so we return its value
            if ( reqControl.getID().equals( SUBENTRY_CONTROL ) )
            {
                SubentriesControl subentriesControl = ( SubentriesControl ) reqControl;
                return subentriesControl.isVisible();
            }
        }

        return false;
    }


    // -----------------------------------------------------------------------
    // Methods dealing with entry and subentry addition
    // -----------------------------------------------------------------------

    /**
     * Evaluates the set of subentry subtrees upon an entry and returns the
     * operational subentry attributes that will be added to the entry if
     * added at the dn specified.
     *
     * @param dn the normalized distinguished name of the entry
     * @param entryAttrs the entry attributes are generated for
     * @return the set of subentry op attrs for an entry
     * @throws NamingException if there are problems accessing entry information
     */
    public ServerEntry getSubentryAttributes( LdapDN dn, ServerEntry entryAttrs ) throws NamingException
    {
        ServerEntry subentryAttrs = new DefaultServerEntry( registries, dn );
        Iterator<String> list = subentryCache.nameIterator();

        while ( list.hasNext() )
        {
            String subentryDnStr = list.next();
            LdapDN subentryDn = new LdapDN( subentryDnStr );
            LdapDN apDn = ( LdapDN ) subentryDn.clone();
            apDn.remove( apDn.size() - 1 );
            Subentry subentry = subentryCache.getSubentry( subentryDnStr );
            SubtreeSpecification ss = subentry.getSubtreeSpecification();

            if ( evaluator.evaluate( ss, apDn, dn, entryAttrs ) )
            {
                EntryAttribute operational;

                if ( subentry.isAccessControlSubentry() )
                {
                    operational = subentryAttrs.get( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT );

                    if ( operational == null )
                    {
                        operational = new DefaultServerAttribute( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT,
                            atRegistry.lookup( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT ) );
                        subentryAttrs.put( operational );
                    }

                    operational.add( subentryDn.toString() );
                }
                if ( subentry.isSchemaSubentry() )
                {
                    operational = subentryAttrs.get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT );

                    if ( operational == null )
                    {
                        operational = new DefaultServerAttribute( SchemaConstants.SUBSCHEMA_SUBENTRY_AT, atRegistry
                            .lookup( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ) );
                        subentryAttrs.put( operational );
                    }

                    operational.add( subentryDn.toString() );
                }
                if ( subentry.isCollectiveSubentry() )
                {
                    operational = subentryAttrs.get( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT );

                    if ( operational == null )
                    {
                        operational = new DefaultServerAttribute( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT,
                            atRegistry.lookup( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT ) );
                        subentryAttrs.put( operational );
                    }

                    operational.add( subentryDn.toString() );
                }
                if ( subentry.isTriggerSubentry() )
                {
                    operational = subentryAttrs.get( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT );

                    if ( operational == null )
                    {
                        operational = new DefaultServerAttribute( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT,
                            atRegistry.lookup( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT ) );
                        subentryAttrs.put( operational );
                    }

                    operational.add( subentryDn.toString() );
                }
            }
        }

        return subentryAttrs;
    }


    public void add( NextInterceptor next, AddOperationContext addContext ) throws NamingException
    {
        LdapDN name = addContext.getDn();
        ServerEntry entry = addContext.getEntry();

        EntryAttribute objectClasses = entry.get( SchemaConstants.OBJECT_CLASS_AT );

        if ( objectClasses.contains( SchemaConstants.SUBENTRY_OC ) )
        {
            // get the name of the administrative point and its administrativeRole attributes
            LdapDN apName = ( LdapDN ) name.clone();
            apName.remove( name.size() - 1 );
            ServerEntry ap = nexus.lookup( new LookupOperationContext( registries, apName ) );
            EntryAttribute administrativeRole = ap.get( "administrativeRole" );

            // check that administrativeRole has something valid in it for us
            if ( administrativeRole == null || administrativeRole.size() <= 0 )
            {
                throw new LdapNoSuchAttributeException( "Administration point " + apName
                    + " does not contain an administrativeRole attribute! An"
                    + " administrativeRole attribute in the administrative point is"
                    + " required to add a subordinate subentry." );
            }

            /* ----------------------------------------------------------------
             * Build the set of operational attributes to be injected into
             * entries that are contained within the subtree repesented by this
             * new subentry.  In the process we make sure the proper roles are
             * supported by the administrative point to allow the addition of
             * this new subentry.
             * ----------------------------------------------------------------
             */
            Subentry subentry = new Subentry();
            subentry.setTypes( getSubentryTypes( entry ) );
            ServerEntry operational = getSubentryOperatationalAttributes( name, subentry );

            /* ----------------------------------------------------------------
             * Parse the subtreeSpecification of the subentry and add it to the
             * SubtreeSpecification cache.  If the parse succeeds we continue
             * to add the entry to the DIT.  Thereafter we search out entries
             * to modify the subentry operational attributes of.
             * ----------------------------------------------------------------
             */
            String subtree = entry.get( SchemaConstants.SUBTREE_SPECIFICATION_AT ).getString();
            SubtreeSpecification ss;

            try
            {
                ss = ssParser.parse( subtree );
            }
            catch ( Exception e )
            {
                String msg = "Failed while parsing subtreeSpecification for " + name.getUpName();
                LOG.warn( msg );
                throw new LdapInvalidAttributeValueException( msg, ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX );
            }

            subentryCache.setSubentry( name.getNormName(), ss, getSubentryTypes( entry ) );

            next.add( addContext );

            /* ----------------------------------------------------------------
             * Find the baseDn for the subentry and use that to search the tree
             * while testing each entry returned for inclusion within the
             * subtree of the subentry's subtreeSpecification.  All included
             * entries will have their operational attributes merged with the
             * operational attributes calculated above.
             * ----------------------------------------------------------------
             */
            LdapDN baseDn = ( LdapDN ) apName.clone();
            baseDn.addAll( ss.getBase() );

            ExprNode filter = new PresenceNode( SchemaConstants.OBJECT_CLASS_AT_OID ); // (objectClass=*)
            SearchControls controls = new SearchControls();
            controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            controls.setReturningAttributes( new String[]
                { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );

            NamingEnumeration<ServerSearchResult> subentries = nexus.search( new SearchOperationContext( registries,
                baseDn, AliasDerefMode.NEVER_DEREF_ALIASES, filter, controls ) );

            while ( subentries.hasMore() )
            {
                ServerSearchResult result = subentries.next();
                LdapDN dn = result.getDn();
                dn.normalize( atRegistry.getNormalizerMapping() );
                ServerEntry candidate = result.getServerEntry();

                if ( evaluator.evaluate( ss, apName, dn, candidate ) )
                {
                    nexus.modify( new ModifyOperationContext( registries, dn, getOperationalModsForAdd( candidate,
                        operational ) ) );
                }
            }

            addContext.setEntry( entry );
        }
        else
        {
            Iterator<String> list = subentryCache.nameIterator();

            while ( list.hasNext() )
            {
                String subentryDnStr = list.next();
                LdapDN subentryDn = new LdapDN( subentryDnStr );
                LdapDN apDn = ( LdapDN ) subentryDn.clone();
                apDn.remove( apDn.size() - 1 );
                Subentry subentry = subentryCache.getSubentry( subentryDnStr );
                SubtreeSpecification ss = subentry.getSubtreeSpecification();

                if ( evaluator.evaluate( ss, apDn, name, entry ) )
                {
                    EntryAttribute operational;

                    if ( subentry.isAccessControlSubentry() )
                    {
                        operational = entry.get( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT );

                        if ( operational == null )
                        {
                            operational = new DefaultServerAttribute( atRegistry
                                .lookup( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT ) );
                            entry.put( operational );
                        }

                        operational.add( subentryDn.toString() );
                    }

                    if ( subentry.isSchemaSubentry() )
                    {
                        operational = entry.get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT );

                        if ( operational == null )
                        {
                            operational = new DefaultServerAttribute( atRegistry
                                .lookup( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ) );
                            entry.put( operational );
                        }

                        operational.add( subentryDn.toString() );
                    }

                    if ( subentry.isCollectiveSubentry() )
                    {
                        operational = entry.get( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT );

                        if ( operational == null )
                        {
                            operational = new DefaultServerAttribute( atRegistry
                                .lookup( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT ) );
                            entry.put( operational );
                        }

                        operational.add( subentryDn.toString() );
                    }

                    if ( subentry.isTriggerSubentry() )
                    {
                        operational = entry.get( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT );

                        if ( operational == null )
                        {
                            operational = new DefaultServerAttribute( atRegistry
                                .lookup( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT ) );
                            entry.put( operational );
                        }

                        operational.add( subentryDn.toString() );
                    }
                }
            }

            addContext.setEntry( entry );

            next.add( addContext );
        }
    }


    // -----------------------------------------------------------------------
    // Methods dealing subentry deletion
    // -----------------------------------------------------------------------

    public void delete( NextInterceptor next, DeleteOperationContext opContext ) throws NamingException
    {
        LdapDN name = opContext.getDn();
        ServerEntry entry = nexus.lookup( new LookupOperationContext( registries, name ) );
        EntryAttribute objectClasses = entry.get( objectClassType );

        if ( objectClasses.contains( SchemaConstants.SUBENTRY_OC ) )
        {
            SubtreeSpecification ss = subentryCache.removeSubentry( name.toNormName() ).getSubtreeSpecification();
            next.delete( opContext );

            /* ----------------------------------------------------------------
             * Find the baseDn for the subentry and use that to search the tree
             * for all entries included by the subtreeSpecification.  Then we
             * check the entry for subentry operational attribute that contain
             * the DN of the subentry.  These are the subentry operational
             * attributes we remove from the entry in a modify operation.
             * ----------------------------------------------------------------
             */
            LdapDN apName = ( LdapDN ) name.clone();
            apName.remove( name.size() - 1 );
            LdapDN baseDn = ( LdapDN ) apName.clone();
            baseDn.addAll( ss.getBase() );

            ExprNode filter = new PresenceNode( oidRegistry.getOid( SchemaConstants.OBJECT_CLASS_AT ) );
            SearchControls controls = new SearchControls();
            controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            controls.setReturningAttributes( new String[]
                { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );

            NamingEnumeration<ServerSearchResult> subentries = nexus.search( new SearchOperationContext( registries,
                baseDn, AliasDerefMode.NEVER_DEREF_ALIASES, filter, controls ) );

            while ( subentries.hasMore() )
            {
                ServerSearchResult result = subentries.next();
                LdapDN dn = new LdapDN( result.getDn() );
                dn.normalize( atRegistry.getNormalizerMapping() );
                ServerEntry candidate = result.getServerEntry();

                if ( evaluator.evaluate( ss, apName, dn, candidate ) )
                {
                    nexus.modify( new ModifyOperationContext( registries, dn, getOperationalModsForRemove( name,
                        candidate ) ) );
                }
            }
        }
        else
        {
            next.delete( opContext );
        }
    }


    // -----------------------------------------------------------------------
    // Methods dealing subentry name changes
    // -----------------------------------------------------------------------

    /**
     * Checks to see if an entry being renamed has a descendant that is an
     * administrative point.
     *
     * @param name the name of the entry which is used as the search base
     * @return true if name is an administrative point or one of its descendants
     * are, false otherwise
     * @throws NamingException if there are errors while searching the directory
     */
    private boolean hasAdministrativeDescendant( LdapDN name ) throws NamingException
    {
        ExprNode filter = new PresenceNode( "administrativeRole" );
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        NamingEnumeration<ServerSearchResult> aps = nexus.search( new SearchOperationContext( registries, name,
            AliasDerefMode.NEVER_DEREF_ALIASES, filter, controls ) );

        if ( aps.hasMore() )
        {
            aps.close();
            return true;
        }

        return false;
    }


    private List<Modification> getModsOnEntryRdnChange( Name oldName, Name newName, ServerEntry entry )
        throws NamingException
    {
        List<Modification> modList = new ArrayList<Modification>();

        /*
         * There are two different situations warranting action.  Firt if
         * an ss evalutating to true with the old name no longer evalutates
         * to true with the new name.  This would be caused by specific chop
         * exclusions that effect the new name but did not effect the old
         * name. In this case we must remove subentry operational attribute
         * values associated with the dn of that subentry.
         *
         * In the second case an ss selects the entry with the new name when
         * it did not previously with the old name.  Again this situation
         * would be caused by chop exclusions. In this case we must add subentry
         * operational attribute values with the dn of this subentry.
         */
        Iterator<String> subentries = subentryCache.nameIterator();

        while ( subentries.hasNext() )
        {
            String subentryDn = subentries.next();
            Name apDn = new LdapDN( subentryDn );
            apDn.remove( apDn.size() - 1 );
            SubtreeSpecification ss = subentryCache.getSubentry( subentryDn ).getSubtreeSpecification();
            boolean isOldNameSelected = evaluator.evaluate( ss, apDn, oldName, entry );
            boolean isNewNameSelected = evaluator.evaluate( ss, apDn, newName, entry );

            if ( isOldNameSelected == isNewNameSelected )
            {
                continue;
            }

            // need to remove references to the subentry
            if ( isOldNameSelected && !isNewNameSelected )
            {
                for ( String aSUBENTRY_OPATTRS : SUBENTRY_OPATTRS )
                {
                    ModificationOperation op = ModificationOperation.REPLACE_ATTRIBUTE;
                    EntryAttribute opAttr = entry.get( aSUBENTRY_OPATTRS );

                    if ( opAttr != null )
                    {
                        opAttr = ( ServerAttribute ) opAttr.clone();
                        opAttr.remove( subentryDn );

                        if ( opAttr.size() < 1 )
                        {
                            op = ModificationOperation.REMOVE_ATTRIBUTE;
                        }

                        modList.add( new ServerModification( op, opAttr ) );
                    }
                }
            }
            // need to add references to the subentry
            else if ( isNewNameSelected && !isOldNameSelected )
            {
                for ( String aSUBENTRY_OPATTRS : SUBENTRY_OPATTRS )
                {
                    ModificationOperation op = ModificationOperation.ADD_ATTRIBUTE;
                    ServerAttribute opAttr = new DefaultServerAttribute( aSUBENTRY_OPATTRS, atRegistry
                        .lookup( aSUBENTRY_OPATTRS ) );
                    opAttr.add( subentryDn );
                    modList.add( new ServerModification( op, opAttr ) );
                }
            }
        }

        return modList;
    }


    public void rename( NextInterceptor next, RenameOperationContext opContext ) throws NamingException
    {
        LdapDN name = opContext.getDn();

        ServerEntry entry = nexus.lookup( new LookupOperationContext( registries, name ) );

        EntryAttribute objectClasses = entry.get( objectClassType );

        if ( objectClasses.contains( SchemaConstants.SUBENTRY_OC ) )
        {
            Subentry subentry = subentryCache.getSubentry( name.toNormName() );
            SubtreeSpecification ss = subentry.getSubtreeSpecification();
            LdapDN apName = ( LdapDN ) name.clone();
            apName.remove( apName.size() - 1 );
            LdapDN baseDn = ( LdapDN ) apName.clone();
            baseDn.addAll( ss.getBase() );
            LdapDN newName = ( LdapDN ) name.clone();
            newName.remove( newName.size() - 1 );

            newName.add( opContext.getNewRdn() );

            String newNormName = newName.toNormName();
            subentryCache.setSubentry( newNormName, ss, subentry.getTypes() );
            next.rename( opContext );

            subentry = subentryCache.getSubentry( newNormName );
            ExprNode filter = new PresenceNode( oidRegistry.getOid( SchemaConstants.OBJECT_CLASS_AT ) );
            SearchControls controls = new SearchControls();
            controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            controls.setReturningAttributes( new String[]
                { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );
            NamingEnumeration<ServerSearchResult> subentries = nexus.search( new SearchOperationContext( registries,
                baseDn, AliasDerefMode.NEVER_DEREF_ALIASES, filter, controls ) );

            while ( subentries.hasMore() )
            {
                ServerSearchResult result = subentries.next();
                LdapDN dn = result.getDn();
                dn.normalize( atRegistry.getNormalizerMapping() );

                ServerEntry candidate = result.getServerEntry();

                if ( evaluator.evaluate( ss, apName, dn, candidate ) )
                {
                    nexus.modify( new ModifyOperationContext( registries, dn, getOperationalModsForReplace( name,
                        newName, subentry, candidate ) ) );
                }
            }
        }
        else
        {
            if ( hasAdministrativeDescendant( name ) )
            {
                String msg = "Will not allow rename operation on entries with administrative descendants.";
                LOG.warn( msg );
                throw new LdapSchemaViolationException( msg, ResultCodeEnum.NOT_ALLOWED_ON_RDN );
            }

            next.rename( opContext );

            // calculate the new DN now for use below to modify subentry operational
            // attributes contained within this regular entry with name changes
            LdapDN newName = ( LdapDN ) name.clone();
            newName.remove( newName.size() - 1 );
            newName.add( opContext.getNewRdn() );
            newName.normalize( atRegistry.getNormalizerMapping() );
            List<Modification> mods = getModsOnEntryRdnChange( name, newName, entry );

            if ( mods.size() > 0 )
            {
                nexus.modify( new ModifyOperationContext( registries, newName, mods ) );
            }
        }
    }


    public void moveAndRename( NextInterceptor next, MoveAndRenameOperationContext opContext ) throws NamingException
    {
        LdapDN oriChildName = opContext.getDn();
        LdapDN parent = opContext.getParent();

        ServerEntry entry = nexus.lookup( new LookupOperationContext( registries, oriChildName ) );

        EntryAttribute objectClasses = entry.get( objectClassType );

        if ( objectClasses.contains( SchemaConstants.SUBENTRY_OC ) )
        {
            Subentry subentry = subentryCache.getSubentry( oriChildName.toNormName() );
            SubtreeSpecification ss = subentry.getSubtreeSpecification();
            LdapDN apName = ( LdapDN ) oriChildName.clone();
            apName.remove( apName.size() - 1 );
            LdapDN baseDn = ( LdapDN ) apName.clone();
            baseDn.addAll( ss.getBase() );
            LdapDN newName = ( LdapDN ) parent.clone();
            newName.remove( newName.size() - 1 );

            newName.add( opContext.getNewRdn() );

            String newNormName = newName.toNormName();
            subentryCache.setSubentry( newNormName, ss, subentry.getTypes() );
            next.moveAndRename( opContext );

            subentry = subentryCache.getSubentry( newNormName );

            ExprNode filter = new PresenceNode( oidRegistry.getOid( SchemaConstants.OBJECT_CLASS_AT ) );
            SearchControls controls = new SearchControls();
            controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            controls.setReturningAttributes( new String[]
                { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );
            NamingEnumeration<ServerSearchResult> subentries = nexus.search( new SearchOperationContext( registries,
                baseDn, AliasDerefMode.NEVER_DEREF_ALIASES, filter, controls ) );

            while ( subentries.hasMore() )
            {
                ServerSearchResult result = subentries.next();
                LdapDN dn = result.getDn();
                dn.normalize( atRegistry.getNormalizerMapping() );
                ServerEntry candidate = result.getServerEntry();

                if ( evaluator.evaluate( ss, apName, dn, candidate ) )
                {
                    nexus.modify( new ModifyOperationContext( registries, dn, getOperationalModsForReplace(
                        oriChildName, newName, subentry, candidate ) ) );
                }
            }
        }
        else
        {
            if ( hasAdministrativeDescendant( oriChildName ) )
            {
                String msg = "Will not allow rename operation on entries with administrative descendants.";
                LOG.warn( msg );
                throw new LdapSchemaViolationException( msg, ResultCodeEnum.NOT_ALLOWED_ON_RDN );
            }

            next.moveAndRename( opContext );

            // calculate the new DN now for use below to modify subentry operational
            // attributes contained within this regular entry with name changes
            LdapDN newName = ( LdapDN ) parent.clone();
            newName.add( opContext.getNewRdn() );
            newName.normalize( atRegistry.getNormalizerMapping() );
            List<Modification> mods = getModsOnEntryRdnChange( oriChildName, newName, entry );

            if ( mods.size() > 0 )
            {
                nexus.modify( new ModifyOperationContext( registries, newName, mods ) );
            }
        }
    }


    public void move( NextInterceptor next, MoveOperationContext opContext ) throws NamingException
    {
        LdapDN oriChildName = opContext.getDn();
        LdapDN newParentName = opContext.getParent();

        ServerEntry entry = nexus.lookup( new LookupOperationContext( registries, oriChildName ) );

        EntryAttribute objectClasses = entry.get( SchemaConstants.OBJECT_CLASS_AT );

        if ( objectClasses.contains( SchemaConstants.SUBENTRY_OC ) )
        {
            Subentry subentry = subentryCache.getSubentry( oriChildName.toString() );
            SubtreeSpecification ss = subentry.getSubtreeSpecification();
            LdapDN apName = ( LdapDN ) oriChildName.clone();
            apName.remove( apName.size() - 1 );
            LdapDN baseDn = ( LdapDN ) apName.clone();
            baseDn.addAll( ss.getBase() );
            LdapDN newName = ( LdapDN ) newParentName.clone();
            newName.remove( newName.size() - 1 );
            newName.add( newParentName.get( newParentName.size() - 1 ) );

            String newNormName = newName.toNormName();
            subentryCache.setSubentry( newNormName, ss, subentry.getTypes() );
            next.move( opContext );

            subentry = subentryCache.getSubentry( newNormName );

            ExprNode filter = new PresenceNode( SchemaConstants.OBJECT_CLASS_AT );
            SearchControls controls = new SearchControls();
            controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            controls.setReturningAttributes( new String[]
                { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );
            NamingEnumeration<ServerSearchResult> subentries = nexus.search( new SearchOperationContext( registries,
                baseDn, AliasDerefMode.NEVER_DEREF_ALIASES, filter, controls ) );

            while ( subentries.hasMore() )
            {
                ServerSearchResult result = subentries.next();
                LdapDN dn = result.getDn();
                dn.normalize( atRegistry.getNormalizerMapping() );
                ServerEntry candidate = result.getServerEntry();

                if ( evaluator.evaluate( ss, apName, dn, candidate ) )
                {
                    nexus.modify( new ModifyOperationContext( registries, dn, getOperationalModsForReplace(
                        oriChildName, newName, subentry, candidate ) ) );
                }
            }
        }
        else
        {
            if ( hasAdministrativeDescendant( oriChildName ) )
            {
                String msg = "Will not allow rename operation on entries with administrative descendants.";
                LOG.warn( msg );
                throw new LdapSchemaViolationException( msg, ResultCodeEnum.NOT_ALLOWED_ON_RDN );
            }

            next.move( opContext );

            // calculate the new DN now for use below to modify subentry operational
            // attributes contained within this regular entry with name changes
            LdapDN newName = ( LdapDN ) newParentName.clone();
            newName.add( oriChildName.get( oriChildName.size() - 1 ) );
            List<Modification> mods = getModsOnEntryRdnChange( oriChildName, newName, entry );

            if ( mods.size() > 0 )
            {
                nexus.modify( new ModifyOperationContext( registries, newName, mods ) );
            }
        }
    }


    // -----------------------------------------------------------------------
    // Methods dealing subentry modification
    // -----------------------------------------------------------------------

    private int getSubentryTypes( ServerEntry entry, List<Modification> mods ) throws NamingException
    {
        ServerAttribute ocFinalState = ( ServerAttribute ) entry.get( SchemaConstants.OBJECT_CLASS_AT ).clone();

        for ( Modification mod : mods )
        {
            if ( mod.getAttribute().getId().equalsIgnoreCase( SchemaConstants.OBJECT_CLASS_AT ) )
            {
                switch ( mod.getOperation() )
                {
                    case ADD_ATTRIBUTE:
                        for ( Value<?> value : ( ServerAttribute ) mod.getAttribute() )
                        {
                            ocFinalState.add( ( String ) value.get() );
                        }

                        break;

                    case REMOVE_ATTRIBUTE:
                        for ( Value<?> value : ( ServerAttribute ) mod.getAttribute() )
                        {
                            ocFinalState.remove( ( String ) value.get() );
                        }

                        break;

                    case REPLACE_ATTRIBUTE:
                        ocFinalState = ( ServerAttribute ) mod.getAttribute();
                        break;
                }
            }
        }

        ServerEntry attrs = new DefaultServerEntry( registries, LdapDN.EMPTY_LDAPDN );
        attrs.put( ocFinalState );
        return getSubentryTypes( attrs );
    }


    public void modify( NextInterceptor next, ModifyOperationContext opContext ) throws NamingException
    {
        LdapDN name = opContext.getDn();
        List<Modification> mods = opContext.getModItems();

        ServerEntry entry = nexus.lookup( new LookupOperationContext( registries, name ) );

        ServerEntry oldEntry = ( ServerEntry ) entry.clone();
        EntryAttribute objectClasses = entry.get( objectClassType );
        boolean isSubtreeSpecificationModification = false;
        Modification subtreeMod = null;

        for ( Modification mod : mods )
        {
            if ( SchemaConstants.SUBTREE_SPECIFICATION_AT.equalsIgnoreCase( mod.getAttribute().getId() ) )
            {
                isSubtreeSpecificationModification = true;
                subtreeMod = mod;
            }
        }

        if ( objectClasses.contains( SchemaConstants.SUBENTRY_OC ) && isSubtreeSpecificationModification )
        {
            SubtreeSpecification ssOld = subentryCache.removeSubentry( name.toString() ).getSubtreeSpecification();
            SubtreeSpecification ssNew;

            try
            {
                ssNew = ssParser.parse( ( ( ServerAttribute ) subtreeMod.getAttribute() ).getString() );
            }
            catch ( Exception e )
            {
                String msg = "failed to parse the new subtreeSpecification";
                LOG.error( msg, e );
                throw new LdapInvalidAttributeValueException( msg, ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX );
            }

            subentryCache.setSubentry( name.toNormName(), ssNew, getSubentryTypes( entry, mods ) );
            next.modify( opContext );

            // search for all entries selected by the old SS and remove references to subentry
            LdapDN apName = ( LdapDN ) name.clone();
            apName.remove( apName.size() - 1 );
            LdapDN oldBaseDn = ( LdapDN ) apName.clone();
            oldBaseDn.addAll( ssOld.getBase() );
            ExprNode filter = new PresenceNode( oidRegistry.getOid( SchemaConstants.OBJECT_CLASS_AT ) );
            SearchControls controls = new SearchControls();
            controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            controls.setReturningAttributes( new String[]
                { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );
            NamingEnumeration<ServerSearchResult> subentries = nexus.search( new SearchOperationContext( registries,
                oldBaseDn, AliasDerefMode.NEVER_DEREF_ALIASES, filter, controls ) );

            while ( subentries.hasMore() )
            {
                ServerSearchResult result = subentries.next();
                LdapDN dn = result.getDn();
                dn.normalize( atRegistry.getNormalizerMapping() );
                ServerEntry candidate = result.getServerEntry();

                if ( evaluator.evaluate( ssOld, apName, dn, candidate ) )
                {
                    nexus.modify( new ModifyOperationContext( registries, dn, getOperationalModsForRemove( name,
                        candidate ) ) );
                }
            }

            // search for all selected entries by the new SS and add references to subentry
            Subentry subentry = subentryCache.getSubentry( name.toNormName() );
            ServerEntry operational = getSubentryOperatationalAttributes( name, subentry );
            LdapDN newBaseDn = ( LdapDN ) apName.clone();
            newBaseDn.addAll( ssNew.getBase() );
            subentries = nexus.search( new SearchOperationContext( registries, newBaseDn,
                AliasDerefMode.NEVER_DEREF_ALIASES, filter, controls ) );
            while ( subentries.hasMore() )
            {
                ServerSearchResult result = subentries.next();
                LdapDN dn = result.getDn();
                dn.normalize( atRegistry.getNormalizerMapping() );
                ServerEntry candidate = result.getServerEntry();

                if ( evaluator.evaluate( ssNew, apName, dn, candidate ) )
                {
                    nexus.modify( new ModifyOperationContext( registries, dn, getOperationalModsForAdd( candidate,
                        operational ) ) );
                }
            }
        }
        else
        {
            next.modify( opContext );

            if ( !objectClasses.contains( SchemaConstants.SUBENTRY_OC ) )
            {
                ServerEntry newEntry = nexus.lookup( new LookupOperationContext( registries, name ) );

                List<Modification> subentriesOpAttrMods = getModsOnEntryModification( name, oldEntry, newEntry );

                if ( subentriesOpAttrMods.size() > 0 )
                {
                    nexus.modify( new ModifyOperationContext( registries, name, subentriesOpAttrMods ) );
                }
            }
        }
    }


    // -----------------------------------------------------------------------
    // Utility Methods
    // -----------------------------------------------------------------------

    private List<Modification> getOperationalModsForReplace( Name oldName, Name newName, Subentry subentry,
        ServerEntry entry ) throws NamingException
    {
        List<Modification> modList = new ArrayList<Modification>();

        ServerAttribute operational;

        if ( subentry.isAccessControlSubentry() )
        {
            operational = ( ServerAttribute ) entry.get( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT ).clone();

            if ( operational == null )
            {
                operational = new DefaultServerAttribute( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT, atRegistry
                    .lookup( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT ) );
                operational.add( newName.toString() );
            }
            else
            {
                operational.remove( oldName.toString() );
                operational.add( newName.toString() );
            }

            modList.add( new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, operational ) );
        }

        if ( subentry.isSchemaSubentry() )
        {
            operational = ( ServerAttribute ) entry.get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ).clone();

            if ( operational == null )
            {
                operational = new DefaultServerAttribute( SchemaConstants.SUBSCHEMA_SUBENTRY_AT, atRegistry
                    .lookup( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ) );
                operational.add( newName.toString() );
            }
            else
            {
                operational.remove( oldName.toString() );
                operational.add( newName.toString() );
            }

            modList.add( new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, operational ) );
        }

        if ( subentry.isCollectiveSubentry() )
        {
            operational = ( ServerAttribute ) entry.get( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT ).clone();

            if ( operational == null )
            {
                operational = new DefaultServerAttribute( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT,
                    atRegistry.lookup( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT ) );
                operational.add( newName.toString() );
            }
            else
            {
                operational.remove( oldName.toString() );
                operational.add( newName.toString() );
            }

            modList.add( new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, operational ) );
        }

        if ( subentry.isTriggerSubentry() )
        {
            operational = ( ServerAttribute ) entry.get( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT ).clone();

            if ( operational == null )
            {
                operational = new DefaultServerAttribute( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT, atRegistry
                    .lookup( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT ) );
                operational.add( newName.toString() );
            }
            else
            {
                operational.remove( oldName.toString() );
                operational.add( newName.toString() );
            }

            modList.add( new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, operational ) );
        }

        return modList;
    }


    /**
     * Gets the subschema operational attributes to be added to or removed from
     * an entry selected by a subentry's subtreeSpecification.
     *
     * @param name the normalized distinguished name of the subentry (the value of op attrs)
     * @param subentry the subentry to get attributes from
     * @return the set of attributes to be added or removed from entries
     */
    private ServerEntry getSubentryOperatationalAttributes( LdapDN name, Subentry subentry ) throws NamingException
    {
        ServerEntry operational = new DefaultServerEntry( registries, name );

        if ( subentry.isAccessControlSubentry() )
        {
            if ( operational.get( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT ) == null )
            {
                operational.put( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT, name.toString() );
            }
            else
            {
                operational.get( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT ).add( name.toString() );
            }
        }
        if ( subentry.isSchemaSubentry() )
        {
            if ( operational.get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ) == null )
            {
                operational.put( SchemaConstants.SUBSCHEMA_SUBENTRY_AT, name.toString() );
            }
            else
            {
                operational.get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ).add( name.toString() );
            }
        }
        if ( subentry.isCollectiveSubentry() )
        {
            if ( operational.get( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT ) == null )
            {
                operational.put( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT, name.toString() );
            }
            else
            {
                operational.get( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT ).add( name.toString() );
            }
        }
        if ( subentry.isTriggerSubentry() )
        {
            if ( operational.get( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT ) == null )
            {
                operational.put( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT, name.toString() );
            }
            else
            {
                operational.get( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT ).add( name.toString() );
            }
        }

        return operational;
    }


    /**
     * Calculates the subentry operational attributes to remove from a candidate
     * entry selected by a subtreeSpecification.  When we remove a subentry we
     * must remove the operational attributes in the entries that were once selected
     * by the subtree specification of that subentry.  To do so we must perform
     * a modify operation with the set of modifications to perform.  This method
     * calculates those modifications.
     *
     * @param subentryDn the distinguished name of the subentry
     * @param candidate the candidate entry to removed from the
     * @return the set of modifications required to remove an entry's reference to
     * a subentry
     */
    private List<Modification> getOperationalModsForRemove( LdapDN subentryDn, ServerEntry candidate )
        throws NamingException
    {
        List<Modification> modList = new ArrayList<Modification>();
        String dn = subentryDn.toNormName();

        for ( String opAttrId : SUBENTRY_OPATTRS )
        {
            EntryAttribute opAttr = candidate.get( opAttrId );

            if ( ( opAttr != null ) && opAttr.contains( dn ) )
            {
                AttributeType attributeType = atRegistry.lookup( opAttrId );
                ServerAttribute attr = new DefaultServerAttribute( opAttrId, attributeType, dn );
                modList.add( new ServerModification( ModificationOperation.REMOVE_ATTRIBUTE, attr ) );
            }
        }

        return modList;
    }


    /**
     * Calculates the subentry operational attributes to add or replace from
     * a candidate entry selected by a subtree specification.  When a subentry
     * is added or it's specification is modified some entries must have new
     * operational attributes added to it to point back to the associated
     * subentry.  To do so a modify operation must be performed on entries
     * selected by the subtree specification.  This method calculates the
     * modify operation to be performed on the entry.
     *
     * @param entry the entry being modified
     * @param operational the set of operational attributes supported by the AP
     * of the subentry
     * @return the set of modifications needed to update the entry
     * @throws NamingException if there are probelms accessing modification items
     */
    public List<Modification> getOperationalModsForAdd( ServerEntry entry, ServerEntry operational )
        throws NamingException
    {
        List<Modification> modList = new ArrayList<Modification>();

        for ( AttributeType attributeType : operational.getAttributeTypes() )
        {
            ModificationOperation op = ModificationOperation.REPLACE_ATTRIBUTE;
            EntryAttribute result = new DefaultServerAttribute( attributeType );
            EntryAttribute opAttrAdditions = operational.get( attributeType );
            EntryAttribute opAttrInEntry = entry.get( attributeType );

            for ( Value<?> value : opAttrAdditions )
            {
                result.add( value );
            }

            if ( opAttrInEntry != null && opAttrInEntry.size() > 0 )
            {
                for ( Value<?> value : opAttrInEntry )
                {
                    result.add( value );
                }
            }
            else
            {
                op = ModificationOperation.ADD_ATTRIBUTE;
            }

            modList.add( new ServerModification( op, result ) );
        }

        return modList;
    }

    /**
     * SearchResultFilter used to filter out subentries based on objectClass values.
     */
    public class HideSubentriesFilter implements SearchResultFilter
    {
        public boolean accept( Invocation invocation, ServerSearchResult result, SearchControls controls )
            throws NamingException
        {
            String dn = result.getDn().getNormName();

            // see if we can get a match without normalization
            if ( subentryCache.hasSubentry( dn ) )
            {
                return false;
            }

            // see if we can use objectclass if present
            EntryAttribute objectClasses = result.getServerEntry().get( SchemaConstants.OBJECT_CLASS_AT );

            if ( objectClasses != null )
            {
                return !objectClasses.contains( SchemaConstants.SUBENTRY_OC );
            }

            if ( !result.isRelative() )
            {
                LdapDN ndn = new LdapDN( dn );
                ndn.normalize( atRegistry.getNormalizerMapping() );
                String normalizedDn = ndn.toString();
                return !subentryCache.hasSubentry( normalizedDn );
            }

            LdapDN name = new LdapDN( invocation.getCaller().getNameInNamespace() );
            name.normalize( atRegistry.getNormalizerMapping() );

            LdapDN rest = result.getDn();
            rest.normalize( atRegistry.getNormalizerMapping() );
            name.addAll( rest );
            return !subentryCache.hasSubentry( name.toString() );
        }
    }

    /**
     * SearchResultFilter used to filter out normal entries but shows subentries based on 
     * objectClass values.
     */
    public class HideEntriesFilter implements SearchResultFilter
    {
        public boolean accept( Invocation invocation, ServerSearchResult result, SearchControls controls )
            throws NamingException
        {
            String dn = result.getDn().getNormName();

            // see if we can get a match without normalization
            if ( subentryCache.hasSubentry( dn ) )
            {
                return true;
            }

            // see if we can use objectclass if present
            EntryAttribute objectClasses = result.getServerEntry().get( SchemaConstants.OBJECT_CLASS_AT );

            if ( objectClasses != null )
            {
                return objectClasses.contains( SchemaConstants.SUBENTRY_OC );
            }

            if ( !result.isRelative() )
            {
                LdapDN ndn = new LdapDN( dn );
                ndn.normalize( atRegistry.getNormalizerMapping() );
                return subentryCache.hasSubentry( ndn.toNormName() );
            }

            LdapDN name = new LdapDN( invocation.getCaller().getNameInNamespace() );
            name.normalize( atRegistry.getNormalizerMapping() );

            LdapDN rest = result.getDn();
            rest.normalize( atRegistry.getNormalizerMapping() );
            name.addAll( rest );
            return subentryCache.hasSubentry( name.toNormName() );
        }
    }


    private List<Modification> getModsOnEntryModification( LdapDN name, ServerEntry oldEntry, ServerEntry newEntry )
        throws NamingException
    {
        List<Modification> modList = new ArrayList<Modification>();

        Iterator<String> subentries = subentryCache.nameIterator();

        while ( subentries.hasNext() )
        {
            String subentryDn = subentries.next();
            Name apDn = new LdapDN( subentryDn );
            apDn.remove( apDn.size() - 1 );
            SubtreeSpecification ss = subentryCache.getSubentry( subentryDn ).getSubtreeSpecification();
            boolean isOldEntrySelected = evaluator.evaluate( ss, apDn, name, oldEntry );
            boolean isNewEntrySelected = evaluator.evaluate( ss, apDn, name, newEntry );

            if ( isOldEntrySelected == isNewEntrySelected )
            {
                continue;
            }

            // need to remove references to the subentry
            if ( isOldEntrySelected && !isNewEntrySelected )
            {
                for ( String aSUBENTRY_OPATTRS : SUBENTRY_OPATTRS )
                {
                    ModificationOperation op = ModificationOperation.REPLACE_ATTRIBUTE;
                    EntryAttribute opAttr = oldEntry.get( aSUBENTRY_OPATTRS );

                    if ( opAttr != null )
                    {
                        opAttr = ( ServerAttribute ) opAttr.clone();
                        opAttr.remove( subentryDn );

                        if ( opAttr.size() < 1 )
                        {
                            op = ModificationOperation.REMOVE_ATTRIBUTE;
                        }

                        modList.add( new ServerModification( op, opAttr ) );
                    }
                }
            }
            // need to add references to the subentry
            else if ( isNewEntrySelected && !isOldEntrySelected )
            {
                for ( String attribute : SUBENTRY_OPATTRS )
                {
                    ModificationOperation op = ModificationOperation.ADD_ATTRIBUTE;
                    AttributeType type = atRegistry.lookup( attribute );
                    ServerAttribute opAttr = new DefaultServerAttribute( attribute, type );
                    opAttr.add( subentryDn );
                    modList.add( new ServerModification( op, opAttr ) );
                }
            }
        }

        return modList;
    }

}