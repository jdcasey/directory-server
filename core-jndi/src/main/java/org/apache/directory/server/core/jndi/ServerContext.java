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
package org.apache.directory.server.core.jndi;


import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.DefaultCoreSession;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.OperationManager;
import org.apache.directory.server.core.authn.LdapPrincipal;
import org.apache.directory.server.core.cursor.EmptyCursor;
import org.apache.directory.server.core.cursor.SingletonCursor;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.entry.DefaultServerEntry;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.entry.ServerEntryUtils;
import org.apache.directory.server.core.event.DirectoryListener;
import org.apache.directory.server.core.event.NotificationCriteria;
import org.apache.directory.server.core.filtering.BaseEntryFilteringCursor;
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
import org.apache.directory.server.core.interceptor.context.OperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.shared.ldap.constants.JndiPropertyConstants;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.exception.LdapNoPermissionException;
import org.apache.directory.shared.ldap.exception.LdapSchemaViolationException;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.AttributeTypeAndValue;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.name.Rdn;
import org.apache.directory.shared.ldap.util.AttributeUtils;
import org.apache.directory.shared.ldap.util.StringTools;

import javax.naming.Context;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.event.EventContext;
import javax.naming.event.NamingListener;
import javax.naming.ldap.Control;
import javax.naming.spi.DirStateFactory;
import javax.naming.spi.DirectoryManager;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;


