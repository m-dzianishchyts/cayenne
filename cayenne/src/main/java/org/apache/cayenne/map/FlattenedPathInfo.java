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

import java.util.Collections;
import java.util.List;

/**
 * Pre-computed classification of a flattened {@link ObjRelationship}'s
 * {@link DbRelationship} path. Each segment in the path is annotated with a
 * {@link FlattenedPathSegmentType} that tells downstream consumers
 * (flush pipeline, prefetch routing, column extraction, etc.)
 * what kind of link they are dealing with — without re-discovering it
 * from raw join metadata every time.
 * <p>
 * Instances are <b>immutable</b> and created lazily by
 * {@link FlattenedPathAnalyzer#analyze(ObjRelationship)}.
 * </p>
 *
 * @since 5.0
 * @see FlattenedPathSegmentType
 * @see AnnotatedSegment
 */
public class FlattenedPathInfo {

    private final List<AnnotatedSegment> segments;
    private final boolean fkThroughInheritance;

    public FlattenedPathInfo(List<AnnotatedSegment> segments) {
        this.segments = Collections.unmodifiableList(segments);

        boolean fkThroughVI = false;

        if (segments.size() >= 2) {
            // FK through inheritance: all preceding segments are VI_TO_CHILD,
            // and the last segment is a regular FK-to-PK relationship
            boolean allPrecedingAreVI = true;
            for (int i = 0; i < segments.size() - 1; i++) {
                if (segments.get(i).getType() != FlattenedPathSegmentType.VI_TO_CHILD) {
                    allPrecedingAreVI = false;
                    break;
                }
            }
            if (allPrecedingAreVI) {
                FlattenedPathSegmentType lastType = segments.get(segments.size() - 1).getType();
                if (lastType == FlattenedPathSegmentType.REGULAR) {
                    DbRelationship lastRel = segments.get(segments.size() - 1).getRelationship();
                    fkThroughVI = lastRel.isToPK();
                }
            }
        }

        this.fkThroughInheritance = fkThroughVI;
    }

    /**
     * Returns the annotated segments of this flattened path, in order.
     */
    public List<AnnotatedSegment> getSegments() {
        return segments;
    }

    /**
     * Returns {@code true} if the FK is accessed through a vertical inheritance chain:
     * one or more {@link FlattenedPathSegmentType#VI_TO_CHILD} segments followed by
     * a {@link FlattenedPathSegmentType#REGULAR} FK-to-PK segment.
     */
    public boolean isFkThroughInheritance() {
        return fkThroughInheritance;
    }

    /**
     * A single segment of a flattened path, pairing a {@link DbRelationship}
     * with its classified {@link FlattenedPathSegmentType}.
     *
     * @since 5.0
     */
    public static class AnnotatedSegment {

        private final DbRelationship relationship;
        private final FlattenedPathSegmentType type;

        public AnnotatedSegment(DbRelationship relationship, FlattenedPathSegmentType type) {
            this.relationship = relationship;
            this.type = type;
        }

        public DbRelationship getRelationship() {
            return relationship;
        }

        public FlattenedPathSegmentType getType() {
            return type;
        }

        public boolean isVerticalInheritance() {
            return type.isVerticalInheritance();
        }

        @Override
        public String toString() {
            return relationship.getName() + " [" + type + "]";
        }
    }
}
