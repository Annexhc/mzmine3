package io.github.mzmine.modules.dataprocessing.featdet_ionmobilitytracebuilder;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.types.FeatureShapeIonMobilityRetentionTimeHeatMapType;
import io.github.mzmine.datamodel.features.types.FeatureShapeIonMobilityRetentionTimeType;
import io.github.mzmine.datamodel.features.types.FeatureShapeMobilogramType;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.project.impl.StorableFrame;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.DataTypeUtils;
import io.github.mzmine.util.FeatureConvertors;

/**
 * Worker task to build ion mobility traces
 */
public class IonMobilityTraceBuilderTask extends AbstractTask {

  private static Logger logger = Logger.getLogger(IonMobilityTraceBuilderTask.class.getName());

  private RangeSet<Double> rangeSet = TreeRangeSet.create();
  private HashMap<Range<Double>, IIonMobilityTrace> rangeToIonTraceMap = new HashMap<>();

  private final MZmineProject project;
  private final RawDataFile rawDataFile;
  private final String suffix;
  private final Set<Frame> frames;
  private final MZTolerance mzTolerance;
  private final String massList;
  private final int minDataPointsRt;
  private final int minTotalSignals;
  private final ScanSelection scanSelection;
  private double dataPointHeight;
  private double dataPointWidth;
  private double progress = 0.0;

  @SuppressWarnings("unchecked")
  public IonMobilityTraceBuilderTask(MZmineProject project, RawDataFile rawDataFile,
      Set<Frame> frames, ParameterSet parameters) {
    this.project = project;
    this.rawDataFile = rawDataFile;
    this.mzTolerance =
        parameters.getParameter(IonMobilityTraceBuilderParameters.mzTolerance).getValue();
    this.massList = parameters.getParameter(IonMobilityTraceBuilderParameters.massList).getValue();
    this.minDataPointsRt =
        parameters.getParameter(IonMobilityTraceBuilderParameters.minDataPointsRt).getValue();
    this.minTotalSignals =
        parameters.getParameter(IonMobilityTraceBuilderParameters.minTotalSignals).getValue();
    this.scanSelection =
        parameters.getParameter(IonMobilityTraceBuilderParameters.scanSelection).getValue();
    this.frames = (Set<Frame>) scanSelection.getMachtingScans((frames));
    this.suffix = parameters.getParameter(IonMobilityTraceBuilderParameters.suffix).getValue();
    setStatus(TaskStatus.WAITING);
  }

  @Override
  public String getTaskDescription() {
    return "Detecting mobility ion traces";
  }

  @Override
  public double getFinishedPercentage() {
    return progress;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    if (isCanceled()) {
      return;
    }
    calculateDataPointSizeForPlots(frames);
    Set<RetentionTimeMobilityDataPoint> rtMobilityDataPoints = extractAllDataPointsFromFrames();
    progress = 0.0;
    double progressStep =
        (!rtMobilityDataPoints.isEmpty()) ? 0.5 / rtMobilityDataPoints.size() : 0.0;
    createIonMobilityTraceTargetSet(rtMobilityDataPoints, progressStep);
    SortedSet<IIonMobilityTrace> ionMobilityTraces = finishIonMobilityTraces();
    buildModularFeatureList(ionMobilityTraces);
    progress = 1.0;
    setStatus(TaskStatus.FINISHED);
  }

  // Extract all retention time and mobility resolved data point sorted by intensity
  private Set<RetentionTimeMobilityDataPoint> extractAllDataPointsFromFrames() {
    logger.info("Start data point extraction");
    SortedSet<RetentionTimeMobilityDataPoint> allDataPoints =
        new TreeSet<>(Comparator.comparing(RetentionTimeMobilityDataPoint::getIntensity));
    for (Frame frame : frames) {
      if (!(frame instanceof StorableFrame) || !scanSelection.matches(frame)) {
        continue;
      }
      for (Scan scan : frame.getMobilityScans()) {
        if (scan.getMassList(massList) == null) {
          setStatus(TaskStatus.ERROR);
          setErrorMessage(
              "Scan #" + scan.getScanNumber() + " does not have a mass list " + massList);
        } else {
          Arrays.stream(scan.getMassList(massList).getDataPoints()).forEach(
              dp -> allDataPoints.add(new RetentionTimeMobilityDataPoint(scan.getMobility(),
                  dp.getMZ(), scan.getRetentionTime(), dp.getIntensity(), frame.getFrameId(),
                  scan.getScanNumber(), dataPointWidth, dataPointHeight)));
        }
      }
    }
    logger.info("Extracted " + allDataPoints.size() + " ims data points");
    return allDataPoints;
  }

