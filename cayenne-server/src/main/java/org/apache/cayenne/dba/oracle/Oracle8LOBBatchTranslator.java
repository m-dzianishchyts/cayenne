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

package org.apache.cayenne.dba.oracle;

import org.apache.cayenne.CayenneRuntimeException;
import org.apache.cayenne.access.sqlbuilder.ColumnNodeBuilder;
import org.apache.cayenne.access.sqlbuilder.SQLBuilder;
import org.apache.cayenne.access.sqlbuilder.SelectBuilder;
import org.apache.cayenne.access.sqlbuilder.TableNodeBuilder;
import org.apache.cayenne.access.translator.DbAttributeBinding;
import org.apache.cayenne.access.translator.batch.BaseBatchTranslator;
import org.apache.cayenne.access.translator.batch.BatchTranslator;
import org.apache.cayenne.access.types.ExtendedType;
import org.apache.cayenne.dba.DbAdapter;
import org.apache.cayenne.dba.TypesMapping;
import org.apache.cayenne.map.DbAttribute;
import org.apache.cayenne.query.BatchQuery;
import org.apache.cayenne.query.BatchQueryRow;

import java.sql.Types;
import java.util.List;

/**
 * Superclass of query builders for the DML operations involving LOBs.
 */
abstract class Oracle8LOBBatchTranslator<T extends BatchQuery> extends BaseBatchTranslator<T> implements
        BatchTranslator {

    protected String newClobFunction;
    protected String newBlobFunction;

    Oracle8LOBBatchTranslator(T query, DbAdapter adapter) {
        super(query, adapter);
    }

    String createLOBSelectString(List<DbAttribute> selectedLOBAttributes, List<DbAttribute> qualifierAttributes) {
        TableNodeBuilder table = SQLBuilder.table(context.getRootDbEntity());
        ColumnNodeBuilder[] selectedLOBAttributesArray = new ColumnNodeBuilder[selectedLOBAttributes.size()];

        for (int i = 0; i < selectedLOBAttributesArray.length; i++) {
            DbAttribute attribute = selectedLOBAttributes.get(i);
            selectedLOBAttributesArray[i] = table.column(attribute);
        }

        // TODO: select for update
        SelectBuilder select = SQLBuilder
                .select(selectedLOBAttributesArray)
                .from(table)
                .where(buildQualifier(qualifierAttributes));
        return doTranslate(select);
    }

    /**
     * Appends parameter placeholder for the value of the column being updated.
     * If requested, performs special handling on LOB columns.
     */
    protected void appendUpdatedParameter(StringBuilder buf, DbAttribute dbAttribute, Object value) {

        int type = dbAttribute.getType();

        if (isUpdatableColumn(value, type)) {
            buf.append('?');
        } else {
            if (type == Types.CLOB) {
                buf.append(newClobFunction);
            } else if (type == Types.BLOB) {
                buf.append(newBlobFunction);
            } else {
                throw new CayenneRuntimeException("Unknown LOB column type: %s(%s). Query buffer: %s."
                        , type, TypesMapping.getSqlNameByType(type), buf);
            }
        }
    }

    @Override
    public DbAttributeBinding[] updateBindings(BatchQueryRow row) {

        int len = bindings.length;

        for (int i = 0, j = 1; i < len; i++) {
            DbAttributeBinding binding = bindings[i];
            Object value = row.getValue(i);
            DbAttribute attribute = binding.getAttribute();
            int type = attribute.getType();

            // TODO: (Andrus) This works as long as there is no LOBs in qualifier
            if (isUpdatableColumn(value, type)) {
                DbAdapter adapter = context.getAdapter();
                ExtendedType extendedType = value != null
                                            ? adapter.getExtendedTypes().getRegisteredType(value.getClass())
                                            : adapter.getExtendedTypes().getDefaultType();
                binding.include(j++, value, extendedType);
            } else {
                binding.exclude();
            }
        }

        return bindings;
    }

    protected boolean isUpdatableColumn(Object value, int type) {
        return value == null || type != Types.BLOB && type != Types.CLOB;
    }

    void setNewBlobFunction(String string) {
        newBlobFunction = string;
    }

    void setNewClobFunction(String string) {
        newClobFunction = string;
    }
}
