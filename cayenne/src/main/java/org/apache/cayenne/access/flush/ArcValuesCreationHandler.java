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

package org.apache.cayenne.access.flush;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cayenne.ObjectId;
import org.apache.cayenne.access.flush.operation.DbRowOp;
import org.apache.cayenne.access.flush.operation.DbRowOpType;
import org.apache.cayenne.access.flush.operation.DbRowOpVisitor;
import org.apache.cayenne.access.flush.operation.DbRowOpWithValues;
import org.apache.cayenne.access.flush.operation.DeleteDbRowOp;
import org.apache.cayenne.access.flush.operation.InsertDbRowOp;
import org.apache.cayenne.access.flush.operation.UpdateDbRowOp;
import org.apache.cayenne.exp.parser.ASTDbPath;
import org.apache.cayenne.exp.path.CayennePath;
import org.apache.cayenne.exp.path.CayennePathSegment;
import org.apache.cayenne.graph.ArcId;
import org.apache.cayenne.graph.GraphChangeHandler;
import org.apache.cayenne.map.DbAttribute;
import org.apache.cayenne.map.DbEntity;
import org.apache.cayenne.map.DbJoin;
import org.apache.cayenne.map.DbRelationship;
import org.apache.cayenne.map.FlattenedPathAnalyzer;
import org.apache.cayenne.map.FlattenedPathInfo;
import org.apache.cayenne.map.FlattenedPathInfo.AnnotatedSegment;
import org.apache.cayenne.map.FlattenedPathSegmentType;
import org.apache.cayenne.map.ObjEntity;
import org.apache.cayenne.map.ObjRelationship;
import org.apache.cayenne.reflect.AdditionalDbEntityDescriptor;

/**
 * Graph handler that collects information about arc changes into
 * {@link org.apache.cayenne.access.flush.operation.Values} and/or {@link org.apache.cayenne.access.flush.operation.Qualifier}.
 *
 * @since 4.2
 */
class ArcValuesCreationHandler implements GraphChangeHandler {

    final DbRowOpFactory factory;
    final DbRowOpType defaultType;

    ArcValuesCreationHandler(DbRowOpFactory factory, DbRowOpType defaultType) {
        this.factory = factory;
        this.defaultType = defaultType;
    }

    public void arcCreated(Object nodeId, Object targetNodeId, ArcId arcId) {
        processArcChange(nodeId, targetNodeId, arcId, true);
    }

    public void arcDeleted(Object nodeId, Object targetNodeId, ArcId arcId) {
        processArcChange(nodeId, targetNodeId, arcId, false);
    }

    private void processArcChange(Object nodeId, Object targetNodeId, ArcId arcId, boolean created) {
        ObjectId actualTargetId = (ObjectId)targetNodeId;
        ObjectId snapshotId = factory.getDiff().getCurrentArcSnapshotValue(arcId.getForwardArc());
        if(snapshotId != null) {
            actualTargetId = snapshotId;
        }
        ArcTarget arcTarget = new ArcTarget((ObjectId) nodeId, actualTargetId, arcId, !created);
        ObjectId sourceId = arcTarget.getSourceId();
        ObjectId targetId = arcTarget.getTargetId();
        if(factory.getProcessedArcs().contains(arcTarget.getReversed())) {
            return;
        }

        ObjEntity entity = factory.getDescriptor().getEntity();
        ObjRelationship objRelationship = entity.getRelationship(arcTarget.getArcId().getForwardArc());
        if(objRelationship == null) {
            String arc = arcId.getForwardArc();
            if(arc.startsWith(ASTDbPath.DB_PREFIX)) {
                String relName = arc.substring(ASTDbPath.DB_PREFIX.length());
                DbRelationship dbRelationship = entity.getDbEntity().getRelationship(relName);
                processRelationship(dbRelationship, sourceId, targetId, created);
            }
            return;
        }

        if(objRelationship.isFlattened()) {
            FlattenedPathInfo pathInfo = objRelationship.getFlattenedPathInfo();
            FlattenedPathProcessingResult result = processFlattenedPath(sourceId, targetId, pathInfo, created);
            if(result.isProcessed()) {
                factory.getProcessedArcs().add(arcTarget);
            }
        } else {
            DbRelationship dbRelationship = objRelationship.getDbRelationships().get(0);
            processRelationship(dbRelationship, sourceId, targetId, created);
            factory.getProcessedArcs().add(arcTarget);
        }
    }

    /**
     * Processes a flattened path using pre-computed {@link FlattenedPathInfo}.
     */
    FlattenedPathProcessingResult processFlattenedPath(ObjectId id, ObjectId finalTargetId,
                                                       FlattenedPathInfo pathInfo, boolean add) {
        return processFlattenedPath(id, finalTargetId, pathInfo, add, true);
    }

