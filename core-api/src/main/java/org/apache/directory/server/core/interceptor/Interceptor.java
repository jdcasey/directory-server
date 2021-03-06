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
package org.apache.directory.server.core.interceptor;


import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.interceptor.context.CompareOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.EntryOperationContext;
import org.apache.directory.server.core.interceptor.context.GetRootDSEOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.interceptor.context.UnbindOperationContext;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.exception.LdapException;


/**
 * Filters invocations on {@link DefaultPartitionNexus}.  {@link Interceptor}
 * filters most method calls performed on {@link DefaultPartitionNexus} just
 * like Servlet filters do.
 * <p/>
 * <h2>Interceptor Chaining</h2>
 * 
 * Interceptors should usually pass the control
 * of current invocation to the next interceptor by calling an appropriate method
 * on {@link NextInterceptor}.  The flow control is returned when the next 
 * interceptor's filter method returns. You can therefore implement pre-, post-,
 * around- invocation handler by how you place the statement.  Otherwise, you
 * can transform the invocation into other(s).
 * <p/>
 * <h3>Pre-invocation Filtering</h3>
 * <pre>
 * public void delete( NextInterceptor nextInterceptor, Name name )
 * {
 *     System.out.println( "Starting invocation." );
 *     nextInterceptor.delete( name );
 * }
 * </pre>
 * <p/>
 * <h3>Post-invocation Filtering</h3>
 * <pre>
 * public void delete( NextInterceptor nextInterceptor, Name name )
 * {
 *     nextInterceptor.delete( name );
 *     System.out.println( "Invocation ended." );
 * }
 * </pre>
 * <p/>
 * <h3>Around-invocation Filtering</h3>
 * <pre>
 * public void delete( NextInterceptor nextInterceptor, Name name )
 * {
 *     long startTime = System.currentTimeMillis();
 *     try
 *     {
 *         nextInterceptor.delete( name );
 *     }
 *     finally
 *     {
 *         long endTime = System.currentTimeMillis();
 *         System.out.println( ( endTime - startTime ) + "ms elapsed." );
 *     }
 * }
 * </pre>
 * <p/>
 * <h3>Transforming invocations</h3>
 * <pre>
 * public void delete( NextInterceptor nextInterceptor, Name name )
 * {
 *     // transform deletion into modification.
 *     Attribute mark = new AttributeImpl( "entryDeleted", "true" );
 *     nextInterceptor.modify( name, DirIteratorContext.REPLACE_ATTRIBUTE, mark );
 * }
 * </pre>
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public interface Interceptor
{
    /**
     * Name that must be unique in an interceptor chain
     * @return name of this interceptor, must be unique in an interceptor chain.
     */
    String getName();

    /**
     * Intializes this interceptor.  This is invoked by {@link InterceptorChain}
     * when this intercepter is loaded into interceptor chain.
     * @throws Exception 
     */
    void init( DirectoryService directoryService ) throws LdapException;


    /**
     * Deinitializes this interceptor.  This is invoked by {@link InterceptorChain}
     * when this intercepter is unloaded from interceptor chain.
     */
    void destroy();


    /**
     * Filters {@link DefaultPartitionNexus#getRootDSE( GetRootDSEOperationContext )} call.
     */
    Entry getRootDSE( NextInterceptor next, GetRootDSEOperationContext  getRootDseContext ) throws LdapException;


    /**
     * Filters {@link DefaultPartitionNexus#compare( CompareOperationContext )} call.
     */
    boolean compare( NextInterceptor next, CompareOperationContext compareContext) throws LdapException;


    /**
     * Filters {@link Partition#delete( DeleteOperationContext )} call.
     */
    void delete( NextInterceptor next, DeleteOperationContext deleteContext ) throws LdapException;


    /**
     * Filters {@link Partition#add( AddOperationContext )} call.
     */
    void add( NextInterceptor next, AddOperationContext addContext ) throws LdapException;


    /**
     * Filters {@link Partition#modify( ModifyOperationContext )} call.
     */
    void modify( NextInterceptor next, ModifyOperationContext modifyContext ) throws LdapException;


    /**
     * Filters {@link Partition#list( ListOperationContext )} call.
     */
    EntryFilteringCursor list( NextInterceptor next, ListOperationContext listContext ) throws LdapException;


    /**
     * Filters {@link Partition#search( SearchOperationContext )} call.
     */
    EntryFilteringCursor search( NextInterceptor next, SearchOperationContext searchContext ) throws LdapException;


    /**
     * Filters {@link Partition#lookup( LookupOperationContext )} call.
     */
    Entry lookup( NextInterceptor next, LookupOperationContext lookupContext ) throws LdapException;


    /**
     * Filters {@link Partition#hasEntry( EntryOperationContext )} call.
     */
    boolean hasEntry( NextInterceptor next, EntryOperationContext hasEntryContext ) throws LdapException;


    /**
     * Filters {@link Partition#rename( RenameOperationContext )} call.
     */
    void rename( NextInterceptor next, RenameOperationContext renameContext ) throws LdapException;


    /**
     * Filters {@link Partition#move( MoveOperationContext )} call.
     */
    void move( NextInterceptor next, MoveOperationContext moveContext ) throws LdapException;


    /**
     * Filters {@link Partition#moveAndRename( MoveAndRenameOperationContext) } call.
     */
    void moveAndRename( NextInterceptor next, MoveAndRenameOperationContext moveAndRenameContext )
        throws LdapException;

    /**
     * Filters {@link Partition#bind( BindOperationContext )} call.
     */
    void bind( NextInterceptor next, BindOperationContext bindContext )
        throws LdapException;

    /**
     * Filters {@link Partition#unbind( UnbindOperationContext )} call.
     */
    void unbind( NextInterceptor next, UnbindOperationContext unbindContext ) throws LdapException;
}
