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

package org.apache.cayenne.exp.property;

import java.util.List;

import org.apache.cayenne.Persistent;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.exp.parser.ASTPath;
import org.apache.cayenne.exp.path.CayennePath;
import org.apache.cayenne.exp.path.CayennePathSegment;

/**
 * Property that represents to-many relationship mapped on {@link List}.
 * <pre>{@code
 * ObjectSelect.query(Artist.class)
 *      .where(Artist.PAINTING_ARRAY.contains(painting));
 * }</pre>
 *
 * @see org.apache.cayenne.exp.property
 * @since 4.2
 */
public class ListProperty<V extends Persistent> extends CollectionProperty<V, List<V>> {

    /**
     * Constructs a new property with the given path and expression
     *
     * @param path           of the property (will be used as alias for the expression)
     * @param expression     expression for property
     * @param entityType     type of related entity
     */
    protected ListProperty(CayennePath path, Expression expression, Class<V> entityType) {
        super(path, expression, List.class, entityType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ListProperty<V> alias(String alias) {
        ASTPath exp = PropertyUtils.createPathExp(this.getPath(), alias, getExpression().getPathAliases());
        return PropertyFactory.createList(exp.getPath(), exp, this.getEntityType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ListProperty<V> outer() {
        CayennePathSegment last = getPath().last();
        if (last.isOuterJoin()) {
            return this;
        } else {
            CayennePath outerPath = getPath().parent().dot(last.outer());
            return PropertyFactory.createList(outerPath, getEntityType());
        }
    }

    /**
     * @return property that will be translated relative to parent query
     */
    public ListProperty<V> enclosing() {
        return PropertyFactory.createList(
                CayennePath.EMPTY_PATH,
                ExpressionFactory.enclosingObjectExp(getExpression()),
                getEntityType()
        );
    }
}
