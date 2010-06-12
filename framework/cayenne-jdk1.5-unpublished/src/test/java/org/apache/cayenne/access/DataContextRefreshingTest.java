/*****************************************************************
 *   Licensed to the Apache Software Foundation (ASF) under one
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
 ****************************************************************/

package org.apache.cayenne.access;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.cayenne.ObjectId;
import org.apache.cayenne.PersistenceState;
import org.apache.cayenne.map.ObjEntity;
import org.apache.cayenne.query.SQLTemplate;
import org.apache.cayenne.testdo.testmap.Artist;
import org.apache.cayenne.testdo.testmap.Painting;

/**
 * Test suite covering possible scenarios of refreshing updated objects. This includes
 * refreshing relationships and attributes changed outside of Cayenne with and without
 * prefetching.
 */
public class DataContextRefreshingTest extends DataContextCase {

    public void testRefetchRootWithUpdatedAttributes() throws Exception {
        String nameBefore = "artist2";
        String nameAfter = "not an artist";

        Artist artist = fetchArtist(nameBefore, false);
        assertNotNull(artist);
        assertEquals(nameBefore, artist.getArtistName());

        // update via DataNode directly
        updateRow(artist.getObjectId(), "ARTIST_NAME", nameAfter);

        // fetch into the same context
        artist = fetchArtist(nameBefore, false);
        assertNull(artist);

        artist = fetchArtist(nameAfter, false);
        assertNotNull(artist);
        assertEquals(nameAfter, artist.getArtistName());
    }

    public void testRefetchRootWithNullifiedToOne() throws Exception {
        Painting painting = insertPaintingInContext("p");
        assertNotNull(painting.getToArtist());

        // update via DataNode directly
        updateRow(painting.getObjectId(), "ARTIST_ID", null);

        // select without prefetch
        painting = fetchPainting(painting.getPaintingTitle(), false);
        assertNotNull(painting);
        assertNull(painting.getToArtist());
    }

    public void testRefetchRootWithChangedToOneTarget() throws Exception {
        Painting painting = insertPaintingInContext("p");
        Artist artistBefore = painting.getToArtist();
        assertNotNull(artistBefore);

        Artist artistAfter = fetchArtist("artist3", false);
        assertNotNull(artistAfter);
        assertNotSame(artistBefore, artistAfter);

        // update via DataNode directly
        updateRow(painting.getObjectId(), "ARTIST_ID", artistAfter
                .getObjectId()
                .getIdSnapshot()
                .get("ARTIST_ID"));

        // select without prefetch
        painting = fetchPainting(painting.getPaintingTitle(), false);
        assertNotNull(painting);
        assertSame(artistAfter, painting.getToArtist());
    }

    public void testRefetchRootWithNullToOneTargetChangedToNotNull() throws Exception {
        Painting painting = insertPaintingInContext("p");
        painting.setToArtist(null);
        context.commitChanges();

        assertNull(painting.getToArtist());

        Artist artistAfter = fetchArtist("artist3", false);
        assertNotNull(artistAfter);

        // update via DataNode directly
        updateRow(painting.getObjectId(), "ARTIST_ID", artistAfter
                .getObjectId()
                .getIdSnapshot()
                .get("ARTIST_ID"));

        // select without prefetch
        painting = fetchPainting(painting.getPaintingTitle(), false);
        assertNotNull(painting);
        assertSame(artistAfter, painting.getToArtist());
    }

    public void testRefetchRootWithDeletedToMany() throws Exception {
        Painting painting = insertPaintingInContext("p");
        Artist artist = painting.getToArtist();
        assertEquals(artist.getPaintingArray().size(), 1);

        deleteRow(painting.getObjectId());

        // select without prefetch
        artist = fetchArtist(artist.getArtistName(), false);
        assertEquals(artist.getPaintingArray().size(), 1);

        // select using relationship prefetching
        artist = fetchArtist(artist.getArtistName(), true);
        assertEquals(0, artist.getPaintingArray().size());
    }

    public void testRefetchRootWithAddedToMany() throws Exception {
        Artist artist = fetchArtist("artist2", false);
        assertEquals(artist.getPaintingArray().size(), 0);

        createTestData("P2");

        // select without prefetch
        artist = fetchArtist(artist.getArtistName(), false);
        assertEquals(artist.getPaintingArray().size(), 0);

        // select using relationship prefetching
        artist = fetchArtist(artist.getArtistName(), true);
        assertEquals(artist.getPaintingArray().size(), 1);
    }

    public void testInvalidateRootWithUpdatedAttributes() throws Exception {
        String nameBefore = "artist2";
        String nameAfter = "not an artist";

        Artist artist = fetchArtist(nameBefore, false);
        assertNotNull(artist);
        assertEquals(nameBefore, artist.getArtistName());

        // update via DataNode directly
        updateRow(artist.getObjectId(), "ARTIST_NAME", nameAfter);

        context.invalidateObjects(Collections.singletonList(artist));
        assertEquals(nameAfter, artist.getArtistName());
    }

