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
package org.apache.directory.shared.ldap.schema;


import org.apache.directory.shared.ldap.util.ArrayUtils;


/**
 * The abstract base class for all schema object types.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public abstract class AbstractSchemaObject implements SchemaObject
{
    /** a numeric object identifier */
    protected final String oid;
    
    /** The object hash code compiled only once to avoid doing it at every call */
    private int hash;

    /** whether or not this SchemaObject is active */
    protected boolean isObsolete;

    /** a human readable identifiers for this SchemaObject */
    protected String[] names = ArrayUtils.EMPTY_STRING_ARRAY;

    /** a short description of this SchemaObject */
    protected String description;

    /** the name of the schema this object is associated with */
    protected String schema;

    // ------------------------------------------------------------------------
    // C O N S T R U C T O R S
    // ------------------------------------------------------------------------

    /**
     * Creates an abstract SchemaObject.
     * 
     * @param oid
     *            the numeric object identifier (OID)
     * @see SchemaObject#getOid()
     * @see MatchingRuleUse
     * @throws NullPointerException
     *             if oid is null
     */
    protected AbstractSchemaObject( String oid )
    {
        this( oid, ArrayUtils.EMPTY_STRING_ARRAY, false, null );
        
        hash = oid.hashCode();
    }


    /**
     * Creates an abstract SchemaObject.
     * 
     * @param oid the numeric object identifier (OID)
     * @param names the human readable names for this SchemaObject
     * @throws NullPointerException if oid is null
     */
    protected AbstractSchemaObject( String oid, String[] names )
    {
        this( oid, names, false, null );

        hash = oid.hashCode();
    }


    /**
     * Creates an abstract SchemaObject.
     * 
     * @param oid the numeric object identifier (OID)
     * @param names the human readable names for this SchemaObject
     * @param isObsolete true if this object is inactive, false if active
     * @throws NullPointerException if oid is null
     */
    protected AbstractSchemaObject( String oid, String[] names, boolean isObsolete )
    {
        this( oid, names, isObsolete, null );

        hash = oid.hashCode();
    }


    /**
     * Creates an abstract SchemaObject.
     * 
     * @param oid the numeric object identifier (OID)
     * @param name the first human readable name for this SchemaObject
     * @param isObsolete true if this object is inactive, false if active
     * @throws NullPointerException if oid is null
     */
    protected AbstractSchemaObject( String oid, String name, boolean isObsolete )
    {
        this( oid, new String[]
            { name }, isObsolete, null );

        hash = oid.hashCode();
    }


    /**
     * Creates an abstract SchemaObject.
     * 
     * @param oid the numeric object identifier (OID)
     * @param isObsolete true if this object is inactive, false if active
     * @throws NullPointerException if oid is null
     */
    protected AbstractSchemaObject( String oid, boolean isObsolete )
    {
        this( oid, null, isObsolete, null );

        hash = oid.hashCode();
    }


    /**
     * Creates an abstract SchemaObject.
     * 
     * @param oid the numeric object identifier (OID)
     * @param description a brief description for the SchemaObject
     * @throws NullPointerException if oid is null
     */
    protected AbstractSchemaObject( String oid, String description )
    {
        this( oid, null, false, description );

        hash = oid.hashCode();
    }


    /**
     * Creates an abstract SchemaObject.
     * 
     * @param oid the numeric object identifier (OID)
     * @param names the human readable names for this SchemaObject
     * @param isObsolete true if this object is inactive, false if active
     * @param description a brief description for the SchemaObject
     * @throws NullPointerException if oid is null
     */
    protected AbstractSchemaObject( String oid, String[] names, boolean isObsolete, String description )
    {
        if ( oid == null )
        {
            throw new NullPointerException( "oid cannot be null" );
        }

        this.oid = oid;
        this.isObsolete = isObsolete;
        this.description = description;

        if ( names != null )
        {
            this.names = new String[names.length];
            System.arraycopy( names, 0, this.names, 0, names.length );
        }

        hash = oid.hashCode();
    }


    // ------------------------------------------------------------------------
    // P U B L I C A C C E S S O R S
    // ------------------------------------------------------------------------

    /**
     * @see SchemaObject#getOid()
     * @return an OID for this SchemaObject or its MatchingRule if this
     *         SchemaObject is a MatchingRuleUse object
     */
    public String getOid()
    {
        return oid;
    }


    /**
     * @see SchemaObject#isObsolete()
     * @return true if inactive, false if active
     */
    public boolean isObsolete()
    {
        return isObsolete;
    }


    /**
     * @see SchemaObject#getNames()
     * @return the names for this SchemaObject
     */
    public String[] getNamesRef()
    {
        return names;
    }


    /**
     * @see SchemaObject#getSchema()
     * @return the name of the schema associated with this schemaObject
     */
    public String getSchema()
    {
        return schema;
    }
    
    
    /**
     * @see SchemaObject#getName()
     * @return the first of the names for this SchemaObject or null if one does
     *         not exist
     */
    public String getName()
    {
        return ( names == null || names.length == 0 ) ? null : names[0];
    }


    /**
     * @see SchemaObject#getDescription()
     * @return a short description about this SchemaObject
     */
    public String getDescription()
    {
        return description;
    }


    // ------------------------------------------------------------------------
    // P R O T E C T E D M U T A T O R S
    // ------------------------------------------------------------------------

    
    /**
     * Sets whether or not this SchemaObject is inactived.
     * 
     * @param obsolete
     *            true if this object is inactive, false if it is in use
     */
    protected void setObsolete( boolean obsolete )
    {
        isObsolete = obsolete;
    }


    /**
     * Sets schema name this schema object is associated with.
     * 
     * @param schema the name of the schema this object is associated with
     */
    public void setSchema( String schema )
    {
        this.schema = schema;
    }


    /**
     * Sets the human readable names for this SchemaObject.
     * 
     * @param names
     *            the human readable names for this SchemaObject
     */
    protected void setNames( String[] names )
    {
        this.names = new String[names.length];
        System.arraycopy( names, 0, this.names, 0, names.length );
    }


    /**
     * Sets the brief description for this SchemaObject.
     * 
     * @param description
     *            the brief description for this SchemaObject
     */
    protected void setDescription( String description )
    {
        this.description = description;
    }


    // ------------------------------------------------------------------------
    // Object overloads
    // ------------------------------------------------------------------------


    /**
     * Based on the hashCode of the oid property. It's bre-computed, as this
     * value won't change once the instance is created.
     * 
     * @return the hashCode of the oid String
     */
    public int hashCode()
    {
        return hash;
    }


    /**
     * If the object implements SchemaObject and has the same OID as this
     * SchemaObject then they are considered equal.
     * 
     * @param obj
     *            the object to test for equality
     * @return true if obj is a SchemaObject and the OID's match
     */
    public boolean equals( Object obj )
    {
        if ( obj == this )
        {
            return true;
        }

        if ( obj instanceof SchemaObject )
        {
            return oid.equals( ( ( SchemaObject ) obj ).getOid() );
        }

        return false;
    }


    /**
     * Gets the String for the OID of this SchmeaObject.
     * 
     * @return the OID of this SchmeaObject
     */
    public String toString()
    {
        return "<" + oid + ", " + ( ( names == null ) || ( names.length == 0 ) ? "null" : names[0] ) + ">";
    }
}