package org.apache.eve.jndi ;


import java.util.Map ;
import java.util.Stack ;
import java.util.Iterator ;
import java.util.EmptyStackException ;
        
import javax.naming.Name ;
import javax.naming.Context ;
import javax.naming.ldap.LdapContext ;
import javax.naming.NamingEnumeration ;
import javax.naming.directory.Attributes ;
import javax.naming.directory.SearchControls ;
import javax.naming.directory.ModificationItem ;

import org.apache.ldap.common.filter.ExprNode ;
import org.apache.eve.PartitionNexus ;
import org.apache.eve.db.Database ;
import org.apache.eve.ContextPartition;


/**
 * Aspect coupling the JNDI provider with the nexus thereby facilitating the 
 * injection of interceptors between calls to the nexus from the JNDI.
 * 
 * @author <a href="mailto:aok123@bellsouth.net">Alex Karasulu</a>
 * @author $Author: akarasulu $
 * @version $Revision: 1.5 $
 */
public aspect ProviderNexusAspect
{
    // ------------------------------------------------------------------------
    // M E M B E R   I N T R O D U C T I O N S
    // ------------------------------------------------------------------------
    
    
    /** 
     * Adds a static ThreadLocal for a Stack of LdapContexts representing nested
     * backend nexus calls made by the JndiProvider's contexts within the same 
     * thread of execution.
     */
    private static ThreadLocal EveJndiProvider.s_contextStacks = new ThreadLocal() ;
    
    
    // ------------------------------------------------------------------------
    // M E T H O D   I N T R O D U C T I O N S
    // ------------------------------------------------------------------------


    /**
     * Pushes a calling LdapContext onto the Thread's ThreadLocal Stack before
     * calls are made on the nexus within the LdapContext code.
     * 
     * @param context the caller or the current context for the nexus call
     */
    private static void EveJndiProvider.push( LdapContext context )
    {
        Stack stack = ( Stack ) s_contextStacks.get() ;
        
        if ( null == stack )
        {
            stack = new Stack() ;
            s_contextStacks.set( stack ) ;
        }
        
        stack.push( context ) ;
    } 


    /**
     * Pops the head of the Thread's ThreadLocal Stack after calls complete on
     * the nexus within the LdapContext code.
     * 
     * @return the last LdapContext or caller
     */
    private static LdapContext EveJndiProvider.pop()
    {
        Stack stack = ( Stack ) s_contextStacks.get() ;
        
        if ( null == stack )
        {
            throw new EmptyStackException() ;
        }
        
        return ( LdapContext ) stack.pop() ;
    }
    
    
    /**
     * Peeks at the head of the Thread's ThreadLocal Stack.
     * 
     * @return the current LdapContext or caller
     */
    static LdapContext EveJndiProvider.peek()
    {
        Stack stack = ( Stack ) s_contextStacks.get() ;
        
        if ( null == stack )
        {
            throw new EmptyStackException() ;
        }
        
        return ( LdapContext ) stack.peek() ;
    }
    
    
    // ------------------------------------------------------------------------
    // A S P E C T   M E T H O D S 
    // ------------------------------------------------------------------------
    

    /**
     * Gets a shallow copy of the context stack.
     * 
     * @return the context Stack.
     */    
    private static Stack getContextStack()
    {
        Stack stack = ( Stack ) EveJndiProvider.s_contextStacks.get() ;
        
        if ( null == stack )
        {
            throw new NullPointerException( "Thread had null stack" ) ;
        }
        
        return ( Stack ) stack.clone() ;
    }
    
    
    // ------------------------------------------------------------------------
    // P O I N T   C U T S
    // ------------------------------------------------------------------------
    
    
    /**
     * Selects join points on any public method calls made on the BackendNexus
     * interface implementing proxy from code executing within an LdapContext 
     * caller.  Makes sure that Proxy methods (static) and the 
     * getInvokationHandler() do not get selected.
     * 
     * @param caller the calling LdapContext.
     */
    pointcut jndiNexusCalls( Context caller ):
        this( caller ) &&
        ! this( ContextPartition ) &&
        target( PartitionNexus ) &&
        ! target( Database ) &&
        (
        // these are for the ContextPartition interface methods 

        // not used anymore since it was moved to RootNexus class
        // call( public Attributes getRootDSE() ) ||
        		
        call( public Name getMatchedDn( Name, boolean ) ) ||
        call( public Name getSuffix( Name, boolean ) ) ||
		call( public Iterator listSuffixes( boolean ) ) ||
		call( public Attributes lookup( Name, String [] ) ) ||

		// these are for BackingStore interface methods
        call( public void add( String, Name, Attributes ) ) ||
        call( public void delete( Name ) ) ||
		call( public boolean hasEntry( Name ) ) ||
        call( public boolean isSuffix( Name ) ) ||
        call( public NamingEnumeration list( Name ) ) ||
		call( public Attributes lookup( Name ) ) ||
        call( public void modify( Name, int, Attributes ) ) ||
        call( public void modify( Name, ModificationItem [] ) ) ||
        call( public void modifyRdn( Name, String, boolean ) ) ||
        call( public void move( Name, Name ) ) ||
        call( public void move( Name, Name, String, boolean ) ) ||
        call( public NamingEnumeration 
           search( Name, Map, ExprNode, SearchControls ) ) ) ;
        

    /**
     * Selects join points where the Invokation default constructor executes.
     * 
     * @param a_invocation the Invocation instantiated
     */
    pointcut newInvocation( Invocation a_invocation ):
        target( a_invocation ) &&
        execution( public Invocation.new() ) ;
      
        
    // ------------------------------------------------------------------------
    // A D V I C E
    // ------------------------------------------------------------------------


    before( Context a_caller ):
        jndiNexusCalls( a_caller )
        {
    		EveJndiProvider.push( ( LdapContext ) a_caller ) ;
            //System.out.println( "\npushed " + a_caller + " for join point "
            //    + thisJoinPoint ) ;
        }
        

    after( Context a_caller ):
        jndiNexusCalls( a_caller ) 
        {
            LdapContext l_head = EveJndiProvider.pop() ;
            //System.out.println( "\npopped " + a_caller + " for join point "
            //    + thisJoinPoint ) ;
        }
      
        
    after( Invocation a_invocation ):
        newInvocation( a_invocation )
        {
            a_invocation.setContextStack( getContextStack() ) ;
            //System.out.println( 
            //    "\nJust set the context stack on a new Invocation: " 
            //    + thisJoinPoint ) ;
        }
}