    /**
     * Core implementation that processes a flattened path segment by segment.
     */
    private FlattenedPathProcessingResult processFlattenedPath(ObjectId id, ObjectId finalTargetId,
                                                               FlattenedPathInfo pathInfo, boolean add,
                                                               boolean lastSegmentIsTerminal) {
        if(shouldSkipFlattenedOp(id, finalTargetId)) {
            return flattenedResultNotProcessed();
        }

        List<AnnotatedSegment> segments = pathInfo.getSegments();
        CayennePath flattenedPath = CayennePath.EMPTY_PATH;
        ObjectId srcId = id;
        ObjectId targetId = null;

        for (int i = 0; i < segments.size(); i++) {
            AnnotatedSegment segment = segments.get(i);
            DbRelationship relationship = segment.getRelationship();
            DbEntity target = relationship.getTargetEntity();
            boolean isLast = lastSegmentIsTerminal && (i == segments.size() - 1);
            flattenedPath = flattenedPath.dot(relationship.getName());

            // Build remaining path for PK derivation
            List<DbRelationship> remainingPath = new ArrayList<>(segments.size() - i - 1);
            for (int j = i + 1; j < segments.size(); j++) {
                remainingPath.add(segments.get(j).getRelationship());
            }

            if (isLast) {
                // 1. Last segment: use finalTargetId directly
                targetId = finalTargetId;
            } else {
                if (!relationship.isToMany()) {
                    // 2. Look up in store (already marked)
                    targetId = factory.getStore().getFlattenedId(id, flattenedPath);
                }
                if (targetId == null && finalTargetId != null) {
                    // 3. PK derivation from finalTargetId via remaining PK-to-PK chain
                    targetId = tryDerivePkFromFinal(target, finalTargetId, remainingPath);
                    if (targetId != null && !relationship.isToMany()) {
                        factory.getStore().markFlattenedPath(id, flattenedPath, targetId);
                    }
                }
            }

            if (targetId == null) {
                // 4. Fallback: create new row
                targetId = createNewRow(segment.getType(), relationship, target, flattenedPath, id, add);
            } else if (!isLast) {
                factory.getOrCreate(target, targetId, add ? DbRowOpType.UPDATE : defaultType);
            }

            processRelationship(relationship, srcId, targetId, shouldProcessAsAddition(segment, add));
            srcId = targetId;
        }

        return flattenedResultId(targetId);
    }

    /**
     * Processes a flattened attribute path for {@link ValuesCreationHandler}.
     * Uses the cached {@link FlattenedPathInfo} from {@link AdditionalDbEntityDescriptor}
     * when available. Falls back to runtime classification via {@link FlattenedPathAnalyzer}.
     */
    FlattenedPathProcessingResult processFlattenedAttributePath(ObjectId id, DbEntity entity,
                                                                CayennePath dbPath, boolean add) {
        if(shouldSkipFlattenedOp(id, null)) {
            return flattenedResultNotProcessed();
        }

        // The dbPath ends with a DbAttribute; the relationship prefix is the path to the AdditionalDbEntity.
        // Walk the path to find the last relationship segment index.
        CayennePath relPath = dbPath.parent();
        if (relPath != null && !relPath.isEmpty()) {
            AdditionalDbEntityDescriptor addEntity = factory.getDescriptor().getAdditionalDbEntities().get(relPath);
            if (addEntity != null && addEntity.getFlattenedPathInfo() != null) {
                return processFlattenedPath(id, null, addEntity.getFlattenedPathInfo(), add, false);
            }
        }

        // Fallback: walk the path segments, collecting only DbRelationship entries
        List<AnnotatedSegment> segments = new ArrayList<>();
        DbEntity current = entity;
        for (CayennePathSegment seg : dbPath) {
            DbRelationship rel = current.getRelationship(seg.value());
            if (rel == null) {
                break;
            }
            segments.add(new AnnotatedSegment(rel, FlattenedPathAnalyzer.classifySegment(rel)));
            current = rel.getTargetEntity();
        }

        if (segments.isEmpty()) {
            return flattenedResultNotProcessed();
        }

        FlattenedPathInfo pathInfo = new FlattenedPathInfo(segments);
        return processFlattenedPath(id, null, pathInfo, add, false);
    }

