package org.apache.directory.server.core.schema;


import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.schema.SchemaManager;


public interface SchemaService
{

    /**
     * Tells if the given DN is the schemaSubentry DN
     * 
     * @param dnString The DN we want to check
     * @return <code>true</code> if the given DN is the Schema subentry DN
     * @throws LdapException If the given DN is not valid
     */
    boolean isSchemaSubentry( String dnString ) throws LdapException;


    /**
     * @return the schemaManager loaded from schemaPartition
     */
    SchemaManager getSchemaManager();


    SchemaPartition getSchemaPartition();


    void setSchemaPartition( SchemaPartition schemaPartition );
    
    
    /**
     * Initializes the SchemaService
     *
     * @throws Exception If the initializaion fails
     */
    void initialize() throws LdapException;


    /**
     * A seriously unsafe (unsynchronized) means to access the schemaSubentry.
     *
     * @return the schemaSubentry
     * @throws Exception if there is a failure to access schema timestamps
     */
    Entry getSubschemaEntryImmutable() throws LdapException;


    /**
     * A seriously unsafe (unsynchronized) means to access the schemaSubentry.
     *
     * @return the schemaSubentry
     * @throws Exception if there is a failure to access schema timestamps
     */
    Entry getSubschemaEntryCloned() throws LdapException;


    /**
     * Gets the schemaSubentry based on specific search id parameters which
     * include the special '*' and '+' operators.
     *
     * @param ids the ids of the attributes that should be returned from a search
     * @return the subschema entry with the ids provided
     * @throws Exception if there are failures during schema info access
     */
    Entry getSubschemaEntry( String[] ids ) throws LdapException;
}