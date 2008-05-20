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
package org.apache.directory.server.core.authz.support;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import javax.naming.NamingException;

import org.apache.directory.server.core.authn.AuthenticationInterceptor;
import org.apache.directory.server.core.authz.AciAuthorizationInterceptor;
import org.apache.directory.server.core.authz.DefaultAuthorizationInterceptor;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.event.Evaluator;
import org.apache.directory.server.core.event.EventInterceptor;
import org.apache.directory.server.core.event.ExpressionEvaluator;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.normalization.NormalizationInterceptor;
import org.apache.directory.server.core.operational.OperationalAttributeInterceptor;
import org.apache.directory.server.core.partition.PartitionNexusProxy;
import org.apache.directory.server.core.schema.SchemaInterceptor;
import org.apache.directory.server.core.subtree.RefinementEvaluator;
import org.apache.directory.server.core.subtree.RefinementLeafEvaluator;
import org.apache.directory.server.core.subtree.SubentryInterceptor;
import org.apache.directory.server.core.subtree.SubtreeEvaluator;
import org.apache.directory.server.core.trigger.TriggerInterceptor;
import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.server.schema.registries.OidRegistry;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.aci.ACITuple;
import org.apache.directory.shared.ldap.aci.MicroOperation;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapNoPermissionException;
import org.apache.directory.shared.ldap.name.LdapDN;