    public void testInvalidateRootWithNullifiedToOne() throws Exception {
        Painting painting = insertPaintingInContext("p");
        assertNotNull(painting.getToArtist());

        // update via DataNode directly
        updateRow(painting.getObjectId(), "ARTIST_ID", null);

        context.invalidateObjects(Collections.singletonList(painting));
        assertNull(painting.getToArtist());
    }

    public void testInvalidateRootWithChangedToOneTarget() throws Exception {
        Painting painting = insertPaintingInContext("p");
        Artist artistBefore = painting.getToArtist();
        assertNotNull(artistBefore);

        Artist artistAfter = fetchArtist("artist3", false);
        assertNotNull(artistAfter);
        assertNotSame(artistBefore, artistAfter);

        // update via DataNode directly
        updateRow(painting.getObjectId(), "ARTIST_ID", artistAfter
                .getObjectId()
                .getIdSnapshot()
                .get("ARTIST_ID"));

        context.invalidateObjects(Collections.singletonList(painting));
        assertSame(artistAfter, painting.getToArtist());
    }

    public void testInvalidateRootWithNullToOneTargetChangedToNotNull() throws Exception {
        Painting painting = insertPaintingInContext("p");
        painting.setToArtist(null);
        context.commitChanges();

        assertNull(painting.getToArtist());

        Artist artistAfter = fetchArtist("artist3", false);
        assertNotNull(artistAfter);

        // update via DataNode directly
        updateRow(painting.getObjectId(), "ARTIST_ID", artistAfter
                .getObjectId()
                .getIdSnapshot()
                .get("ARTIST_ID"));

        context.invalidateObjects(Collections.singletonList(painting));
        assertSame(artistAfter, painting.getToArtist());
    }

    public void testInvalidateRootWithDeletedToMany() throws Exception {
        Painting painting = insertPaintingInContext("p");
        Artist artist = painting.getToArtist();
        assertEquals(artist.getPaintingArray().size(), 1);

        deleteRow(painting.getObjectId());

        context.invalidateObjects(Collections.singletonList(artist));
        assertEquals(artist.getPaintingArray().size(), 0);
    }

    public void testInvaliateRootWithAddedToMany() throws Exception {
        Artist artist = fetchArtist("artist2", false);
        assertEquals(artist.getPaintingArray().size(), 0);

        createTestData("P2");
        assertEquals(artist.getPaintingArray().size(), 0);
        context.invalidateObjects(Collections.singletonList(artist));
        assertEquals(artist.getPaintingArray().size(), 1);
    }

    public void testInvalidateThenModify() throws Exception {
        Artist artist = fetchArtist("artist2", false);
        assertNotNull(artist);

        context.invalidateObjects(Collections.singletonList(artist));
        assertEquals(PersistenceState.HOLLOW, artist.getPersistenceState());

        // this must trigger a fetch
        artist.setArtistName("new name");
        assertEquals(PersistenceState.MODIFIED, artist.getPersistenceState());
    }

    public void testModifyHollow() throws Exception {
        createTestData("P2");

        // reset context
        context = createDataContext();

        Painting painting = fetchPainting("P_artist2", false);
        Artist artist = painting.getToArtist();
        assertEquals(PersistenceState.HOLLOW, artist.getPersistenceState());
        assertNull(artist.readPropertyDirectly("artistName"));

        // this must trigger a fetch
        artist.setDateOfBirth(new Date());
        assertEquals(PersistenceState.MODIFIED, artist.getPersistenceState());
        assertNotNull(artist.readPropertyDirectly("artistName"));
    }

    /**
     * Helper method to update a single column in a database row.
     */
    private void updateRow(ObjectId id, String dbAttribute, Object newValue) {
        SQLTemplate updateQuery = new SQLTemplate(
                id.getEntityName(),
                "UPDATE $table SET $column = #bind($value) "
                        + "WHERE $idColumn = #bind($id)");

        ObjEntity entity = getDomain().getEntityResolver().getObjEntity(
                id.getEntityName());
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("table", entity.getDbEntityName());
        parameters.put("column", dbAttribute);
        parameters.put("value", newValue);
        parameters.put("idColumn", id.getIdSnapshot().keySet().iterator().next());
        parameters.put("id", id.getIdSnapshot().values().iterator().next());
        updateQuery.setParameters(parameters);

        getDomain().onQuery(null, updateQuery);
    }

    private void deleteRow(ObjectId id) {
        SQLTemplate deleteQuery = new SQLTemplate(
                id.getEntityName(),
                "DELETE FROM $table " + "WHERE $idColumn = #bind($id)");

        ObjEntity entity = getDomain().getEntityResolver().getObjEntity(
                id.getEntityName());
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("table", entity.getDbEntityName());
        parameters.put("idColumn", id.getIdSnapshot().keySet().iterator().next());
        parameters.put("id", id.getIdSnapshot().values().iterator().next());
        deleteQuery.setParameters(parameters);

        getDomain().onQuery(null, deleteQuery);
    }
}