  private void createIonMobilityTraceTargetSet(
      Set<RetentionTimeMobilityDataPoint> rtMobilityDataPoints, double progressStep) {
    logger.info("Start m/z ranges calculation");
    for (RetentionTimeMobilityDataPoint rtMobilityDataPoint : rtMobilityDataPoints) {
      if (isCanceled()) {
        return;
      }
      Range<Double> containsDataPointRange = rangeSet.rangeContaining(rtMobilityDataPoint.getMZ());
      Range<Double> toleranceRange = mzTolerance.getToleranceRange(rtMobilityDataPoint.getMZ());
      if (containsDataPointRange == null) {
        // look +- mz tolerance to see if ther is a range near by.
        // If there is use the proper boundry of that range for the
        // new range to insure than NON OF THE RANGES OVERLAP.
        Range<Double> plusRange = rangeSet.rangeContaining(toleranceRange.upperEndpoint());
        Range<Double> minusRange = rangeSet.rangeContaining(toleranceRange.lowerEndpoint());
        Double toBeLowerBound;
        Double toBeUpperBound;

        // If both of the above ranges are null then we make the new range spaning the full
        // mz tolerance range.
        // If one or both are not null we need to properly modify the range of the new
        // chromatogram so that none of the points are overlapping.
        if ((plusRange == null) && (minusRange == null)) {
          toBeLowerBound = toleranceRange.lowerEndpoint();
          toBeUpperBound = toleranceRange.upperEndpoint();
        } else if ((plusRange == null) && (minusRange != null)) {
          // the upper end point of the minus range will be the lower
          // range of the new one
          toBeLowerBound = minusRange.upperEndpoint();
          toBeUpperBound = toleranceRange.upperEndpoint();

        } else if ((minusRange == null) && (plusRange != null)) {
          toBeLowerBound = toleranceRange.lowerEndpoint();
          toBeUpperBound = plusRange.lowerEndpoint();
        } else if ((minusRange != null) && (plusRange != null)) {
          toBeLowerBound = minusRange.upperEndpoint();
          toBeUpperBound = plusRange.lowerEndpoint();
        } else {
          toBeLowerBound = 0.0;
          toBeUpperBound = 0.0;
        }

        if (toBeLowerBound < toBeUpperBound) {
          Range<Double> newRange = Range.open(toBeLowerBound, toBeUpperBound);
          IIonMobilityTrace newIonMobilityIonTrace = new IonMobilityTrace(
              rtMobilityDataPoint.getMZ(), rtMobilityDataPoint.getRetentionTime(),
              rtMobilityDataPoint.getMobility(), rtMobilityDataPoint.getIntensity(), newRange);
          Set<RetentionTimeMobilityDataPoint> dataPointsSetForTrace = new HashSet<>();
          dataPointsSetForTrace.add(rtMobilityDataPoint);
          newIonMobilityIonTrace.setDataPoints(dataPointsSetForTrace);
          rangeToIonTraceMap.put(newRange, newIonMobilityIonTrace);
          rangeSet.add(newRange);
        } else if (toBeLowerBound.equals(toBeUpperBound) && plusRange != null) {
          IIonMobilityTrace currentIonMobilityIonTrace = rangeToIonTraceMap.get(plusRange);
          currentIonMobilityIonTrace.getDataPoints().add(rtMobilityDataPoint);
        } else
          throw new IllegalStateException(String.format("Incorrect range [%f, %f] for m/z %f",
              toBeLowerBound, toBeUpperBound, rtMobilityDataPoint.getMZ()));

      } else {
        // In this case we do not need to update the rangeSet

        IIonMobilityTrace currentIonMobilityIonTrace =
            rangeToIonTraceMap.get(containsDataPointRange);
        currentIonMobilityIonTrace.getDataPoints().add(rtMobilityDataPoint);

        // update the entry in the map
        rangeToIonTraceMap.put(containsDataPointRange, currentIonMobilityIonTrace);
      }
      progress += progressStep;
    }
  }