    /**
     * Creates a new row for a segment where no existing target ID was found.
     * Operation type and flattenedId tracking depend on the segment type.
     */
    private ObjectId createNewRow(FlattenedPathSegmentType segmentType, DbRelationship relationship,
                                  DbEntity target, CayennePath flattenedPath, ObjectId rootId, boolean add) {
        ObjectId targetId = ObjectId.of(ASTDbPath.DB_PREFIX + target.getName());

        if (!relationship.isToMany()) {
            factory.getStore().markFlattenedPath(rootId, flattenedPath, targetId);
        }

        DbRowOpType type = determineNewRowOpType(segmentType, relationship, add);

        if (segmentType != FlattenedPathSegmentType.JOIN_TABLE && !relationship.isToMany()) {
            factory.<DbRowOpWithValues>getOrCreate(target, targetId, type)
                    .getValues()
                    .addFlattenedId(flattenedPath, targetId);
        } else {
            factory.getOrCreate(target, targetId, type);
        }

        return targetId;
    }

    /**
     * Determines the {@link DbRowOpType} for a new row based on segment type and direction.
     * <ul>
     *   <li>JOIN_TABLE: INSERT when adding, DELETE when removing</li>
     *   <li>toMany (non-join): INSERT when adding, DELETE when removing</li>
     *   <li>toOne (VI or regular): INSERT when adding, UPDATE when removing</li>
     * </ul>
     */
    private DbRowOpType determineNewRowOpType(FlattenedPathSegmentType segmentType,
                                              DbRelationship relationship, boolean add) {
        if (segmentType == FlattenedPathSegmentType.JOIN_TABLE || relationship.isToMany()) {
            return add ? DbRowOpType.INSERT : DbRowOpType.DELETE;
        }
        return add ? DbRowOpType.INSERT : DbRowOpType.UPDATE;
    }

    /**
     * Attempts to derive the target {@link ObjectId} by tracing through the remaining
     * path via PK-to-PK joins from {@code finalTargetId}.
     *
     * @return the derived {@link ObjectId}, or {@code null} if derivation fails
     */
    private static ObjectId tryDerivePkFromFinal(DbEntity target, ObjectId finalTargetId,
                                                 List<DbRelationship> remainingPath) {
        Map<String, Object> finalIdSnapshot = finalTargetId.getIdSnapshot();
        if (finalIdSnapshot == null) {
            return null;
        }
        Map<String, String> pkMapping = resolvePkMapping(target, remainingPath);
        if (pkMapping.isEmpty()) {
            return null;
        }
        Map<String, Object> derivedPk = new HashMap<>(pkMapping.size());
        for (Map.Entry<String, String> entry : pkMapping.entrySet()) {
            Object value = finalIdSnapshot.get(entry.getValue());
            if (value == null) {
                return null;
            }
            derivedPk.put(entry.getKey(), value);
        }
        return ObjectId.of(ASTDbPath.DB_PREFIX + target.getName(), derivedPk);
    }

    /**
     * Builds a mapping from target PK attribute names to the corresponding PK attribute names
     * in the final entity, tracing through a PK-to-PK join chain.
     *
     * @return map where key = target PK attr name, value = final entity PK attr name;
     *         empty map if the path is not a valid PK-to-PK chain
     */
    private static Map<String, String> resolvePkMapping(DbEntity target, List<DbRelationship> remainingPath) {
        Map<String, String> targetToCurrentPk = new HashMap<>();
        for (DbAttribute pk : target.getPrimaryKeys()) {
            targetToCurrentPk.put(pk.getName(), pk.getName());
        }
        if (targetToCurrentPk.isEmpty()) {
            return Map.of();
        }
        for (DbRelationship rel : remainingPath) {
            DbRelationship reverse = rel.getReverseRelationship();
            boolean isPkToPk = rel.isToDependentPK() || (reverse != null && reverse.isToDependentPK());
            if (!isPkToPk || rel.isToMany()) {
                return Map.of();
            }
            Map<String, String> nextMapping = new HashMap<>(targetToCurrentPk.size());
            for (DbJoin join : rel.getJoins()) {
                if (!join.getSource().isPrimaryKey() || !join.getTarget().isPrimaryKey()) {
                    return Map.of();
                }
                for (Map.Entry<String, String> entry : targetToCurrentPk.entrySet()) {
                    if (entry.getValue().equals(join.getSource().getName())) {
                        nextMapping.put(entry.getKey(), join.getTarget().getName());
                    }
                }
            }
            if (nextMapping.size() != targetToCurrentPk.size()) {
                return Map.of();
            }
            targetToCurrentPk = nextMapping;
        }
        return targetToCurrentPk;
    }

    private boolean shouldSkipFlattenedOp(ObjectId id, ObjectId finalTargetId) {
        // as we get two sides of the relationship processed,
        // check if we got more information for a reverse operation
        return finalTargetId != null
                && factory.getStore().getFlattenedIds(id).isEmpty()
                && !factory.getStore().getFlattenedIds(finalTargetId).isEmpty();
    }