/**
 * A non-federated abstract Context implementation.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public abstract class ServerContext implements EventContext
{
    /** property key used for deleting the old RDN on a rename */
    public static final String DELETE_OLD_RDN_PROP = JndiPropertyConstants.JNDI_LDAP_DELETE_RDN;

    /** Empty array of controls for use in dealing with them */
    protected static final Control[] EMPTY_CONTROLS = new Control[0];

    /** The directory service which owns this context **/
    private final DirectoryService service;

    /** The cloned environment used by this Context */
    private final Hashtable<String, Object> env;

    /** The distinguished name of this Context */
    private final LdapDN dn;

    /** The set of registered NamingListeners */
    private final Map<NamingListener,DirectoryListener> listeners = 
        new HashMap<NamingListener,DirectoryListener>();

    /** The request controls to set on operations before performing them */
    protected Control[] requestControls = EMPTY_CONTROLS;

    /** The response controls to set after performing operations */
    protected Control[] responseControls = EMPTY_CONTROLS;

    /** Connection level controls associated with the session */
    protected Control[] connectControls = EMPTY_CONTROLS;
    
    private final CoreSession session;


    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------

    
    /**
     * Must be called by all subclasses to initialize the nexus proxy and the
     * environment settings to be used by this Context implementation.  This
     * specific contstructor relies on the presence of the {@link
     * Context#PROVIDER_URL} key and value to determine the distinguished name
     * of the newly created context.  It also checks to make sure the
     * referenced name actually exists within the system.  This constructor
     * is used for all InitialContext requests.
     * 
     * @param service the parent service that manages this context
     * @param env the environment properties used by this context.
     * @throws NamingException if the environment parameters are not set 
     * correctly.
     */
    @SuppressWarnings(value = { "unchecked" })
    protected ServerContext( DirectoryService service, Hashtable<String, Object> env ) throws Exception
    {
        this.service = service;

        this.env = env;
        
        LdapJndiProperties props = LdapJndiProperties.getLdapJndiProperties( this.env );
        dn = props.getProviderDn();

        /*
         * Need do bind operation here, and opContext returned contains the 
         * newly created session.
         */
        BindOperationContext opContext = doBindOperation( props.getBindDn(), props.getCredentials(), 
            props.getSaslMechanism(), props.getSaslAuthId() );

        session = opContext.getSession();
        OperationManager operationManager = service.getOperationManager();
        
        if ( ! operationManager.hasEntry( new EntryOperationContext( session, dn ) ) )
        {
            throw new NameNotFoundException( dn + " does not exist" );
        }
    }
    
    
    /**
     * Must be called by all subclasses to initialize the nexus proxy and the
     * environment settings to be used by this Context implementation.  This
     * constructor is used to propagate new contexts from existing contexts.
     *
     * @param principal the directory user principal that is propagated
     * @param dn the distinguished name of this context
     * @param service the directory service core
     * @throws NamingException if there is a problem creating the new context
     */
    public ServerContext( DirectoryService service, LdapPrincipal principal, Name dn ) throws Exception
    {
        this.service = service;
        this.dn = ( LdapDN ) dn.clone();

        this.env = new Hashtable<String, Object>();
        this.env.put( PROVIDER_URL, dn.toString() );
        this.env.put( DirectoryService.JNDI_KEY, service );
        session = new DefaultCoreSession( principal, service );
        OperationManager operationManager = service.getOperationManager();
        
        if ( ! operationManager.hasEntry( new EntryOperationContext( session, ( LdapDN ) dn ) ) )
        {
            throw new NameNotFoundException( dn + " does not exist" );
        }
    }


    public ServerContext( DirectoryService service, CoreSession session, Name dn ) throws Exception
    {
        this.service = service;
        this.dn = ( LdapDN ) dn.clone();
        this.env = new Hashtable<String, Object>();
        this.env.put( PROVIDER_URL, dn.toString() );
        this.env.put( DirectoryService.JNDI_KEY, service );
        this.session = session;
        OperationManager operationManager = service.getOperationManager();
        
        if ( ! operationManager.hasEntry( new EntryOperationContext( session, ( LdapDN ) dn ) ) )
        {
            throw new NameNotFoundException( dn + " does not exist" );
        }
    }


    /**
     * Set the referral handling flag into the operation context using
     * the JNDI value stored into the environment.
     */
    protected void injectReferralControl( OperationContext opCtx )
    {
        if ( "ignore".equalsIgnoreCase( (String)env.get( Context.REFERRAL ) ) )
        {
            opCtx.ignoreReferral();
        }
        else if ( "throw".equalsIgnoreCase( (String)env.get( Context.REFERRAL ) ) )
        {
            opCtx.throwReferral();
        }
        else
        {
            // TODO : handle the 'follow' referral option 
            opCtx.throwReferral();
        }
    }
    // ------------------------------------------------------------------------
    // Protected Methods for Operations
    // ------------------------------------------------------------------------
    // Use these methods instead of manually calling the nexusProxy so we can
    // add request controls to operation contexts before the call and extract 
    // response controls from the contexts after the call.  NOTE that the 
    // requestControls must be cleared after each operation.  This makes a 
    // context not thread safe.
    // ------------------------------------------------------------------------

    /**
     * Used to encapsulate [de]marshalling of controls before and after add operations.
     * @param entry
     * @param target
     */
    protected void doAddOperation( LdapDN target, ServerEntry entry ) throws Exception
    {
        // setup the op context and populate with request controls
        AddOperationContext opCtx = new AddOperationContext( session, entry );

        opCtx.addRequestControls( requestControls );
        
        // Inject the referral handling into the operation context
        injectReferralControl( opCtx );
        
        // execute add operation
        OperationManager operationManager = service.getOperationManager();
        operationManager.add( opCtx );
    
        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after delete operations.
     * @param target
     */
    protected void doDeleteOperation( LdapDN target ) throws Exception
    {
        // setup the op context and populate with request controls
        DeleteOperationContext opCtx = new DeleteOperationContext( session, target );

        opCtx.addRequestControls( requestControls );

        // Inject the referral handling into the operation context
        injectReferralControl( opCtx );

        // execute delete operation
        OperationManager operationManager = service.getOperationManager();
        operationManager.delete( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after list operations.
     * @param dn
     * @param aliasDerefMode
     * @param filter
     * @param searchControls
     * @return NamingEnumeration
     */
    protected EntryFilteringCursor doSearchOperation( LdapDN dn, AliasDerefMode aliasDerefMode,
        ExprNode filter, SearchControls searchControls ) throws Exception
    {
        OperationManager operationManager = service.getOperationManager();
        EntryFilteringCursor results = null;
        OperationContext opContext;

        // We have to check if it's a compare operation or a search. 
        // A compare operation has a OBJECT scope search, the filter must
        // be of the form (object=value) (no wildcards), and no attributes
        // should be asked to be returned.
        if ( ( searchControls.getSearchScope() == SearchControls.OBJECT_SCOPE )
            && ( ( searchControls.getReturningAttributes() != null )
                && ( searchControls.getReturningAttributes().length == 0 ) )
            && ( filter instanceof EqualityNode ) )
        {
            opContext = new CompareOperationContext( session, dn, ((EqualityNode)filter).getAttribute(), ((EqualityNode)filter).getValue() );
            
            // Inject the referral handling into the operation context
            injectReferralControl( opContext );

            // Call the operation
            boolean result = operationManager.compare( (CompareOperationContext)opContext );

            // setup the op context and populate with request controls
            opContext = new SearchOperationContext( session, dn, aliasDerefMode, filter,
                searchControls );
            opContext.addRequestControls( requestControls );
            
            if ( result )
            {
                ServerEntry emptyEntry = new DefaultServerEntry( service.getRegistries(), LdapDN.EMPTY_LDAPDN ); 
                return new BaseEntryFilteringCursor( new SingletonCursor<ServerEntry>( emptyEntry ), (SearchOperationContext)opContext );
            }
            else
            {
                return new BaseEntryFilteringCursor( new EmptyCursor<ServerEntry>(), (SearchOperationContext)opContext );
            }
        }
        else
        {
            // It's a Search
            
            // setup the op context and populate with request controls
            opContext = new SearchOperationContext( session, dn, aliasDerefMode, filter,
                searchControls );
            opContext.addRequestControls( requestControls );

            // Inject the referral handling into the operation context
            injectReferralControl( opContext );

            // execute search operation
            results = operationManager.search( (SearchOperationContext)opContext );
        }

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opContext.getResponseControls();

        return results;
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after list operations.
     */
    protected EntryFilteringCursor doListOperation( LdapDN target ) throws Exception
    {
        // setup the op context and populate with request controls
        ListOperationContext opCtx = new ListOperationContext( session, target );
        opCtx.addRequestControls( requestControls );

        // execute list operation
        OperationManager operationManager = service.getOperationManager();
        EntryFilteringCursor results = operationManager.list( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();

        return results;
    }


    protected ServerEntry doGetRootDSEOperation( LdapDN target ) throws Exception
    {
        GetRootDSEOperationContext opCtx = new GetRootDSEOperationContext( session, target );
        opCtx.addRequestControls( requestControls );

        // do not reset request controls since this is not an external 
        // operation and not do bother setting the response controls either
        OperationManager operationManager = service.getOperationManager();
        return operationManager.getRootDSE( opCtx );
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after lookup operations.
     */
    protected ServerEntry doLookupOperation( LdapDN target ) throws Exception
    {
        // setup the op context and populate with request controls
        LookupOperationContext opCtx;

        // execute lookup/getRootDSE operation
        opCtx = new LookupOperationContext( session, target );
        opCtx.addRequestControls( requestControls );
        OperationManager operationManager = service.getOperationManager();
        ServerEntry serverEntry = operationManager.lookup( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();
        return serverEntry;
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after lookup operations.
     */
    protected ServerEntry doLookupOperation( LdapDN target, String[] attrIds ) throws Exception
    {
        // setup the op context and populate with request controls
        LookupOperationContext opCtx;

        // execute lookup/getRootDSE operation
        opCtx = new LookupOperationContext( session, target, attrIds );
        opCtx.addRequestControls( requestControls );
        OperationManager operationManager = service.getOperationManager();
        ClonedServerEntry serverEntry = operationManager.lookup( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();

        // Now remove the ObjectClass attribute if it has not been requested
        if ( ( opCtx.getAttrsId() != null ) && ( opCtx.getAttrsId().size() != 0 ) )
        {
            if ( ( serverEntry.get( SchemaConstants.OBJECT_CLASS_AT ) != null )
                && ( serverEntry.get( SchemaConstants.OBJECT_CLASS_AT ).size() == 0 ) )
            {
                serverEntry.removeAttributes( SchemaConstants.OBJECT_CLASS_AT );
            }
        }

        return serverEntry;
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after bind operations.
     */
    protected BindOperationContext doBindOperation( LdapDN bindDn, byte[] credentials, String saslMechanism, 
        String saslAuthId ) throws Exception
    {
        // setup the op context and populate with request controls
        BindOperationContext opCtx = new BindOperationContext( null );
        opCtx.setDn( bindDn );
        opCtx.setCredentials( credentials );
        opCtx.setSaslMechanism( saslMechanism );
        opCtx.setSaslAuthId( saslAuthId );
        opCtx.addRequestControls( requestControls );

        // execute bind operation
        OperationManager operationManager = service.getOperationManager();
        operationManager.bind( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();
        return opCtx;
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after moveAndRename operations.
     */
    protected void doMoveAndRenameOperation( LdapDN oldDn, LdapDN parent, String newRdn, boolean delOldDn )
        throws Exception
    {
        // setup the op context and populate with request controls
        MoveAndRenameOperationContext opCtx = new MoveAndRenameOperationContext( session, oldDn, parent, new Rdn(
            newRdn ), delOldDn );
        opCtx.addRequestControls( requestControls );

        // Inject the referral handling into the operation context
        injectReferralControl( opCtx );
        
        // execute moveAndRename operation
        OperationManager operationManager = service.getOperationManager();
        operationManager.moveAndRename( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after modify operations.
     */
    protected void doModifyOperation( LdapDN dn, List<Modification> modifications ) throws Exception
    {
        // setup the op context and populate with request controls
        ModifyOperationContext opCtx = new ModifyOperationContext( session, dn, modifications );
        opCtx.addRequestControls( requestControls );

        // Inject the referral handling into the operation context
        injectReferralControl( opCtx );
        
        // execute modify operation
        OperationManager operationManager = service.getOperationManager();
        operationManager.modify( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after moveAndRename operations.
     */
    protected void doMove( LdapDN oldDn, LdapDN target ) throws Exception
    {
        // setup the op context and populate with request controls
        MoveOperationContext opCtx = new MoveOperationContext( session, oldDn, target );
        opCtx.addRequestControls( requestControls );

        // Inject the referral handling into the operation context
        injectReferralControl( opCtx );
        
        // execute move operation
        OperationManager operationManager = service.getOperationManager();
        operationManager.move( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();
    }


    /**
     * Used to encapsulate [de]marshalling of controls before and after rename operations.
     */
    protected void doRename( LdapDN oldDn, String newRdn, boolean delOldRdn ) throws Exception
    {
        // setup the op context and populate with request controls
        RenameOperationContext opCtx = new RenameOperationContext( session, oldDn, new Rdn( newRdn ), delOldRdn );
        opCtx.addRequestControls( requestControls );

        // Inject the referral handling into the operation context
        injectReferralControl( opCtx );
        
        // execute rename operation
        OperationManager operationManager = service.getOperationManager();
        operationManager.rename( opCtx );

        // clear the request controls and set the response controls 
        requestControls = EMPTY_CONTROLS;
        responseControls = opCtx.getResponseControls();
    }

    
    public CoreSession getSession()
    {
        return session;
    }
    
    
    public DirectoryService getDirectoryService()
    {
        return service;
    }
    
    
    // ------------------------------------------------------------------------
    // New Impl Specific Public Methods
    // ------------------------------------------------------------------------

    /**
     * Gets a handle on the root context of the DIT.  The RootDSE as the present user.
     *
     * @return the rootDSE context
     * @throws NamingException if this fails
     */
    public abstract ServerContext getRootContext() throws NamingException;


    /**
     * Gets the {@link DirectoryService} associated with this context.
     *
     * @return the directory service associated with this context
     */
    public DirectoryService getService()
    {
        return service;
    }


    // ------------------------------------------------------------------------
    // Protected Accessor Methods
    // ------------------------------------------------------------------------

    
    /**
     * Gets the distinguished name of the entry associated with this Context.
     * 
     * @return the distinguished name of this Context's entry.
     */
    protected Name getDn()
    {
        return dn;
    }


    // ------------------------------------------------------------------------
    // JNDI Context Interface Methods
    // ------------------------------------------------------------------------

    /**
     * @see javax.naming.Context#close()
     */
    public void close() throws NamingException
    {
        for ( DirectoryListener listener : listeners.values() )
        {
            try
            {
                service.getEventService().removeListener( listener );
            }
            catch ( Exception e )
            {
                JndiUtils.wrap( e );
            }
        }
        
        listeners.clear();
    }


    /**
     * @see javax.naming.Context#getNameInNamespace()
     */
    public String getNameInNamespace() throws NamingException
    {
        return dn.getUpName();
    }


    /**
     * @see javax.naming.Context#getEnvironment()
     */
    public Hashtable<String, Object> getEnvironment()
    {
        return env;
    }


    /**
     * @see javax.naming.Context#addToEnvironment(java.lang.String, 
     * java.lang.Object)
     */
    public Object addToEnvironment( String propName, Object propVal ) throws NamingException
    {
        return env.put( propName, propVal );
    }


    /**
     * @see javax.naming.Context#removeFromEnvironment(java.lang.String)
     */
    public Object removeFromEnvironment( String propName ) throws NamingException
    {
        return env.remove( propName );
    }


    /**
     * @see javax.naming.Context#createSubcontext(java.lang.String)
     */
    public Context createSubcontext( String name ) throws NamingException
    {
        return createSubcontext( new LdapDN( name ) );
    }


    /**
     * @see javax.naming.Context#createSubcontext(javax.naming.Name)
     */
    public Context createSubcontext( Name name ) throws NamingException
    {
        LdapDN target = buildTarget( name );
        ServerEntry serverEntry = service.newEntry( target );
        serverEntry.add( SchemaConstants.OBJECT_CLASS_AT, SchemaConstants.TOP_OC, JavaLdapSupport.JCONTAINER_ATTR );

        // Now add the CN attribute, which is mandatory
        Rdn rdn = target.getRdn();

        if ( rdn != null )
        {
            if ( SchemaConstants.CN_AT.equals( rdn.getNormType() ) )
            {
                serverEntry.put( rdn.getUpType(), ( String ) rdn.getUpValue() );
            }
            else
            {
                // No CN in the rdn, this is an error
                throw new LdapSchemaViolationException( name
                    + " does not contains the mandatory 'cn' attribute for JavaContainer ObjectClass!",
                    ResultCodeEnum.OBJECT_CLASS_VIOLATION );
            }
        }
        else
        {
            // No CN in the rdn, this is an error
            throw new LdapSchemaViolationException( name
                + " does not contains the mandatory 'cn' attribute for JavaContainer ObjectClass!",
                ResultCodeEnum.OBJECT_CLASS_VIOLATION );
        }

        /*
         * Add the new context to the server which as a side effect adds 
         * operational attributes to the serverEntry refering instance which
         * can them be used to initialize a new ServerLdapContext.  Remember
         * we need to copy over the controls as well to propagate the complete 
         * environment besides what's in the hashtable for env.
         */
        try
        {
            doAddOperation( target, serverEntry );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }
        
        ServerLdapContext ctx = null;
        
        try
        {
            ctx = new ServerLdapContext( service, session.getEffectivePrincipal(), target );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }
        
        return ctx;
    }


    /**
     * @see javax.naming.Context#destroySubcontext(java.lang.String)
     */
    public void destroySubcontext( String name ) throws NamingException
    {
        destroySubcontext( new LdapDN( name ) );
    }


    /**
     * @see javax.naming.Context#destroySubcontext(javax.naming.Name)
     */
    public void destroySubcontext( Name name ) throws NamingException
    {
        LdapDN target = buildTarget( name );

        if ( target.size() == 0 )
        {
            throw new LdapNoPermissionException( "can't delete the rootDSE" );
        }

        try
        {
            doDeleteOperation( target );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }
    }


    /**
     * @see javax.naming.Context#bind(java.lang.String, java.lang.Object)
     */
    public void bind( String name, Object obj ) throws NamingException
    {
        bind( new LdapDN( name ), obj );
    }


    private void injectRdnAttributeValues( LdapDN target, ServerEntry serverEntry ) throws NamingException
    {
        // Add all the RDN attributes and their values to this entry
        Rdn rdn = target.getRdn( target.size() - 1 );

        if ( rdn.size() == 1 )
        {
            serverEntry.put( rdn.getUpType(), ( String ) rdn.getValue() );
        }
        else
        {
            for ( AttributeTypeAndValue atav : rdn )
            {
                serverEntry.put( atav.getUpType(), ( String ) atav.getNormValue() );
            }
        }
    }


    /**
     * @see javax.naming.Context#bind(javax.naming.Name, java.lang.Object)
     */
    public void bind( Name name, Object obj ) throws NamingException
    {
        // First, use state factories to do a transformation
        DirStateFactory.Result res = DirectoryManager.getStateToBind( obj, name, this, env, null );

        LdapDN target = buildTarget( name );

        // let's be sure that the Attributes is case insensitive
        ServerEntry outServerEntry = ServerEntryUtils.toServerEntry( AttributeUtils.toCaseInsensitive( res
            .getAttributes() ), target, service.getRegistries() );

        if ( outServerEntry != null )
        {
            try
            {
                doAddOperation( target, outServerEntry );
            }
            catch ( Exception e )
            {
                JndiUtils.wrap( e );
            }
            return;
        }

        if ( obj instanceof ServerEntry )
        {
            try
            {
                doAddOperation( target, ( ServerEntry ) obj );
            }
            catch ( Exception e )
            {
                JndiUtils.wrap( e );
            }
        }
        // Check for Referenceable
        else if ( obj instanceof Referenceable )
        {
            throw new NamingException( "Do not know how to store Referenceables yet!" );
        }
        // Store different formats
        else if ( obj instanceof Reference )
        {
            // Store as ref and add outAttrs
            throw new NamingException( "Do not know how to store References yet!" );
        }
        else if ( obj instanceof Serializable )
        {
            // Serialize and add outAttrs
            ServerEntry serverEntry = service.newEntry( target );

            if ( ( outServerEntry != null ) && ( outServerEntry.size() > 0 ) )
            {
                for ( EntryAttribute serverAttribute : outServerEntry )
                {
                    serverEntry.put( serverAttribute );
                }
            }

            // Get target and inject all rdn attributes into entry
            injectRdnAttributeValues( target, serverEntry );

            // Serialize object into entry attributes and add it.
            JavaLdapSupport.serialize( serverEntry, obj, service.getRegistries() );
            try
            {
                doAddOperation( target, serverEntry );
            }
            catch ( Exception e )
            {
                JndiUtils.wrap( e );
            }
        }
        else if ( obj instanceof DirContext )
        {
            // Grab attributes and merge with outAttrs
            ServerEntry serverEntry = ServerEntryUtils.toServerEntry( ( ( DirContext ) obj ).getAttributes( "" ),
                target, service.getRegistries() );

            if ( ( outServerEntry != null ) && ( outServerEntry.size() > 0 ) )
            {
                for ( EntryAttribute serverAttribute : outServerEntry )
                {
                    serverEntry.put( serverAttribute );
                }
            }

            injectRdnAttributeValues( target, serverEntry );
            try
            {
                doAddOperation( target, serverEntry );
            }
            catch ( Exception e )
            {
                JndiUtils.wrap( e );
            }
        }
        else
        {
            throw new NamingException( "Can't find a way to bind: " + obj );
        }
    }


    /**
     * @see javax.naming.Context#rename(java.lang.String, java.lang.String)
     */
    public void rename( String oldName, String newName ) throws NamingException
    {
        rename( new LdapDN( oldName ), new LdapDN( newName ) );
    }


    /**
     * @see javax.naming.Context#rename(javax.naming.Name, javax.naming.Name)
     */
    public void rename( Name oldName, Name newName ) throws NamingException
    {
        LdapDN oldDn = buildTarget( oldName );
        LdapDN newDn = buildTarget( newName );

        if ( oldDn.size() == 0 )
        {
            throw new LdapNoPermissionException( "can't rename the rootDSE" );
        }

        // calculate parents
        LdapDN oldBase = ( LdapDN ) oldName.clone();
        oldBase.remove( oldName.size() - 1 );
        LdapDN newBase = ( LdapDN ) newName.clone();
        newBase.remove( newName.size() - 1 );

        String newRdn = newName.get( newName.size() - 1 );
        String oldRdn = oldName.get( oldName.size() - 1 );
        boolean delOldRdn = true;

        /*
         * Attempt to use the java.naming.ldap.deleteRDN environment property
         * to get an override for the deleteOldRdn option to modifyRdn.  
         */
        if ( null != env.get( DELETE_OLD_RDN_PROP ) )
        {
            String delOldRdnStr = ( String ) env.get( DELETE_OLD_RDN_PROP );
            delOldRdn = !delOldRdnStr.equalsIgnoreCase( "false" ) && !delOldRdnStr.equalsIgnoreCase( "no" )
                && !delOldRdnStr.equals( "0" );
        }

        /*
         * We need to determine if this rename operation corresponds to a simple
         * RDN name change or a move operation.  If the two names are the same
         * except for the RDN then it is a simple modifyRdn operation.  If the
         * names differ in size or have a different baseDN then the operation is
         * a move operation.  Furthermore if the RDN in the move operation 
         * changes it is both an RDN change and a move operation.
         */
        if ( ( oldName.size() == newName.size() ) && oldBase.equals( newBase ) )
        {
            try
            {
                doRename( oldDn, newRdn, delOldRdn );
            }
            catch ( Exception e )
            {
                JndiUtils.wrap( e );
            }
        }
        else
        {
            LdapDN target = ( LdapDN ) newDn.clone();
            target.remove( newDn.size() - 1 );

            if ( newRdn.equalsIgnoreCase( oldRdn ) )
            {
                try
                {
                    doMove( oldDn, target );
                }
                catch ( Exception e )
                {
                    JndiUtils.wrap( e );
                }
            }
            else
            {
                try
                {
                    doMoveAndRenameOperation( oldDn, target, newRdn, delOldRdn );
                }
                catch ( Exception e )
                {
                    JndiUtils.wrap( e );
                }
            }
        }
    }


    /**
     * @see javax.naming.Context#rebind(java.lang.String, java.lang.Object)
     */
    public void rebind( String name, Object obj ) throws NamingException
    {
        rebind( new LdapDN( name ), obj );
    }


    /**
     * @see javax.naming.Context#rebind(javax.naming.Name, java.lang.Object)
     */
    public void rebind( Name name, Object obj ) throws NamingException
    {
        LdapDN target = buildTarget( name );
        OperationManager operationManager = service.getOperationManager();
        
        try
        {
            if ( operationManager.hasEntry( new EntryOperationContext( session, target ) ) )
            {
                doDeleteOperation( target );
            }
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }

        bind( name, obj );
    }


    /**
     * @see javax.naming.Context#unbind(java.lang.String)
     */
    public void unbind( String name ) throws NamingException
    {
        unbind( new LdapDN( name ) );
    }


    /**
     * @see javax.naming.Context#unbind(javax.naming.Name)
     */
    public void unbind( Name name ) throws NamingException
    {
        try
        {
            doDeleteOperation( buildTarget( name ) );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }
    }


    /**
     * @see javax.naming.Context#lookup(java.lang.String)
     */
    public Object lookup( String name ) throws NamingException
    {
        if ( StringTools.isEmpty( name ) )
        {
            return lookup( LdapDN.EMPTY_LDAPDN );
        }
        else
        {
            return lookup( new LdapDN( name ) );
        }
    }


    /**
     * @see javax.naming.Context#lookup(javax.naming.Name)
     */
    public Object lookup( Name name ) throws NamingException
    {
        Object obj;
        LdapDN target = buildTarget( name );

        ServerEntry serverEntry = null;

        try
        {
            if ( name.size() == 0 )
            {
                serverEntry = doGetRootDSEOperation( target );
            }
            else
            {
                serverEntry = doLookupOperation( target );
            }
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }

        try
        {
            obj = DirectoryManager.getObjectInstance( null, name, this, env, 
                ServerEntryUtils.toBasicAttributes( serverEntry ) );
        }
        catch ( Exception e )
        {
            String msg = "Failed to create an object for " + target;
            msg += " using object factories within the context's environment.";
            NamingException ne = new NamingException( msg );
            ne.setRootCause( e );
            throw ne;
        }

        if ( obj != null )
        {
            return obj;
        }

        // First lets test and see if the entry is a serialized java object
        if ( serverEntry.get( JavaLdapSupport.JCLASSNAME_ATTR ) != null )
        {
            // Give back serialized object and not a context
            return JavaLdapSupport.deserialize( serverEntry );
        }

        // Initialize and return a context since the entry is not a java object
        ServerLdapContext ctx = null;
        
        try
        {
            ctx = new ServerLdapContext( service, session.getEffectivePrincipal(), target );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }
        
        return ctx;
    }


    /**
     * @see javax.naming.Context#lookupLink(java.lang.String)
     */
    public Object lookupLink( String name ) throws NamingException
    {
        throw new UnsupportedOperationException();
    }


    /**
     * @see javax.naming.Context#lookupLink(javax.naming.Name)
     */
    public Object lookupLink( Name name ) throws NamingException
    {
        throw new UnsupportedOperationException();
    }


    /**
     * Non-federated implementation presuming the name argument is not a 
     * composite name spanning multiple namespaces but a compound name in 
     * the same LDAP namespace.  Hence the parser returned is always the
     * same as calling this method with the empty String. 
     * 
     * @see javax.naming.Context#getNameParser(java.lang.String)
     */
    public NameParser getNameParser( String name ) throws NamingException
    {
        return new NameParser()
        {
            public Name parse( String name ) throws NamingException
            {
                return new LdapDN( name );
            }
        };
    }


    /**
     * Non-federated implementation presuming the name argument is not a 
     * composite name spanning multiple namespaces but a compound name in 
     * the same LDAP namespace.  Hence the parser returned is always the
     * same as calling this method with the empty String Name.
     * 
     * @see javax.naming.Context#getNameParser(javax.naming.Name)
     */
    public NameParser getNameParser( Name name ) throws NamingException
    {
        return new NameParser()
        {
            public Name parse( String name ) throws NamingException
            {
                return new LdapDN( name );
            }
        };
    }


    /**
     * @see javax.naming.Context#list(java.lang.String)
     */
    @SuppressWarnings(value =
        { "unchecked" })
    public NamingEnumeration list( String name ) throws NamingException
    {
        return list( new LdapDN( name ) );
    }


    /**
     * @see javax.naming.Context#list(javax.naming.Name)
     */
    @SuppressWarnings(value =
        { "unchecked" })
    public NamingEnumeration list( Name name ) throws NamingException
    {
        try
        {
            return new NamingEnumerationAdapter( doListOperation( buildTarget( name ) ) );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
            return null; // shut up compiler
        }
    }


    /**
     * @see javax.naming.Context#listBindings(java.lang.String)
     */
    @SuppressWarnings(value =
        { "unchecked" })
    public NamingEnumeration listBindings( String name ) throws NamingException
    {
        return listBindings( new LdapDN( name ) );
    }


    /**
     * @see javax.naming.Context#listBindings(javax.naming.Name)
     */
    @SuppressWarnings(value =
        { "unchecked" })
    public NamingEnumeration listBindings( Name name ) throws NamingException
    {
        // Conduct a special one level search at base for all objects
        LdapDN base = buildTarget( name );
        PresenceNode filter = new PresenceNode( SchemaConstants.OBJECT_CLASS_AT );
        SearchControls ctls = new SearchControls();
        ctls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        AliasDerefMode aliasDerefMode = AliasDerefMode.getEnum( getEnvironment() );
        try
        {
            return new NamingEnumerationAdapter( doSearchOperation( base, aliasDerefMode, filter, ctls ) );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
            return null; // shutup compiler
        }
    }


    /**
     * @see javax.naming.Context#composeName(java.lang.String, java.lang.String)
     */
    public String composeName( String name, String prefix ) throws NamingException
    {
        return composeName( new LdapDN( name ), new LdapDN( prefix ) ).toString();
    }


    /**
     * @see javax.naming.Context#composeName(javax.naming.Name,
     * javax.naming.Name)
     */
    public Name composeName( Name name, Name prefix ) throws NamingException
    {
        // No prefix reduces to name, or the name relative to this context
        if ( prefix == null || prefix.size() == 0 )
        {
            return name;
        }

        /*
         * Example: This context is ou=people and say name is the relative
         * name of uid=jwalker and the prefix is dc=domain.  Then we must
         * compose the name relative to prefix which would be:
         * 
         * uid=jwalker,ou=people,dc=domain.
         * 
         * The following general algorithm generates the right name:
         *      1). Find the Dn for name and walk it from the head to tail
         *          trying to match for the head of prefix.
         *      2). Remove name components from the Dn until a match for the 
         *          head of the prefix is found.
         *      3). Return the remainder of the fqn or Dn after chewing off some
         */

        // 1). Find the Dn for name and walk it from the head to tail
        Name fqn = buildTarget( name );
        String head = prefix.get( 0 );

        // 2). Walk the fqn trying to match for the head of the prefix
        while ( fqn.size() > 0 )
        {
            // match found end loop
            if ( fqn.get( 0 ).equalsIgnoreCase( head ) )
            {
                return fqn;
            }
            else
            // 2). Remove name components from the Dn until a match 
            {
                fqn.remove( 0 );
            }
        }

        String msg = "The prefix '" + prefix + "' is not an ancestor of this ";
        msg += "entry '" + dn + "'";
        throw new NamingException( msg );
    }


    // ------------------------------------------------------------------------
    // EventContext implementations
    // ------------------------------------------------------------------------

    public void addNamingListener( Name name, int scope, NamingListener namingListener ) throws NamingException
    {
        ExprNode filter = new PresenceNode( SchemaConstants.OBJECT_CLASS_AT );

        try
        {
            DirectoryListener listener = new EventListenerAdapter( ( ServerLdapContext ) this, namingListener );
            NotificationCriteria criteria = new NotificationCriteria();
            criteria.setFilter( filter );
            criteria.setScope( SearchScope.getSearchScope( scope ) );
            criteria.setAliasDerefMode( AliasDerefMode.getEnum( env ) );
            criteria.setBase( buildTarget( name ) );
            
            service.getEventService().addListener( listener );
            listeners.put( namingListener, listener );
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }
    }


    public void addNamingListener( String name, int scope, NamingListener namingListener ) throws NamingException
    {
        addNamingListener( new LdapDN( name ), scope, namingListener );
    }


    public void removeNamingListener( NamingListener namingListener ) throws NamingException
    {
        try
        {
            DirectoryListener listener = listeners.remove( namingListener );
            
            if ( listener != null )
            {
                service.getEventService().removeListener( listener );
            }
        }
        catch ( Exception e )
        {
            JndiUtils.wrap( e );
        }
    }


    public boolean targetMustExist() throws NamingException
    {
        return false;
    }


    /**
     * Allows subclasses to register and unregister listeners.
     *
     * @return the set of listeners used for tracking registered name listeners.
     */
    protected Map<NamingListener, DirectoryListener> getListeners()
    {
        return listeners;
    }


    // ------------------------------------------------------------------------
    // Utility Methods to Reduce Code
    // ------------------------------------------------------------------------

    /**
     * Clones this context's DN and adds the components of the name relative to 
     * this context to the left hand side of this context's cloned DN. 
     * 
     * @param relativeName a name relative to this context.
     * @return the name of the target
     * @throws InvalidNameException if relativeName is not a valid name in
     *      the LDAP namespace.
     */
    LdapDN buildTarget( Name relativeName ) throws InvalidNameException
    {
        LdapDN target = ( LdapDN ) dn.clone();

        // Add to left hand side of cloned DN the relative name arg
        target.addAllNormalized( target.size(), relativeName );
        return target;
    }
}