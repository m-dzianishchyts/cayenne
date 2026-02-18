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

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes a flattened {@link ObjRelationship}'s {@link DbRelationship} path
 * and classifies each segment as one of the {@link FlattenedPathSegmentType} values.
 * <p>
 * This classification is performed once per relationship and cached in
 * {@link ObjRelationship} as a {@link FlattenedPathInfo}, so that downstream
 * consumers never need to re-derive the nature of each segment from raw
 * join metadata.
 * </p>
 *
 * @since 5.0
 */
public class FlattenedPathAnalyzer {

    /**
     * Analyzes the given flattened relationship and returns a {@link FlattenedPathInfo}
     * with each segment classified.
     *
     * @param relationship a flattened ObjRelationship (must have more than one DbRelationship)
     * @return classified path info
     * @throws IllegalArgumentException if the relationship is not flattened
     */
    public static FlattenedPathInfo analyze(ObjRelationship relationship) {
        List<DbRelationship> dbRels = relationship.getDbRelationships();
        if (dbRels.size() < 2) {
            throw new IllegalArgumentException(
                    "FlattenedPathAnalyzer requires a flattened relationship (>1 DbRelationship), got: "
                            + relationship.getName());
        }

        List<FlattenedPathInfo.AnnotatedSegment> segments = new ArrayList<>(dbRels.size());
        for (DbRelationship dbRel : dbRels) {
            FlattenedPathSegmentType type = classifySegment(dbRel);
            segments.add(new FlattenedPathInfo.AnnotatedSegment(dbRel, type));
        }

        return new FlattenedPathInfo(segments);
    }

    /**
     * Classifies a single {@link DbRelationship} segment.
     *
     * <p>Classification rules:</p>
     * <ol>
     *     <li><b>VI_TO_CHILD</b>: the relationship is toDependentPK, toOne,
     *         and has all-PK joins (parent → child in vertical inheritance).</li>
     *     <li><b>VI_TO_PARENT</b>: the reverse relationship is toDependentPK, and
     *         this relationship is toOne with all-PK joins (child → parent in vertical inheritance).</li>
     *     <li><b>JOIN_TABLE</b>: the target entity has multiple incoming toDependentPK
     *         relationships, indicating a many-to-many join table.</li>
     *     <li><b>REGULAR</b>: everything else.</li>
     * </ol>
     */
    public static FlattenedPathSegmentType classifySegment(DbRelationship dbRel) {
        if (dbRel.isToMany()) {
            // toMany can point to a join table
            if (isJoinTableTarget(dbRel.getTargetEntity())) {
                return FlattenedPathSegmentType.JOIN_TABLE;
            }
            return FlattenedPathSegmentType.REGULAR;
        }

        // toOne: check for VI patterns
        if (dbRel.isToDependentPK() && hasAllPkJoins(dbRel)) {
            // parent - child (toDependentPK, all PK-to-PK joins)
            if (isJoinTableTarget(dbRel.getTargetEntity())) {
                return FlattenedPathSegmentType.JOIN_TABLE;
            }
            return FlattenedPathSegmentType.VI_TO_CHILD;
        }

        DbRelationship reverseRel = dbRel.getReverseRelationship();
        if (reverseRel != null && reverseRel.isToDependentPK() && hasAllPkJoins(dbRel)) {
            // child - parent (reverse is toDependentPK, all PK-to-PK joins)
            return FlattenedPathSegmentType.VI_TO_PARENT;
        }

        return FlattenedPathSegmentType.REGULAR;
    }

    /**
     * Returns {@code true} if all joins in the relationship map PK columns on both sides.
     */
    private static boolean hasAllPkJoins(DbRelationship dbRel) {
        for (DbJoin join : dbRel.getJoins()) {
            if (!join.getSource().isPrimaryKey() || !join.getTarget().isPrimaryKey()) {
                return false;
            }
        }
        return !dbRel.getJoins().isEmpty();
    }

    /**
     * Returns {@code true} if the target entity is a join table, i.e. it has
     * more than one incoming toDependentPK relationship from other entities.
     * <p>
     * In vertical inheritance, a child table has exactly one incoming toDependentPK
     * (from the parent). A join table has two or more (from both sides of a many-to-many).
     * </p>
     */
    private static boolean isJoinTableTarget(DbEntity target) {
        int toDependentPKCount = 0;
        for (DbRelationship rel : target.getRelationships()) {
            DbRelationship reverseRel = rel.getReverseRelationship();
            if (reverseRel != null && reverseRel.isToDependentPK()) {
                toDependentPKCount++;
                if (toDependentPKCount > 1) {
                    return true;
                }
            }
        }
        return false;
    }
}