  private SortedSet<IIonMobilityTrace> finishIonMobilityTraces() {
    Set<Range<Double>> ranges = rangeSet.asRanges();
    Iterator<Range<Double>> rangeIterator = ranges.iterator();
    SortedSet<IIonMobilityTrace> ionMobilityTraces =
        new TreeSet<>(Comparator.comparing(IIonMobilityTrace::getMz));
    double progressStep = (!ranges.isEmpty()) ? 0.5 / ranges.size() : 0.0;
    while (rangeIterator.hasNext()) {
      if (isCanceled()) {
        break;
      }
      progress += progressStep;
      Range<Double> currentRangeKey = rangeIterator.next();
      IIonMobilityTrace ionTrace = rangeToIonTraceMap.get(currentRangeKey);
      if (ionTrace.getDataPoints().size() >= minTotalSignals
          && checkNumberOfRetentionTimeDataPoints(ionTrace.getDataPoints())) {
        logger.info("Build ion trace for m/z range " + ionTrace.getMzRange());
        finishIonTrace(ionTrace);
        ionMobilityTraces.add(ionTrace);
      }
    }
    return ionMobilityTraces;
  }

  private IIonMobilityTrace finishIonTrace(IIonMobilityTrace ionTrace) {
    Range<Double> rawDataPointsIntensityRange = null;
    Range<Double> rawDataPointsMZRange = null;
    Range<Double> rawDataPointsMobilityRange = null;
    Range<Float> rawDataPointsRtRange = null;
    Set<Integer> scanNumbers = new HashSet<>();
    SortedSet<RetentionTimeMobilityDataPoint> sortedRetentionTimeMobilityDataPoints =
        new TreeSet<>(Comparator.comparing(RetentionTimeMobilityDataPoint::getScanNumber));
    Float rt = 0.0f;
    double mobility = 0.0f;
    sortedRetentionTimeMobilityDataPoints.addAll(ionTrace.getDataPoints());
    // Update raw data point ranges, height, rt and representative scan
    double maximumIntensity = Double.MIN_VALUE;
    for (RetentionTimeMobilityDataPoint retentionTimeMobilityDataPoint : sortedRetentionTimeMobilityDataPoints) {
      scanNumbers.add(retentionTimeMobilityDataPoint.getScanNumber());
      if (rawDataPointsIntensityRange == null && rawDataPointsMZRange == null
          && rawDataPointsMobilityRange == null && rawDataPointsRtRange == null) {
        rawDataPointsIntensityRange =
            Range.singleton(retentionTimeMobilityDataPoint.getIntensity());
        rawDataPointsMZRange = Range.singleton(retentionTimeMobilityDataPoint.getMZ());
        rawDataPointsMobilityRange = Range.singleton(retentionTimeMobilityDataPoint.getMobility());
        rawDataPointsRtRange = Range.singleton(retentionTimeMobilityDataPoint.getRetentionTime());
      } else {
        rawDataPointsIntensityRange = rawDataPointsIntensityRange
            .span(Range.singleton(retentionTimeMobilityDataPoint.getIntensity()));
        rawDataPointsMZRange =
            rawDataPointsMZRange.span(Range.singleton(retentionTimeMobilityDataPoint.getMZ()));
        rawDataPointsMobilityRange = rawDataPointsMobilityRange
            .span(Range.singleton(retentionTimeMobilityDataPoint.getMobility()));
        rawDataPointsRtRange = rawDataPointsRtRange
            .span(Range.singleton(retentionTimeMobilityDataPoint.getRetentionTime()));
      }

      if (maximumIntensity < retentionTimeMobilityDataPoint.getIntensity()) {
        maximumIntensity = retentionTimeMobilityDataPoint.getIntensity();
        rt = retentionTimeMobilityDataPoint.getRetentionTime();
        mobility = retentionTimeMobilityDataPoint.getMobility();
      }
    }

    // TODO think about representative scan
    ionTrace.setScanNumbers(scanNumbers);
    ionTrace.setMobilityRange(rawDataPointsMobilityRange);
    ionTrace.setMzRange(rawDataPointsMZRange);
    ionTrace.setRetentionTimeRange(rawDataPointsRtRange);
    ionTrace.setIntensityRange(rawDataPointsIntensityRange);
    ionTrace.setMaximumIntensity(maximumIntensity);
    ionTrace.setRetentionTime(rt);
    ionTrace.setMobility(mobility);
    // logger.info("Ion Trace results:\n" + "Scan numbers: " + ionTrace.getScanNumbers() + "\n" + //
    // "Mobility range: " + ionTrace.getMobilityRange() + "\n" + //
    // "m/z range: " + ionTrace.getMzRange() + "\n" + //
    // "rt range: " + ionTrace.getRetentionTimeRange() + "\n" + //
    // "intensity range: " + ionTrace.getIntensityRange() + "\n" + //
    // "Max intensity : " + ionTrace.getMaximumIntensity() + "\n" + //
    // "Retention time : " + ionTrace.getRetentionTime() + "\n" + //
    // "Mobility : " + ionTrace.getMobility()//
    // );
    // TODO calc area
    // Update area
    // double area = 0;
    // for (int i = 1; i < allScanNumbers.length; i++) {
    // // For area calculation, we use retention time in seconds
    // double previousRT = dataFile.getScan(allScanNumbers[i - 1]).getRetentionTime() * 60d;
    // double currentRT = dataFile.getScan(allScanNumbers[i]).getRetentionTime() * 60d;
    // double previousHeight = dataPointsMap.get(allScanNumbers[i - 1]).getIntensity();
    // double currentHeight = dataPointsMap.get(allScanNumbers[i]).getIntensity();
    // area += (currentRT - previousRT) * (currentHeight + previousHeight) / 2;
    // }

    // TODO
    // Update fragment scan
    // fragmentScan =
    // ScanUtils.findBestFragmentScan(dataFile, dataFile.getDataRTRange(1), rawDataPointsMZRange);

    // allMS2FragmentScanNumbers = ScanUtils.findAllMS2FragmentScans(dataFile,
    // dataFile.getDataRTRange(1), rawDataPointsMZRange);

    // if (fragmentScan > 0) {
    // Scan fragmentScanObject = dataFile.getScan(fragmentScan);
    // int precursorCharge = fragmentScanObject.getPrecursorCharge();
    // if (precursorCharge > 0)
    // this.charge = precursorCharge;
    // }

    return ionTrace;
  }