/**
 * An implementation of Access Control Decision Function (18.8, X.501).
 * <p>
 * This engine simply filters the collection of tuples using the following
 * {@link ACITupleFilter}s sequentially:
 * <ol>
 * <li>{@link RelatedUserClassFilter}</li>
 * <li>{@link RelatedProtectedItemFilter}</li>
 * <li>{@link MaxValueCountFilter}</li>
 * <li>{@link MaxImmSubFilter}</li>
 * <li>{@link RestrictedByFilter}</li>
 * <li>{@link MicroOperationFilter}</li>
 * <li>{@link HighestPrecedenceFilter}</li>
 * <li>{@link MostSpecificUserClassFilter}</li>
 * <li>{@link MostSpecificProtectedItemFilter}</li>
 * </ol>
 * <p>
 * Operation is determined to be permitted if and only if there is at least one
 * tuple left and all of them grants the access. (18.8.4. X.501)
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class ACDFEngine
{
    private final ACITupleFilter[] filters;


    /**
     * Creates a new instance.
     * 
     * @param oidRegistry an OID registry to be used by internal components
     * @param attrTypeRegistry an attribute type registry to be used by internal components 
     * 
     * @throws NamingException if failed to initialize internal components
     */
    public ACDFEngine(OidRegistry oidRegistry, AttributeTypeRegistry attrTypeRegistry) throws NamingException
    {
        Evaluator entryEvaluator = new ExpressionEvaluator( oidRegistry, attrTypeRegistry );
        SubtreeEvaluator subtreeEvaluator = new SubtreeEvaluator( oidRegistry, attrTypeRegistry );
        RefinementEvaluator refinementEvaluator = new RefinementEvaluator( new RefinementLeafEvaluator( oidRegistry ) );

        filters = new ACITupleFilter[] {
            new RelatedUserClassFilter( subtreeEvaluator ),
            new RelatedProtectedItemFilter( refinementEvaluator, entryEvaluator, oidRegistry, attrTypeRegistry ),
            new MaxValueCountFilter(),
            new MaxImmSubFilter(),
            new RestrictedByFilter(),
            new MicroOperationFilter(),
            new HighestPrecedenceFilter(),
            new MostSpecificUserClassFilter(),
            new MostSpecificProtectedItemFilter() };
    }


    /**
     * Checks the user with the specified name can access the specified resource
     * (entry, attribute type, or attribute value) and throws {@link LdapNoPermissionException}
     * if the user doesn't have any permission to perform the specified grants.
     * 
     * @param proxy the proxy to the partition nexus
     * @param userGroupNames the collection of the group DNs the user who is trying to access the resource belongs
     * @param username the DN of the user who is trying to access the resource
     * @param entryName the DN of the entry the user is trying to access
     * @param attrId the attribute type of the attribute the user is trying to access.
     *               <tt>null</tt> if the user is not accessing a specific attribute type.
     * @param attrValue the attribute value of the attribute the user is trying to access.
     *                  <tt>null</tt> if the user is not accessing a specific attribute value.
     * @param microOperations the {@link org.apache.directory.shared.ldap.aci.MicroOperation}s to perform
     * @param aciTuples {@link org.apache.directory.shared.ldap.aci.ACITuple}s translated from {@link org.apache.directory.shared.ldap.aci.ACIItem}s in the subtree entries
     * @param entryView in case of a Modify operation, view of the entry being modified as if the modification permitted and completed
     * @throws NamingException if failed to evaluate ACI items
     */
    public void checkPermission( 
        Registries registries, 
        PartitionNexusProxy proxy, 
        Collection<LdapDN> userGroupNames, 
        LdapDN username,
        AuthenticationLevel authenticationLevel, 
        LdapDN entryName, 
        String attrId, 
        Value<?> attrValue, 
        Collection<MicroOperation> microOperations, 
        Collection<ACITuple> aciTuples, 
        ServerEntry entry, 
        ServerEntry entryView ) throws NamingException
    {
        if ( !hasPermission( registries, proxy, userGroupNames, username, authenticationLevel, entryName, attrId, attrValue,
            microOperations, aciTuples, entry, entryView ) )
        {
            throw new LdapNoPermissionException();
        }
    }

    public static final Collection<String> USER_LOOKUP_BYPASS;
    static
    {
        Collection<String> c = new HashSet<String>();
        c.add( NormalizationInterceptor.class.getName() );
        c.add( AuthenticationInterceptor.class.getName() );
//        c.add( ReferralInterceptor.class.getName() );
        c.add( AciAuthorizationInterceptor.class.getName() );
        c.add( DefaultAuthorizationInterceptor.class.getName() );
//        c.add( ExceptionInterceptor.class.getName() );
        c.add( OperationalAttributeInterceptor.class.getName() );
        c.add( SchemaInterceptor.class.getName() );
        c.add( SubentryInterceptor.class.getName() );
//        c.add( CollectiveAttributeInterceptor.class.getName() );
        c.add( EventInterceptor.class.getName() );
        c.add( TriggerInterceptor.class.getName() );
        USER_LOOKUP_BYPASS = Collections.unmodifiableCollection( c );
    }


    /**
     * Returns <tt>true</tt> if the user with the specified name can access the specified resource
     * (entry, attribute type, or attribute value) and throws {@link LdapNoPermissionException}
     * if the user doesn't have any permission to perform the specified grants.
     * 
     * @param proxy the proxy to the partition nexus
     * @param userGroupNames the collection of the group DNs the user who is trying to access the resource belongs
     * @param userName the DN of the user who is trying to access the resource
     * @param entryName the DN of the entry the user is trying to access
     * @param attrId the attribute type of the attribute the user is trying to access.
     *               <tt>null</tt> if the user is not accessing a specific attribute type.
     * @param attrValue the attribute value of the attribute the user is trying to access.
     *                  <tt>null</tt> if the user is not accessing a specific attribute value.
     * @param microOperations the {@link org.apache.directory.shared.ldap.aci.MicroOperation}s to perform
     * @param aciTuples {@link org.apache.directory.shared.ldap.aci.ACITuple}s translated from {@link org.apache.directory.shared.ldap.aci.ACIItem}s in the subtree entries
     * @param entryView in case of a Modify operation, view of the entry being modified as if the modification permitted and completed
     */
    public boolean hasPermission( 
        Registries registries, 
        PartitionNexusProxy proxy, 
        Collection<LdapDN> userGroupNames, 
        LdapDN userName,
        AuthenticationLevel authenticationLevel, 
        LdapDN entryName, 
        String attrId, 
        Value<?> attrValue, 
        Collection<MicroOperation> microOperations, 
        Collection<ACITuple> aciTuples, 
        ServerEntry entry, 
        ServerEntry entryView ) throws NamingException
    {
        if ( entryName == null )
        {
            throw new NullPointerException( "entryName" );
        }

        ServerEntry userEntry = proxy.lookup( new LookupOperationContext( registries, userName ), USER_LOOKUP_BYPASS );

        // Determine the scope of the requested operation.
        OperationScope scope;
        
        if ( attrId == null )
        {
            scope = OperationScope.ENTRY;
        }
        else if ( attrValue == null )
        {
            scope = OperationScope.ATTRIBUTE_TYPE;
        }
        else
        {
            scope = OperationScope.ATTRIBUTE_TYPE_AND_VALUE;
        }

        // Clone aciTuples in case it is unmodifiable.
        aciTuples = new ArrayList<ACITuple>( aciTuples );

        // Filter unrelated and invalid tuples
        for ( ACITupleFilter filter : filters )
        {
            aciTuples = filter.filter( 
                registries, 
                aciTuples, 
                scope, 
                proxy, 
                userGroupNames, 
                userName, 
                userEntry,
                authenticationLevel, 
                entryName, 
                attrId, 
                attrValue, 
                entry, 
                microOperations, 
                entryView );
        }

        // Deny access if no tuples left.
        if ( aciTuples.size() == 0 )
        {
            return false;
        }

        // Grant access if and only if one or more tuples remain and
        // all grant access. Otherwise deny access.
        for ( ACITuple tuple : aciTuples )
        {
            if ( !tuple.isGrant() )
            {
                return false;
            }
        }

        return true;
    }
}