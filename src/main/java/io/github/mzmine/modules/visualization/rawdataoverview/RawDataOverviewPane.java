/*
 * Copyright 2006-2020 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.modules.visualization.rawdataoverview;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.gui.mainwindow.MZmineTab;
import java.io.IOException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.BorderPane;

public class RawDataOverviewPane extends MZmineTab {

  public static final Logger logger = Logger.getLogger(RawDataOverviewPane.class.getName());

  private RawDataOverviewWindowController controller;

  public RawDataOverviewPane(boolean showBinding, boolean defaultBindingState) {
    super("Raw data overview", showBinding, defaultBindingState);

    controller = null;
    FXMLLoader loader = new FXMLLoader((getClass().getResource("RawDataOverviewWindow.fxml")));

    try {
      BorderPane root = loader.load();
      controller = loader.getController();
      controller.initialize();
      setContent(root);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Could not load RawDataOverview.fxml", e);
      return;
    }
  }

  public RawDataOverviewPane() {
    this(false, false);
  }

  @Override
  public Collection<? extends RawDataFile> getRawDataFiles() {
    return controller.getRawDataFiles();
  }

  @Override
  public Collection<? extends FeatureList> getFeatureLists() {
    return null;
  }

  @Override
  public Collection<? extends FeatureList> getAlignedFeatureLists() {
    return null;
  }

  @Override
  public void onRawDataFileSelectionChanged(Collection<? extends RawDataFile> rawDataFiles) {
    controller.setRawDataFiles((Collection<RawDataFile>) rawDataFiles);
  }

  @Override
  public void onFeatureListSelectionChanged(Collection<? extends FeatureList> featureLists) {
    return;
  }

  @Override
  public void onAlignedFeatureListSelectionChanged(
      Collection<? extends FeatureList> featurelists) {
    return;
  }
}