    private boolean shouldProcessAsAddition(AnnotatedSegment segment, boolean add) {
        if (add) {
            return true;
        }
        // VI segments share PK between parent and child tables —
        // must always propagate PK values, never nullify (CAY-2838)
        return segment.isVerticalInheritance();
    }

    protected void processRelationship(DbRelationship dbRelationship, ObjectId srcId, ObjectId targetId, boolean add) {
        for(DbJoin join : dbRelationship.getJoins()) {
            boolean srcPK = join.getSource().isPrimaryKey();
            boolean targetPK = join.getTarget().isPrimaryKey();

            Object valueToUse;
            DbRowOp rowOp;
            DbAttribute attribute;
            ObjectId id;
            boolean processDelete;

            // We manage 3 cases here:
            // 1. PK -> FK: just propagate value from PK and to FK
            // 2. PK -> PK: check isToDep flag and set dependent one
            // 3. NON-PK -> FK (not supported fully for now, see CAY-2488): also check isToDep flag,
            //    but get value from DbRow, not ObjID
            if(srcPK != targetPK) {
                // case 1
                processDelete = true;
                id = null;
                if(srcPK) {
                    valueToUse = ObjectIdValueSupplier.getFor(srcId, join.getSourceName());
                    rowOp = factory.getOrCreate(dbRelationship.getTargetEntity(), targetId, DbRowOpType.UPDATE);
                    attribute = join.getTarget();
                } else {
                    valueToUse = ObjectIdValueSupplier.getFor(targetId, join.getTargetName());
                    rowOp = factory.getOrCreate(dbRelationship.getSourceEntity(), srcId, defaultType);
                    attribute = join.getSource();
                }
            } else {
                // case 2 and 3
                processDelete = false;
                if(dbRelationship.isToDependentPK()) {
                    valueToUse = ObjectIdValueSupplier.getFor(srcId, join.getSourceName());
                    rowOp = factory.getOrCreate(dbRelationship.getTargetEntity(), targetId, DbRowOpType.UPDATE);
                    attribute = join.getTarget();
                    id = targetId;
                    if(dbRelationship.isToMany()) {
                        // strange mapping toDepPK and toMany, but just skip it
                        rowOp = null;
                    }
                } else {
                    valueToUse = ObjectIdValueSupplier.getFor(targetId, join.getTargetName());
                    rowOp = factory.getOrCreate(dbRelationship.getSourceEntity(), srcId, defaultType);
                    attribute = join.getSource();
                    id = srcId;
                    if(dbRelationship.getReverseRelationship().isToMany()) {
                        // strange mapping toDepPK and toMany, but just skip it
                        rowOp = null;
                    }
                }
            }

            // propagated master -> child PK
            if(id != null && attribute.isPrimaryKey()) {
                id.getReplacementIdMap().put(attribute.getName(), valueToUse);
            }
            if(rowOp != null) {
                rowOp.accept(new ValuePropagationVisitor(attribute, add, valueToUse, processDelete));
            }
        }
    }

    private static class ValuePropagationVisitor implements DbRowOpVisitor<Void> {
        private final DbAttribute attribute;
        private final boolean add;
        private final Object valueToUse;
        private final boolean processDelete;

        private ValuePropagationVisitor(DbAttribute attribute, boolean add, Object valueToUse, boolean processDelete) {
            this.attribute = attribute;
            this.add = add;
            this.valueToUse = valueToUse;
            this.processDelete = processDelete;
        }

        @Override
        public Void visitInsert(InsertDbRowOp dbRow) {
            dbRow.getValues().addValue(attribute, add ? valueToUse : null, true);
            return null;
        }

        @Override
        public Void visitUpdate(UpdateDbRowOp dbRow) {
            dbRow.getValues().addValue(attribute, add ? valueToUse : null, true);
            return null;
        }

        @Override
        public Void visitDelete(DeleteDbRowOp dbRow) {
            if(processDelete) {
                dbRow.getQualifier().addAdditionalQualifier(attribute, valueToUse);
            }
            return null;
        }
    }

    static FlattenedPathProcessingResult flattenedResultId(ObjectId id) {
        return new FlattenedPathProcessingResult(true, id);
    }

    static FlattenedPathProcessingResult flattenedResultNotProcessed() {
        return new FlattenedPathProcessingResult(false, null);
    }

    final static class FlattenedPathProcessingResult {
        private final boolean processed;
        private final ObjectId id;

        private FlattenedPathProcessingResult(boolean processed, ObjectId id) {
            this.processed = processed;
            this.id = id;
        }

        public boolean isProcessed() {
            return processed;
        }

        public ObjectId getId() {
            return id;
        }
    }
}
