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
package org.apache.directory.server.core.schema;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.entry.DefaultServerAttribute;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.entry.ServerModification;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.shared.ldap.schema.registries.Schema;
import org.apache.directory.shared.ldap.constants.MetaSchemaConstants;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.entry.client.ClientStringValue;
import org.apache.directory.shared.ldap.filter.AndNode;
import org.apache.directory.shared.ldap.filter.BranchNode;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.OrNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.filter.SimpleNode;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.AttributeTypeOptions;
import org.apache.directory.shared.ldap.schema.MatchingRule;
import org.apache.directory.shared.ldap.schema.ObjectClass;
import org.apache.directory.shared.ldap.schema.registries.AttributeTypeRegistry;
import org.apache.directory.shared.ldap.schema.registries.Registries;
import org.apache.directory.shared.ldap.schema.syntaxCheckers.NumericOidSyntaxChecker;
import org.apache.directory.shared.ldap.util.DateUtils;
import org.apache.directory.shared.schema.loader.ldif.SchemaEntityFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingException;
import javax.naming.directory.SearchControls;


/**
 * A specialized data access object for managing schema objects in the
 * schema partition.  
 * 
 * WARNING:
 * This dao operates directly on a partition.  Hence no interceptors are available
 * to perform the various expected services of respective interceptors.  Take care
 * to normalize all filters and distinguished names.
 * 
 * A single write operation exists for enabling schemas needed for operating indices
 * in partitions and enabling schemas that are dependencies of other schemas that 
 * are enabled.  In both these limited cases there is no need to worry about issues
 * with a lack of replication propagation because these same updates will take place
 * on replicas when the original operation is propagated or when replicas start up.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class SchemaPartitionDaoImpl implements SchemaPartitionDao
{
    /** static class logger */
    private final Logger LOG = LoggerFactory.getLogger( getClass() );
    private static final NumericOidSyntaxChecker NUMERIC_OID_CHECKER = new NumericOidSyntaxChecker();
    private static final String[] SCHEMA_ATTRIBUTES = new String[]
        { SchemaConstants.CREATORS_NAME_AT_OID, "m-dependencies", SchemaConstants.OBJECT_CLASS_AT_OID, 
          SchemaConstants.CN_AT_OID, "m-disabled" };

    private final Partition partition;
    private final SchemaEntityFactory factory;
    private final AttributeTypeRegistry atRegistry;

    private final String M_NAME_OID;
    private final String CN_OID;
    private final String M_OID_OID;
    private final String OBJECTCLASS_OID;
    private final String M_SYNTAX_OID;
    private final String M_ORDERING_OID;
    private final String M_SUBSTRING_OID;
    private final String M_EQUALITY_OID;
    private final String M_SUP_ATTRIBUTE_TYPE_OID;
    private final String M_MUST_OID;
    private final String M_MAY_OID;
    private final String M_AUX_OID;
    private final String M_OC_OID;
    private final String M_SUP_OBJECT_CLASS_OID;
    private final String M_DEPENDENCIES_OID;

    private final Set<AttributeTypeOptions> schemaAttributesToReturn = new HashSet<AttributeTypeOptions>();
    private final AttributeType disabledAttributeType;


    /**
     * Creates a schema dao object backing information within a schema partition.
     * 
     * @param partition the schema partition
     * @param registries the bootstrap registries that were used to start up the schema partition
     * @throws NamingException if there are problems initializing this schema partion dao
     */
    public SchemaPartitionDaoImpl( Partition partition, Registries registries ) throws Exception
    {
        this.partition = partition;
        this.factory = new SchemaEntityFactory();
        this.atRegistry = registries.getAttributeTypeRegistry();

        this.M_NAME_OID = atRegistry.getOidByName( MetaSchemaConstants.M_NAME_AT );
        this.CN_OID = atRegistry.getOidByName( SchemaConstants.CN_AT );
        this.disabledAttributeType = atRegistry.lookup( MetaSchemaConstants.M_DISABLED_AT );
        this.M_OID_OID = atRegistry.getOidByName( MetaSchemaConstants.M_OID_AT );
        this.OBJECTCLASS_OID = atRegistry.getOidByName( SchemaConstants.OBJECT_CLASS_AT );
        this.M_SYNTAX_OID = atRegistry.getOidByName( MetaSchemaConstants.M_SYNTAX_AT );
        this.M_ORDERING_OID = atRegistry.getOidByName( MetaSchemaConstants.M_ORDERING_AT );
        this.M_EQUALITY_OID = atRegistry.getOidByName( MetaSchemaConstants.M_EQUALITY_AT );
        this.M_SUBSTRING_OID = atRegistry.getOidByName( MetaSchemaConstants.M_SUBSTR_AT );
        this.M_SUP_ATTRIBUTE_TYPE_OID = atRegistry.getOidByName( MetaSchemaConstants.M_SUP_ATTRIBUTE_TYPE_AT );
        this.M_MUST_OID = atRegistry.getOidByName( MetaSchemaConstants.M_MUST_AT );
        this.M_MAY_OID = atRegistry.getOidByName( MetaSchemaConstants.M_MAY_AT );
        this.M_AUX_OID = atRegistry.getOidByName( MetaSchemaConstants.M_AUX_AT );
        this.M_OC_OID = atRegistry.getOidByName( MetaSchemaConstants.M_OC_AT );
        this.M_SUP_OBJECT_CLASS_OID = atRegistry.getOidByName( MetaSchemaConstants.M_SUP_OBJECT_CLASS_AT );
        this.M_DEPENDENCIES_OID = atRegistry.getOidByName( MetaSchemaConstants.M_DEPENDENCIES_AT );
        
        for ( String attrId : SCHEMA_ATTRIBUTES )
        {
            AttributeTypeOptions ato = new AttributeTypeOptions( atRegistry.lookup( attrId ) );
            schemaAttributesToReturn.add( ato );
        }
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#getSchemas()
     */
    public Map<String, Schema> getSchemas() throws Exception
    {
        Map<String, Schema> schemas = new HashMap<String, Schema>();
        EntryFilteringCursor list = listSchemas();

        while ( list.next() )
        {
            ServerEntry sr = list.get();
            Schema schema = factory.getSchema( sr );
            schemas.put( schema.getSchemaName(), schema );
        }

        return schemas;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#getSchemaNames()
     */
    public Set<String> getSchemaNames() throws Exception
    {
        Set<String> schemaNames = new HashSet<String>();
        EntryFilteringCursor list = listSchemas();

        while ( list.next() )
        {
            ServerEntry sr = list.get();
            schemaNames.add( sr.get( SchemaConstants.CN_AT ).getString() );
        }

        return schemaNames;
    }


    private EntryFilteringCursor listSchemas() throws Exception
    {
        LdapDN base = new LdapDN( ServerDNConstants.OU_SCHEMA_DN );
        base.normalize( atRegistry.getNormalizerMapping() );
        ExprNode filter = new EqualityNode<String>( atRegistry.getOidByName( SchemaConstants.OBJECT_CLASS_AT ),
            new ClientStringValue( MetaSchemaConstants.META_SCHEMA_OC ) );

        SearchOperationContext searchContext = new SearchOperationContext( null );
        searchContext.setDn( base );
        searchContext.setScope( SearchScope.ONELEVEL );
        searchContext.setReturningAttributes( schemaAttributesToReturn );
        searchContext.setFilter( filter );
        return partition.search( searchContext );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#getSchema(java.lang.String)
     */
    public Schema getSchema( String schemaName ) throws Exception
    {
        LdapDN dn = new LdapDN( "cn=" + schemaName + ",ou=schema" );
        dn.normalize( atRegistry.getNormalizerMapping() );
        return factory.getSchema( partition.lookup( new LookupOperationContext( null, dn ) ) );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#hasMatchingRule(java.lang.String)
     */
    public boolean hasMatchingRule( String oid ) throws Exception
    {
        BranchNode filter = new AndNode();
        filter.addNode( new EqualityNode<String>( OBJECTCLASS_OID, new ClientStringValue(
            MetaSchemaConstants.META_MATCHING_RULE_OC ) ) );

        if ( NUMERIC_OID_CHECKER.isValidSyntax( oid ) )
        {
            filter.addNode( new EqualityNode<String>( M_OID_OID, new ClientStringValue( oid ) ) );
        }
        else
        {
            filter.addNode( new EqualityNode<String>( M_NAME_OID, new ClientStringValue( oid.toLowerCase() ) ) );
        }

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        EntryFilteringCursor cursor = null;

        try
        {
            cursor = partition.search( new SearchOperationContext( null, partition.getSuffixDn(),
                AliasDerefMode.DEREF_ALWAYS, filter, searchControls ) );

            if ( !cursor.next() )
            {
                return false;
            }

            if ( cursor.next() )
            {
                throw new NamingException( "Got more than one matchingRule for oid of " + oid );
            }

            return true;
        }
        finally
        {
            if ( cursor != null )
            {
                cursor.close();
            }
        }
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#hasAttributeType(java.lang.String)
     */
    public boolean hasAttributeType( String oid ) throws Exception
    {
        BranchNode filter = new AndNode();
        filter.addNode( new EqualityNode<String>( OBJECTCLASS_OID, new ClientStringValue(
            MetaSchemaConstants.META_ATTRIBUTE_TYPE_OC ) ) );

        if ( NUMERIC_OID_CHECKER.isValidSyntax( oid ) )
        {
            filter.addNode( new EqualityNode<String>( M_OID_OID, new ClientStringValue( oid ) ) );
        }
        else
        {
            filter.addNode( new EqualityNode<String>( M_NAME_OID, new ClientStringValue( oid.toLowerCase() ) ) );
        }

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        EntryFilteringCursor cursor = null;

        try
        {
            cursor = partition.search( new SearchOperationContext( null, partition.getSuffixDn(),
                AliasDerefMode.DEREF_ALWAYS, filter, searchControls ) );

            if ( !cursor.next() )
            {
                return false;
            }

            if ( cursor.next() )
            {
                throw new NamingException( "Got more than one attributeType for oid of " + oid );
            }

            return true;
        }
        finally
        {
            if ( cursor != null )
            {
                cursor.close();
            }
        }
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#hasObjectClass(java.lang.String)
     */
    public boolean hasObjectClass( String oid ) throws Exception
    {
        BranchNode filter = new AndNode();
        filter.addNode( new EqualityNode<String>( OBJECTCLASS_OID, 
            new ClientStringValue( MetaSchemaConstants.META_OBJECT_CLASS_OC ) ) );

        if ( NUMERIC_OID_CHECKER.isValidSyntax( oid ) )
        {
            filter.addNode( new EqualityNode<String>( M_OID_OID, new ClientStringValue( oid ) ) );
        }
        else
        {
            filter.addNode( new EqualityNode<String>( M_NAME_OID, new ClientStringValue( oid.toLowerCase() ) ) );
        }

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        EntryFilteringCursor cursor = null;

        try
        {
            cursor = partition.search( new SearchOperationContext( null, partition.getSuffixDn(),
                AliasDerefMode.DEREF_ALWAYS, filter, searchControls ) );

            if ( !cursor.next() )
            {
                return false;
            }

            if ( cursor.next() )
            {
                throw new NamingException( "Got more than one attributeType for oid of " + oid );
            }

            return true;
        }
        finally
        {
            if ( cursor != null )
            {
                cursor.close();
            }
        }
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#hasSyntax(java.lang.String)
     */
    public boolean hasSyntax( String oid ) throws Exception
    {
        BranchNode filter = new AndNode();
        filter.addNode( new EqualityNode<String>( OBJECTCLASS_OID, 
                new ClientStringValue( MetaSchemaConstants.META_SYNTAX_OC ) ) );

        if ( NUMERIC_OID_CHECKER.isValidSyntax( oid ) )
        {
            filter.addNode( new EqualityNode<String>( M_OID_OID, new ClientStringValue( oid ) ) );
        }
        else
        {
            filter.addNode( new EqualityNode<String>( M_NAME_OID, new ClientStringValue( oid.toLowerCase() ) ) );
        }

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        EntryFilteringCursor cursor = null;

        try
        {
            cursor = partition.search( new SearchOperationContext( null, partition.getSuffixDn(),
                AliasDerefMode.DEREF_ALWAYS, filter, searchControls ) );

            if ( !cursor.next() )
            {
                return false;
            }

            if ( cursor.next() )
            {
                throw new NamingException( "Got more than one syntax for oid of " + oid );
            }

            return true;
        }
        finally
        {
            if ( cursor != null )
            {
                cursor.close();
            }
        }
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#hasSyntaxChecker(java.lang.String)
     */
    public boolean hasSyntaxChecker( String oid ) throws Exception
    {
        BranchNode filter = new AndNode();
        filter.addNode( new EqualityNode<String>( OBJECTCLASS_OID, 
            new ClientStringValue( MetaSchemaConstants.META_SYNTAX_CHECKER_OC ) ) );

        if ( NUMERIC_OID_CHECKER.isValidSyntax( oid ) )
        {
            filter.addNode( new EqualityNode<String>( M_OID_OID, new ClientStringValue( oid ) ) );
        }
        else
        {
            filter.addNode( new EqualityNode<String>( M_NAME_OID, new ClientStringValue( oid.toLowerCase() ) ) );
        }

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        EntryFilteringCursor cursor = null;

        try
        {
            cursor = partition.search( new SearchOperationContext( null, partition.getSuffixDn(),
                AliasDerefMode.DEREF_ALWAYS, filter, searchControls ) );

            if ( !cursor.next() )
            {
                return false;
            }

            if ( cursor.next() )
            {
                throw new NamingException( "Got more than one syntaxChecker for oid of " + oid );
            }

            return true;
        }
        finally
        {
            if ( cursor != null )
            {
                cursor.close();
            }
        }
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#findSchema(java.lang.String)
     */
    public String findSchema( String entityName ) throws Exception
    {
        LdapDN dn = findDn( entityName );
        if ( dn == null )
        {
            return null;
        }

        Rdn rdn = dn.getRdn( 1 );
        if ( !rdn.getNormType().equalsIgnoreCase( CN_OID ) )
        {
            throw new NamingException( "Attribute of second rdn in dn '" + dn.toNormName()
                + "' expected to be CN oid of " + CN_OID + " but was " + rdn.getNormType() );
        }

        return ( String ) rdn.getValue();
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#findDn(java.lang.String)
     */
    public LdapDN findDn( String entityName ) throws Exception
    {
        ServerEntry sr = find( entityName );
        LdapDN dn = sr.getDn();
        dn.normalize( atRegistry.getNormalizerMapping() );
        return dn;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#find(java.lang.String)
     */
    public ServerEntry find( String entityName ) throws Exception
    {
        BranchNode filter = new OrNode();
        SimpleNode<String> nameAVA = new EqualityNode<String>( M_NAME_OID, 
            new ClientStringValue( entityName.toLowerCase() ) );
        SimpleNode<String> oidAVA = new EqualityNode<String>( M_OID_OID, 
            new ClientStringValue( entityName.toLowerCase() ) );
        filter.addNode( nameAVA );
        filter.addNode( oidAVA );
        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        EntryFilteringCursor cursor = null;

        try
        {
            cursor = partition.search( new SearchOperationContext( null, partition.getSuffixDn(),
                AliasDerefMode.DEREF_ALWAYS, filter, searchControls ) );

            if ( !cursor.next() )
            {
                return null;
            }

            ServerEntry sr = cursor.get();
            
            if ( cursor.next() )
            {
                throw new NamingException( "Got more than one result for the entity name: " + entityName );
            }

            return sr;
        }
        finally
        {
            if ( cursor != null )
            {
                cursor.close();
            }
        }
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#enableSchema(java.lang.String)
     */
    public void enableSchema( String schemaName ) throws Exception
    {
        LdapDN dn = new LdapDN( "cn=" + schemaName + ",ou=schema" );
        dn.normalize( atRegistry.getNormalizerMapping() );
        ServerEntry entry = partition.lookup( new LookupOperationContext( null, dn ) );
        EntryAttribute disabledAttr = entry.get( disabledAttributeType );
        List<Modification> mods = new ArrayList<Modification>( 3 );

        if ( disabledAttr == null )
        {
            LOG.warn( "Does not make sense: you're trying to enable {} schema which is already enabled", schemaName );
            return;
        }

        boolean isDisabled = disabledAttr.contains( "TRUE" );
        if ( !isDisabled )
        {
            LOG.warn( "Does not make sense: you're trying to enable {} schema which is already enabled", schemaName );
            return;
        }

        mods.add( new ServerModification( ModificationOperation.REMOVE_ATTRIBUTE, new DefaultServerAttribute(
            MetaSchemaConstants.M_DISABLED_AT, atRegistry.lookup( MetaSchemaConstants.M_DISABLED_AT ) ) ) );

        mods.add( new ServerModification( ModificationOperation.ADD_ATTRIBUTE, new DefaultServerAttribute(
            SchemaConstants.MODIFIERS_NAME_AT, atRegistry.lookup( SchemaConstants.MODIFIERS_NAME_AT ),
            ServerDNConstants.ADMIN_SYSTEM_DN ) ) );

        mods.add( new ServerModification( ModificationOperation.ADD_ATTRIBUTE, new DefaultServerAttribute(
            SchemaConstants.MODIFY_TIMESTAMP_AT, atRegistry.lookup( SchemaConstants.MODIFY_TIMESTAMP_AT ), DateUtils
                .getGeneralizedTime() ) ) );

        partition.modify( new ModifyOperationContext( null, dn, mods ) );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#listSyntaxDependents(java.lang.String)
     */
    public Set<ServerEntry> listSyntaxDependents( String numericOid ) throws Exception
    {
        Set<ServerEntry> set = new HashSet<ServerEntry>();
        BranchNode filter = new AndNode();

        // subfilter for (| (objectClass=metaMatchingRule) (objectClass=metaAttributeType))  
        BranchNode or = new OrNode();
        or.addNode( new EqualityNode<String>( OBJECTCLASS_OID, 
            new ClientStringValue( MetaSchemaConstants.META_MATCHING_RULE_OC.toLowerCase() ) ) );
        or.addNode( new EqualityNode<String>( OBJECTCLASS_OID, 
            new ClientStringValue( MetaSchemaConstants.META_ATTRIBUTE_TYPE_OC.toLowerCase() ) ) );

        filter.addNode( or );
        filter.addNode( new EqualityNode<String>( M_SYNTAX_OID, new ClientStringValue( numericOid.toLowerCase() ) ) );

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        EntryFilteringCursor cursor = null;

        try
        {
            cursor = partition.search( new SearchOperationContext( null, partition.getSuffixDn(),
                AliasDerefMode.DEREF_ALWAYS, filter, searchControls ) );
            
            while ( cursor.next() )
            {
                set.add( cursor.get() );
            }
        }
        finally
        {
            if ( cursor != null )
            {
                cursor.close();
            }
        }

        return set;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#listMatchingRuleDependents(org.apache.directory.shared.ldap.schema.MatchingRule)
     */
    public Set<ServerEntry> listMatchingRuleDependents( MatchingRule mr ) throws Exception
    {
        Set<ServerEntry> set = new HashSet<ServerEntry>();
        BranchNode filter = new AndNode();

        // ( objectClass = metaAttributeType )
        filter.addNode( new EqualityNode<String>( OBJECTCLASS_OID, new ClientStringValue(
            MetaSchemaConstants.META_ATTRIBUTE_TYPE_OC.toLowerCase() ) ) );

        BranchNode or = new OrNode();
        or.addNode( new EqualityNode<String>( M_ORDERING_OID, new ClientStringValue( mr.getOid() ) ) );
        or.addNode( new EqualityNode<String>( M_SUBSTRING_OID, new ClientStringValue( mr.getOid() ) ) );
        or.addNode( new EqualityNode<String>( M_EQUALITY_OID, new ClientStringValue( mr.getOid() ) ) );
        filter.addNode( or );

        List<String> names = mr.getNames();
        
        if ( ( names != null ) || ( names.size() > 0 ) )
        {
            for ( String name : names )
            {
                or.addNode( new EqualityNode<String>( M_ORDERING_OID, new ClientStringValue( name.toLowerCase() ) ) );
                or.addNode( new EqualityNode<String>( M_SUBSTRING_OID, new ClientStringValue( name.toLowerCase() ) ) );
                or.addNode( new EqualityNode<String>( M_EQUALITY_OID, new ClientStringValue( name.toLowerCase() ) ) );
            }
        }

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        EntryFilteringCursor cursor = null;

        try
        {
            cursor = partition.search( new SearchOperationContext( null, partition.getSuffixDn(),
                AliasDerefMode.DEREF_ALWAYS, filter, searchControls ) );
            
            while ( cursor.next() )
            {
                set.add( cursor.get() );
            }
        }
        finally
        {
            if ( cursor != null )
            {
                cursor.close();
            }
        }

        return set;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#listAllNames()
     */
    public EntryFilteringCursor listAllNames() throws Exception
    {
        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        BranchNode filter = new AndNode();

        // (& (m-oid=*) (m-name=*) )
        filter.addNode( new PresenceNode( M_OID_OID ) );
        filter.addNode( new PresenceNode( M_NAME_OID ) );
        return partition.search( new SearchOperationContext( null, partition.getSuffixDn(),
            AliasDerefMode.DEREF_ALWAYS, filter, searchControls ) );
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#listAttributeTypeDependents(org.apache.directory.shared.ldap.schema.AttributeType)
     */
    public Set<ServerEntry> listAttributeTypeDependents( AttributeType at ) throws Exception
    {
        /*
         * Right now the following inefficient filter is being used:
         * 
         * ( & 
         *      ( | ( objectClass = metaAttributeType ) ( objectClass = metaObjectClass ) )
         *      ( | ( m-oid = $oid ) ( m-must = $oid ) ( m-supAttributeType = $oid ) )
         * )
         * 
         * the reason why this is inefficient is because the or terms have large scan counts
         * and several loops are going to be required.  The following search is better because
         * it constrains the results better:
         * 
         * ( |
         *      ( & ( objectClass = metaAttributeType ) ( m-supAttributeType = $oid ) )
         *      ( & ( objectClass = metaObjectClass ) ( | ( m-may = $oid ) ( m-must = $oid ) ) )
         * )
         */

        Set<ServerEntry> set = new HashSet<ServerEntry>();
        BranchNode filter = new AndNode();

        // ( objectClass = metaAttributeType )
        BranchNode or = new OrNode();
        or.addNode( new EqualityNode<String>( OBJECTCLASS_OID, 
            new ClientStringValue( MetaSchemaConstants.META_ATTRIBUTE_TYPE_OC.toLowerCase() ) ) );
        or.addNode( new EqualityNode<String>( OBJECTCLASS_OID, 
            new ClientStringValue( MetaSchemaConstants.META_OBJECT_CLASS_OC.toLowerCase() ) ) );
        filter.addNode( or );

        or = new OrNode();
        or.addNode( new EqualityNode<String>( M_MAY_OID, new ClientStringValue( at.getOid() ) ) );
        or.addNode( new EqualityNode<String>( M_MUST_OID, new ClientStringValue( at.getOid() ) ) );
        or.addNode( new EqualityNode<String>( M_SUP_ATTRIBUTE_TYPE_OID, new ClientStringValue( at.getOid() ) ) );
        filter.addNode( or );

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        EntryFilteringCursor cursor = null;

        try
        {
            cursor = partition.search( new SearchOperationContext( null, partition.getSuffixDn(),
                AliasDerefMode.DEREF_ALWAYS, filter, searchControls ) );
            
            while ( cursor.next() )
            {
                set.add( cursor.get() );
            }
        }
        finally
        {
            if ( cursor != null )
            {
                cursor.close();
            }
        }

        return set;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#listSchemaDependents(java.lang.String)
     */
    public Set<ServerEntry> listSchemaDependents( String schemaName ) throws Exception
    {
        /*
         * The following filter is being used:
         * 
         * ( & ( objectClass = metaSchema ) ( m-dependencies = $schemaName ) )
         */

        Set<ServerEntry> set = new HashSet<ServerEntry>();
        BranchNode filter = new AndNode();

        filter.addNode( new EqualityNode<String>( OBJECTCLASS_OID, 
            new ClientStringValue( MetaSchemaConstants.META_SCHEMA_OC.toLowerCase() ) ) );
        filter.addNode( new EqualityNode<String>( M_DEPENDENCIES_OID, 
            new ClientStringValue( schemaName.toLowerCase() ) ) );

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        EntryFilteringCursor cursor = null;

        try
        {
            cursor = partition.search( new SearchOperationContext( null, partition.getSuffixDn(),
                AliasDerefMode.DEREF_ALWAYS, filter, searchControls ) );
            
            while ( cursor.next() )
            {
                set.add( cursor.get() );
            }
        }
        finally
        {
            if ( cursor != null )
            {
                cursor.close();
            }
        }

        return set;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#listEnabledSchemaDependents(java.lang.String)
     */
    public Set<ServerEntry> listEnabledSchemaDependents( String schemaName ) throws Exception
    {
        Set<ServerEntry> set = new HashSet<ServerEntry>();
        BranchNode filter = new AndNode();

        filter.addNode( new EqualityNode<String>( OBJECTCLASS_OID, new ClientStringValue( 
            MetaSchemaConstants.META_SCHEMA_OC.toLowerCase() ) ) );
        filter.addNode( new EqualityNode<String>( M_DEPENDENCIES_OID, new ClientStringValue( 
            schemaName.toLowerCase() ) ) );

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        EntryFilteringCursor cursor = null;

        try
        {
            cursor = partition.search( new SearchOperationContext( null, partition.getSuffixDn(),
                AliasDerefMode.DEREF_ALWAYS, filter, searchControls ) );

            while ( cursor.next() )
            {
                ServerEntry sr = cursor.get();
                EntryAttribute disabled = sr.get( disabledAttributeType );

                if ( disabled == null )
                {
                    set.add( sr );
                }
                else if ( disabled.get().equals( "FALSE" ) )
                {
                    set.add( sr );
                }
            }
        }
        finally
        {
            if ( cursor != null )
            {
                cursor.close();
            }
        }

        return set;
    }


    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaPartitionDao#listObjectClassDependents(org.apache.directory.shared.ldap.schema.ObjectClass)
     */
    public Set<ServerEntry> listObjectClassDependents( ObjectClass oc ) throws Exception
    {
        /*
         * Right now the following inefficient filter is being used:
         * 
         * ( & 
         *      ( | ( objectClass = metaObjectClass ) ( objectClass = metaDITContentRule ) 
         *          ( objectClass = metaNameForm ) )
         *      ( | ( m-oc = $oid ) ( m-aux = $oid ) ( m-supObjectClass = $oid ) )
         * )
         * 
         * The reason why this is inefficient is because the or terms have large scan counts
         * and several loops are going to be required.  For example all the objectClasses and 
         * all the metaDITContentRules and all the metaNameForm candidates will be a massive 
         * number.  This is probably going to be bigger than the 2nd term where a candidate 
         * satisfies one of the terms.
         * 
         * The following search is better because it constrains the results better:
         * 
         * ( |
         *      ( & ( objectClass = metaNameForm ) ( m-oc = $oid ) )
         *      ( & ( objectClass = metaObjectClass ) ( m-supObjectClass = $oid ) )
         *      ( & ( objectClass = metaDITContentRule ) ( m-aux = $oid ) )
         * )
         */

        Set<ServerEntry> set = new HashSet<ServerEntry>();
        BranchNode filter = new AndNode();

        BranchNode or = new OrNode();
        or.addNode( new EqualityNode<String>( OBJECTCLASS_OID, new ClientStringValue( MetaSchemaConstants.META_NAME_FORM_OC
            .toLowerCase() ) ) );
        or.addNode( new EqualityNode<String>( OBJECTCLASS_OID, new ClientStringValue( MetaSchemaConstants.META_OBJECT_CLASS_OC
            .toLowerCase() ) ) );
        or.addNode( new EqualityNode<String>( OBJECTCLASS_OID, new ClientStringValue(
            MetaSchemaConstants.META_DIT_CONTENT_RULE_OC.toLowerCase() ) ) );
        filter.addNode( or );

        or = new OrNode();
        or.addNode( new EqualityNode<String>( M_AUX_OID, new ClientStringValue( oc.getOid() ) ) );
        or.addNode( new EqualityNode<String>( M_OC_OID, new ClientStringValue( oc.getOid() ) ) );
        or.addNode( new EqualityNode<String>( M_SUP_OBJECT_CLASS_OID, new ClientStringValue( oc.getOid() ) ) );
        filter.addNode( or );

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        EntryFilteringCursor cursor = null;

        try
        {
            cursor = partition.search( new SearchOperationContext( null, partition.getSuffixDn(),
                AliasDerefMode.DEREF_ALWAYS, filter, searchControls ) );
            
            while ( cursor.next() )
            {
                set.add( cursor.get() );
            }
        }
        finally
        {
            if ( cursor != null )
            {
                cursor.close();
            }
        }

        return set;
    }
}