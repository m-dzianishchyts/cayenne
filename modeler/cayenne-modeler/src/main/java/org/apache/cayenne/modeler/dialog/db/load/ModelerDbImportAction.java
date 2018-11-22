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

package org.apache.cayenne.modeler.dialog.db.load;

import org.apache.cayenne.CayenneRuntimeException;
import org.apache.cayenne.configuration.DataChannelDescriptorLoader;
import org.apache.cayenne.configuration.DataMapLoader;
import org.apache.cayenne.configuration.event.DataMapEvent;
import org.apache.cayenne.configuration.server.DataSourceFactory;
import org.apache.cayenne.configuration.server.DbAdapterFactory;
import org.apache.cayenne.configuration.xml.DataChannelMetaData;
import org.apache.cayenne.dbsync.merge.factory.MergerTokenFactoryProvider;
import org.apache.cayenne.dbsync.merge.token.MergerToken;
import org.apache.cayenne.dbsync.reverse.dbimport.DbImportConfiguration;
import org.apache.cayenne.dbsync.reverse.dbimport.DefaultDbImportAction;
import org.apache.cayenne.di.Inject;
import org.apache.cayenne.map.DataMap;
import org.apache.cayenne.modeler.Application;
import org.apache.cayenne.modeler.editor.GlobalDbImportController;
import org.apache.cayenne.project.ProjectSaver;
import org.slf4j.Logger;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class ModelerDbImportAction extends DefaultDbImportAction {

    private static final String DIALOG_TITLE = "Reverse Engineering Result";

    @Inject
    private DataMap targetMap;

    DataMap sourceDataMap;
    DbImportConfiguration config;

    private DbLoadResultDialog resultDialog;
    private boolean isNothingChanged;

    private GlobalDbImportController globalDbImportController;

    public ModelerDbImportAction(@Inject Logger logger,
                                 @Inject ProjectSaver projectSaver,
                                 @Inject DataSourceFactory dataSourceFactory,
                                 @Inject DbAdapterFactory adapterFactory,
                                 @Inject DataMapLoader mapLoader,
                                 @Inject MergerTokenFactoryProvider mergerTokenFactoryProvider,
                                 @Inject DataChannelMetaData metaData,
                                 @Inject DataChannelDescriptorLoader dataChannelDescriptorLoader) {
        super(logger, projectSaver, dataSourceFactory, adapterFactory, mapLoader, mergerTokenFactoryProvider, dataChannelDescriptorLoader, metaData);
        globalDbImportController = Application.getInstance().getFrameController().getGlobalDbImportController();
    }

    @Override
    public void execute(DbImportConfiguration config) throws Exception {
        this.config = config;
        this.sourceDataMap = loadDataMap(config);
    }


    public void commit() throws Exception {
        commit(config, sourceDataMap);
        Application.getInstance().getFrameController().getProjectController().fireDataMapEvent(new DataMapEvent(this, targetMap));
    }

    @Override
    protected Collection<MergerToken> log(List<MergerToken> tokens) {
        resultDialog = globalDbImportController.createDialog();
        logger.info("");
        if (tokens.isEmpty()) {
            logger.info("Detected changes: No changes to import.");
            resultDialog.addMsg(targetMap);
            isNothingChanged = true;
            return tokens;
        }

        logger.info("Detected changes: ");
        for (MergerToken token : tokens) {
            String logString = String.format("    %-20s %s", token.getTokenName(), token.getTokenValue());
            logger.info(logString);
            resultDialog.addRowToOutput(logString, targetMap);
            isNothingChanged = false;
        }

        logger.info("");
        resultDialog.getOkButton().addActionListener(e -> {
            try {
                commit();
                checkForUnusedImports();
            } catch (Exception ex) {
                throw new CayenneRuntimeException("Nothing to commit.");
            }
        });

        resultDialog.getRevertButton().addActionListener(e -> {
            resetDialog();
        });

        resultDialog.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                resetDialog();
            }
        });

        return tokens;
    }

    private void resetDialog() {
        resultDialog.setVisible(false);
        globalDbImportController.resetDialog();
    }

    private void checkForUnusedImports() {
        globalDbImportController.checkImport(targetMap);
        if(globalDbImportController.createDialog().getTableForMap().isEmpty()) {
            resetDialog();
            globalDbImportController.setGlobalImport(false);
        }
    }

    @Override
    protected void addMessageToLogs(String message, List<String> messages) {
        String formattedMessage = String.format("    %-20s", message);
        messages.add(formattedMessage);
        resultDialog.addRowToOutput(formattedMessage, targetMap);
        isNothingChanged = false;
    }

    @Override
    protected void logMessages(List<String> messages) {
        super.logMessages(messages);
        if (isNothingChanged) {
            JOptionPane optionPane = new JOptionPane("Detected changes: No changes to import.", JOptionPane.PLAIN_MESSAGE);
            JDialog dialog = optionPane.createDialog(DIALOG_TITLE);
            dialog.setModal(false);
            dialog.setAlwaysOnTop(true);
            dialog.setVisible(true);
        } else if (!resultDialog.isVisible()) {
            resultDialog.setVisible(true);
        }
    }

    @Override
    protected DataMap existingTargetMap(DbImportConfiguration configuration) throws IOException {
        return targetMap;
    }
}