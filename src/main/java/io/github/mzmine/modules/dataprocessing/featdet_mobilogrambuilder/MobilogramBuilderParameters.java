package io.github.mzmine.modules.dataprocessing.featdet_mobilogrambuilder;

import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.MassListParameter;
import io.github.mzmine.parameters.parametertypes.StringParameter;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesParameter;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelectionParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;

public class MobilogramBuilderParameters extends SimpleParameterSet {

  public static final RawDataFilesParameter rawDataFiles = new RawDataFilesParameter();

  public static final MassListParameter massList = new MassListParameter("Mass list", "", false);

  public static final ScanSelectionParameter scanSelection =
      new ScanSelectionParameter("Scan " + "selection",
          "Filter scans based on their properties. Different noise levels ( -> mass "
              + "lists) are recommended for MS1 and MS/MS scans",
          new ScanSelection());

  public static final MZToleranceParameter mzTolerance = new MZToleranceParameter("m/z tolerance",
      "m/z tolerance between mobility scans to be assigned to the same mobilogram", 0.001, 5,
      false);

  public static final IntegerParameter minSignals = new IntegerParameter("Minimum signals",
      "Minimum " + "signals in a ion mobility ion trace (above previously set noise levels)", 7);

  public static final StringParameter suffix =
      new StringParameter("Suffix", "This string is added to filename as suffix", "iontraces");

  public MobilogramBuilderParameters() {
    super(new Parameter[] {rawDataFiles, massList, scanSelection, mzTolerance, minSignals, suffix});
  }
}
