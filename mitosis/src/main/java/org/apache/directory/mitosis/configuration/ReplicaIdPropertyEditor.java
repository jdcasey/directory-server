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
package org.apache.directory.mitosis.configuration;


import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;

import org.apache.directory.mitosis.common.ReplicaId;


/**
 * A {@link PropertyEditor} that converts strings into {@link ReplicaId}s
 * and vice versa.
 *
 * @author The Apache Directory Project (dev@directory.apache.org)
 * @version $Rev: 95 $, $Date: 2006-09-16 13:04:28 +0200 (Sat, 16 Sep 2006) $
 */
public class ReplicaIdPropertyEditor extends PropertyEditorSupport
{
    public ReplicaIdPropertyEditor()
    {
        super();
    }


    public ReplicaIdPropertyEditor( Object source )
    {
        super( source );
    }


    public String getAsText()
    {
        Object val = getValue();
        if ( val == null )
        {
            return "";
        }
        else
        {
            return val.toString();
        }
    }


    public void setAsText( String text ) throws IllegalArgumentException
    {
        text = text.trim();
        if ( text.length() == 0 )
        {
            setValue( null );
        }
        else
        {
            setValue( new ReplicaId( text ) );
        }
    }
}