/*
 * Copyright 2016 Sai Pullabhotla.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jmethods.catatumbo.impl;

/**
 * Objects of this class contain metadata about identifier field of an entity.
 *
 * @author Sai Pullabhotla
 */
public class IdentifierMetadata extends FieldMetadata {

    /**
     * If identifier is to be auto generated or not
     */
    private boolean autoGenerated;

    /**
     * Creates a new instance of <code>IdentifierMetadata</code>.
     *
     * @param name the identifier field name
     * @param dataType the identifier data type
     * @param autoGenerated if the identifier is to be generated automatically
     */
    public IdentifierMetadata(String name, DataType dataType,
            boolean autoGenerated) {
        super(name, dataType);
        this.autoGenerated = autoGenerated;
    }

    /**
     * Tells whether or not the identifier is to be generated automatically.
     *
     * @return true, if the identifier is to be generated automatically; false,
     * otherwise.
     */
    public boolean isAutoGenerated() {
        return autoGenerated;
    }

    /**
     * Sets whether or not the identifier is to be generated automatically.
     *
     * @param autoGenerated whether or not the identifier is to be generated
     * automatically.
     */
    public void setAutoGenerated(boolean autoGenerated) {
        this.autoGenerated = autoGenerated;
    }

}