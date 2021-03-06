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

import org.apache.directory.server.core.authn.AuthenticationInterceptor;
import org.apache.directory.server.core.authz.AciAuthorizationInterceptor;
import org.apache.directory.server.core.authz.DefaultAuthorizationInterceptor;
import org.apache.directory.server.core.event.Evaluator;
import org.apache.directory.server.core.event.EventInterceptor;
import org.apache.directory.server.core.event.ExpressionEvaluator;
import org.apache.directory.server.core.interceptor.context.OperationContext;
import org.apache.directory.server.core.normalization.NormalizationInterceptor;
import org.apache.directory.server.core.operational.OperationalAttributeInterceptor;
import org.apache.directory.server.core.schema.SchemaInterceptor;
import org.apache.directory.server.core.subtree.RefinementEvaluator;
import org.apache.directory.server.core.subtree.RefinementLeafEvaluator;
import org.apache.directory.server.core.subtree.SubentryInterceptor;
import org.apache.directory.server.core.subtree.SubtreeEvaluator;
import org.apache.directory.server.core.trigger.TriggerInterceptor;
import org.apache.directory.shared.ldap.aci.ACITuple;
import org.apache.directory.shared.ldap.aci.MicroOperation;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapNoPermissionException;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.SchemaManager;


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
     * @throws LdapException if failed to initialize internal components
     */
    public ACDFEngine( SchemaManager schemaManager ) throws LdapException
    {
        Evaluator entryEvaluator = new ExpressionEvaluator( schemaManager );
        SubtreeEvaluator subtreeEvaluator = new SubtreeEvaluator( schemaManager );
        RefinementEvaluator refinementEvaluator = new RefinementEvaluator( new RefinementLeafEvaluator( schemaManager.getGlobalOidRegistry() ) );

        filters = new ACITupleFilter[] {
            new RelatedUserClassFilter( subtreeEvaluator ),
            new RelatedProtectedItemFilter( refinementEvaluator, entryEvaluator, schemaManager ),
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
     * @param attributeType the attribute type of the attribute the user is trying to access.
     *               <tt>null</tt> if the user is not accessing a specific attribute type.
     * @param attrValue the attribute value of the attribute the user is trying to access.
     *                  <tt>null</tt> if the user is not accessing a specific attribute value.
     * @param microOperations the {@link org.apache.directory.shared.ldap.aci.MicroOperation}s to perform
     * @param aciTuples {@link org.apache.directory.shared.ldap.aci.ACITuple}s translated from {@link org.apache.directory.shared.ldap.aci.ACIItem}s in the subtree entries
     * @param entryView in case of a Modify operation, view of the entry being modified as if the modification permitted and completed
     * @throws LdapException if failed to evaluate ACI items
     */
    public void checkPermission( 
        SchemaManager schemaManager, 
        OperationContext opContext, 
        Collection<DN> userGroupNames, 
        DN username,
        AuthenticationLevel authenticationLevel, 
        DN entryName, 
        AttributeType attributeType, 
        Value<?> attrValue, 
        Collection<MicroOperation> microOperations, 
        Collection<ACITuple> aciTuples, 
        Entry entry, 
        Entry entryView ) throws LdapException
    {
        if ( !hasPermission( schemaManager, opContext, userGroupNames, username, authenticationLevel, entryName, 
            attributeType, attrValue, microOperations, aciTuples, entry, entryView ) )
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
        SchemaManager schemaManager, 
        OperationContext opContext, 
        Collection<DN> userGroupNames, 
        DN userName,
        AuthenticationLevel authenticationLevel, 
        DN entryName, 
        AttributeType attributeType, 
        Value<?> attrValue, 
        Collection<MicroOperation> microOperations, 
        Collection<ACITuple> aciTuples, 
        Entry entry, 
        Entry entryView ) throws LdapException
    {
        if ( entryName == null )
        {
            throw new IllegalArgumentException( "entryName" );
        }

        Entry userEntry = opContext.lookup( userName, USER_LOOKUP_BYPASS );

        // Determine the scope of the requested operation.
        OperationScope scope;
        
        if ( attributeType == null )
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
                schemaManager, 
                aciTuples, 
                scope, 
                opContext, 
                userGroupNames, 
                userName, 
                userEntry,
                authenticationLevel, 
                entryName, 
                attributeType, 
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
