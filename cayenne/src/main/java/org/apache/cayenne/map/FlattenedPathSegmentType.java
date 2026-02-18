/*****************************************************************
 *   Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 ****************************************************************/

package org.apache.cayenne.map;

/**
 * Classifies a {@link DbRelationship} segment within a flattened path
 * according to its role in the data model.
 *
 * <ul>
 *     <li>{@link #VI_TO_CHILD} — vertical inheritance: parent table → child table (toDependentPK, toOne)</li>
 *     <li>{@link #VI_TO_PARENT} — vertical inheritance: child table → parent table (reverse of toDependentPK)</li>
 *     <li>{@link #JOIN_TABLE} — many-to-many join table (target has multiple incoming toDependentPK)</li>
 *     <li>{@link #REGULAR} — any other relationship (standard FK-to-PK, etc.)</li>
 * </ul>
 *
 * @since 5.0
 */
public enum FlattenedPathSegmentType {

    /**
     * Vertical inheritance from parent to child table.
     * The relationship is toDependentPK, toOne, and the target entity
     * is a child in a vertical inheritance hierarchy sharing the parent's PK.
     */
    VI_TO_CHILD,

    /**
     * Vertical inheritance from child to parent table.
     * The reverse relationship is toDependentPK.
     */
    VI_TO_PARENT,

    /**
     * Relationship targeting a many-to-many join table.
     * The target entity has multiple incoming toDependentPK relationships.
     */
    JOIN_TABLE,

    /**
     * Any relationship that doesn't fall into the other categories.
     */
    REGULAR;

    /**
     * Returns {@code true} if this segment type represents a vertical inheritance link.
     */
    public boolean isVerticalInheritance() {
        return this == VI_TO_CHILD || this == VI_TO_PARENT;
    }
}