  private boolean checkNumberOfRetentionTimeDataPoints(
      Set<RetentionTimeMobilityDataPoint> dataPoints) {
    Set<Float> retentionTimes = new HashSet<>();
    for (RetentionTimeMobilityDataPoint dataPoint : dataPoints) {
      retentionTimes.add(dataPoint.getRetentionTime());
    }
    return (retentionTimes.size() >= minDataPointsRt);
  }

  private void buildModularFeatureList(SortedSet<IIonMobilityTrace> ionMobilityTraces) {
    ModularFeatureList featureList =
        new ModularFeatureList(rawDataFile + " " + suffix, rawDataFile);
    // ensure that the default columns are available
    DataTypeUtils.addDefaultChromatographicTypeColumns(featureList);
    featureList.addRowType(new FeatureShapeIonMobilityRetentionTimeType());
    featureList.addRowType(new FeatureShapeIonMobilityRetentionTimeHeatMapType());
    featureList.addRowType(new FeatureShapeMobilogramType());
    int featureId = 1;
    for (IIonMobilityTrace ionTrace : ionMobilityTraces) {
      ionTrace.setFeatureList(featureList);
      ModularFeature modular =
          FeatureConvertors.IonMobilityIonTraceToModularFeature(ionTrace, rawDataFile);
      ModularFeatureListRow newRow =
          new ModularFeatureListRow(featureList, featureId, rawDataFile, modular);
      featureList.addRow(newRow);
      featureId++;
    }
    project.addFeatureList(featureList);
  }


  private void calculateDataPointSizeForPlots(Set<Frame> frames) {
    Frame frameA = null;
    SortedSet<Frame> sortedFrames = new TreeSet<>(Comparator.comparing(Frame::getFrameId));
    sortedFrames.addAll(frames);
    for (Frame frame : frames) {
      System.out.println(frame.getRetentionTime());
      System.out.println(frame.getFrameId());
      if (frameA == null) {
        frameA = frame;
      } else if (frameA.getMSLevel() == 1 && frame.getMSLevel() == 1) {
        dataPointWidth = Math.abs(frameA.getRetentionTime() - frame.getRetentionTime());
        dataPointHeight = 1 / (double) frame.getNumberOfMobilityScans();
        break;
      }
    }
  }

}
