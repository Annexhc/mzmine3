package io.github.mzmine.datamodel.features.types.graphicalnodes;

import io.github.mzmine.util.color.ColorsFX;
import java.util.Map.Entry;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.paint.Color;
import javax.annotation.Nonnull;
import com.google.common.util.concurrent.AtomicDouble;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.types.numbers.AreaType;
import javafx.beans.property.Property;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.StackPane;

public class AreaBarChart extends StackPane {

  public AreaBarChart(@Nonnull ModularFeatureListRow row, AtomicDouble progress) {
    XYChart.Series data = new XYChart.Series();
    int i = 1;
    int size = row.getFilesFeatures().size();
    for (Entry<RawDataFile, ModularFeature> entry : row.getFilesFeatures().entrySet()) {
      Property<Float> areaProperty = entry.getValue().get(AreaType.class);
      // set bar color according to the raw data file
      Data newData = new XYChart.Data("" + i, areaProperty.getValue() == null ? 0f : areaProperty.getValue());
      newData.nodeProperty().addListener((ChangeListener<Node>) (ov, oldNode, newNode) -> {
        if (newNode != null) {
          Node node = newData.getNode();
          Color fileColor = entry.getKey().getColor();
          if (fileColor == null) {
            fileColor = Color.DARKORANGE;
          }
          node.setStyle("-fx-bar-fill: " + ColorsFX.toHexString(fileColor) + ";");
        }
      });
      data.getData().add(newData);
      i++;
      if (progress != null)
        progress.addAndGet(1.0 / size);
    }

    final CategoryAxis xAxis = new CategoryAxis();
    final NumberAxis yAxis = new NumberAxis();
    final BarChart<String, Number> bc = new BarChart<String, Number>(xAxis, yAxis);
    bc.setLegendVisible(false);
    bc.setMinHeight(100);
    bc.setPrefHeight(100);
    bc.setMaxHeight(100);
    bc.setBarGap(3);
    bc.setCategoryGap(3);
    bc.setPrefWidth(150);

    bc.getData().addAll(data);
    this.getChildren().add(bc);
  }

}
