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

package io.github.mzmine.project.impl;

import io.github.mzmine.datamodel.MobilityType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import com.google.common.collect.Range;
import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.MassList;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.util.DataPointSorter;
import io.github.mzmine.util.SortingDirection;
import io.github.mzmine.util.SortingProperty;
import io.github.mzmine.util.scans.ScanUtils;


/**
 * Implementation of the Scan interface which stores raw data points in a temporary file, accessed
 * by RawDataFileImpl.readFromFloatBufferFile()
 */
public class StorableScan implements Scan {

  private Logger logger = Logger.getLogger(this.getClass().getName());

  private int scanNumber, msLevel, fragmentScans[];
  private double precursorMZ;
  private int precursorCharge;
  private float retentionTime;
  private Range<Double> mzRange;
  private DataPoint basePeak;
  private Double totalIonCurrent;
  private MassSpectrumType spectrumType;
  private int numberOfDataPoints;
  protected RawDataFileImpl rawDataFile;
  private ArrayList<MassList> massLists = new ArrayList<MassList>();
  private PolarityType polarity;
  private String scanDefinition;
  private Range<Double> scanMZRange;

  private int storageID;
  private double mobility;
  protected MobilityType mobilityType;

  /**
   * Constructor for creating a storable scan from a given scan
   */
  public StorableScan(Scan originalScan, RawDataFileImpl rawDataFile, int numberOfDataPoints,
      int storageID) {

    // save scan data
    this.rawDataFile = rawDataFile;
    this.numberOfDataPoints = numberOfDataPoints;
    this.storageID = storageID;

    this.scanNumber = originalScan.getScanNumber();
    this.msLevel = originalScan.getMSLevel();
    this.retentionTime = originalScan.getRetentionTime();
    this.precursorMZ = originalScan.getPrecursorMZ();
    this.precursorCharge = originalScan.getPrecursorCharge();
    this.fragmentScans = originalScan.getFragmentScanNumbers();
    this.spectrumType = originalScan.getSpectrumType();
    this.mzRange = originalScan.getDataPointMZRange();
    this.basePeak = originalScan.getHighestDataPoint();
    this.totalIonCurrent = originalScan.getTIC();
    this.polarity = originalScan.getPolarity();
    this.scanDefinition = originalScan.getScanDefinition();
    this.scanMZRange = originalScan.getScanningMZRange();

    this.mobility = originalScan.getMobility();
    this.mobilityType = originalScan.getMobilityType();
  }

  public StorableScan(RawDataFileImpl rawDataFile, int storageID, int numberOfDataPoints,
      int scanNumber, int msLevel, float retentionTime, double precursorMZ,
      int precursorCharge, int fragmentScans[], MassSpectrumType spectrumType,
      PolarityType polarity, String scanDefinition, Range<Double> scanMZRange) {

    this(rawDataFile, storageID, numberOfDataPoints, scanNumber, msLevel, retentionTime,
        precursorMZ, precursorCharge, fragmentScans, spectrumType, polarity, scanDefinition,
        scanMZRange, -1.0d, MobilityType.NONE);
  }

  public StorableScan(RawDataFileImpl rawDataFile, int storageID, int numberOfDataPoints,
      int scanNumber, int msLevel, float retentionTime, double precursorMZ,
      int precursorCharge, int fragmentScans[], MassSpectrumType spectrumType,
      PolarityType polarity, String scanDefinition, Range<Double> scanMZRange, double mobility,
      MobilityType mobilityType) {

    this.rawDataFile = rawDataFile;
    this.numberOfDataPoints = numberOfDataPoints;
    this.storageID = storageID;

    this.scanNumber = scanNumber;
    this.msLevel = msLevel;
    this.retentionTime = retentionTime;
    this.precursorMZ = precursorMZ;
    this.mobility = mobility;
    this.precursorCharge = precursorCharge;
    this.fragmentScans = fragmentScans;
    this.spectrumType = spectrumType;
    this.polarity = polarity;
    this.scanDefinition = scanDefinition;
    this.scanMZRange = scanMZRange;

    this.mobilityType = mobilityType;
  }

  /**
   * @return Scan's datapoints from temporary file.
   */
  @Override
  @Nonnull
  public DataPoint[] getDataPoints() {

    try {
      DataPoint result[] = rawDataFile.readDataPoints(storageID);
      return result;
    } catch (IOException e) {
      logger.severe("Could not read data from temporary file " + e.toString());
      return new DataPoint[0];
    }

  }

  /**
   * @return Returns scan datapoints within a given range
   */
  @Override
  @Nonnull
  public DataPoint[] getDataPointsByMass(@Nonnull Range<Double> mzRange) {

    DataPoint dataPoints[] = getDataPoints();

    // Important fix for https://github.com/mzmine/mzmine2/issues/844
    Arrays.sort(dataPoints, new DataPointSorter(SortingProperty.MZ, SortingDirection.Ascending));

    int startIndex, endIndex;
    for (startIndex = 0; startIndex < dataPoints.length; startIndex++) {
      if (dataPoints[startIndex].getMZ() >= mzRange.lowerEndpoint()) {
        break;
      }
    }

    for (endIndex = startIndex; endIndex < dataPoints.length; endIndex++) {
      if (dataPoints[endIndex].getMZ() > mzRange.upperEndpoint()) {
        break;
      }
    }

    DataPoint pointsWithinRange[] = new DataPoint[endIndex - startIndex];

    // Copy the relevant points
    System.arraycopy(dataPoints, startIndex, pointsWithinRange, 0, endIndex - startIndex);

    return pointsWithinRange;
  }

  /**
   * @return Returns scan datapoints over certain intensity
   */
  @Override
  @Nonnull
  public DataPoint[] getDataPointsOverIntensity(double intensity) {
    int index;
    Vector<DataPoint> points = new Vector<DataPoint>();
    DataPoint dataPoints[] = getDataPoints();

    for (index = 0; index < dataPoints.length; index++) {
      if (dataPoints[index].getIntensity() >= intensity) {
        points.add(dataPoints[index]);
      }
    }

    DataPoint pointsOverIntensity[] = points.toArray(new DataPoint[0]);

    return pointsOverIntensity;
  }

  @Override
  @Nonnull
  public RawDataFile getDataFile() {
    return rawDataFile;
  }

  public int getStorageID() {
    return storageID;
  }

  /**
   * @see io.github.mzmine.datamodel.Scan#getNumberOfDataPoints()
   */
  @Override
  public int getNumberOfDataPoints() {
    return numberOfDataPoints;
  }

  /**
   * @see io.github.mzmine.datamodel.Scan#getScanNumber()
   */
  @Override
  public int getScanNumber() {
    return scanNumber;
  }

  /**
   * @see io.github.mzmine.datamodel.Scan#getMSLevel()
   */
  @Override
  public int getMSLevel() {
    return msLevel;
  }

  /**
   * @see io.github.mzmine.datamodel.Scan#getPrecursorMZ()
   */
  @Override
  public double getPrecursorMZ() {
    return precursorMZ;
  }

  /**
   * @return Returns the precursorCharge.
   */
  @Override
  public int getPrecursorCharge() {
    return precursorCharge;
  }

  /**
   * @see io.github.mzmine.datamodel.Scan#getScanAcquisitionTime()
   */
  @Override
  public float getRetentionTime() {
    return retentionTime;
  }

  void updateValues() {
    DataPoint dataPoints[] = getDataPoints();

    // find m/z range and base peak
    if (dataPoints.length > 0) {

      basePeak = dataPoints[0];
      mzRange = Range.singleton(dataPoints[0].getMZ());
      double tic = 0;

      for (DataPoint dp : dataPoints) {

        if (dp.getIntensity() > basePeak.getIntensity()) {
          basePeak = dp;
        }

        mzRange = mzRange.span(Range.singleton(dp.getMZ()));

        tic += dp.getIntensity();

      }

      totalIonCurrent = new Double(tic);

    } else {
      mzRange = Range.singleton(0.0);
      totalIonCurrent = new Double(0);
    }
  }

  /**
   * @see io.github.mzmine.datamodel.Scan#getMZRangeMax()
   */
  @Override
  @Nonnull
  public Range<Double> getDataPointMZRange() {
    if (mzRange == null) {
      updateValues();
    }
    return mzRange;
  }

  /**
   * @see io.github.mzmine.datamodel.Scan#getBasePeakMZ()
   */
  @Override
  public DataPoint getHighestDataPoint() {
    if ((basePeak == null) && (numberOfDataPoints > 0)) {
      updateValues();
    }
    return basePeak;
  }

  /**
   * @see io.github.mzmine.datamodel.Scan#getFragmentScanNumbers()
   */
  @Override
  public int[] getFragmentScanNumbers() {
    return fragmentScans;
  }

  /**
   * @param fragmentScans The fragmentScans to set.
   */
  void setFragmentScanNumbers(int[] fragmentScans) {
    this.fragmentScans = fragmentScans;
  }

  /**
   * @see io.github.mzmine.datamodel.Scan#getSpectrumType()
   */
  @Override
  public MassSpectrumType getSpectrumType() {
    if (spectrumType == null) {
      spectrumType = ScanUtils.detectSpectrumType(getDataPoints());
    }
    return spectrumType;
  }

  @Override
  public double getTIC() {
    if (totalIonCurrent == null) {
      updateValues();
    }
    return totalIonCurrent;
  }

  @Override
  public String toString() {
    return ScanUtils.scanToString(this, false);
  }

  @Override
  public synchronized void addMassList(final @Nonnull MassList massList) {

    // Remove all mass lists with same name, if there are any
    MassList currentMassLists[] = massLists.toArray(new MassList[0]);
    for (MassList ml : currentMassLists) {
      if (ml.getName().equals(massList.getName())) {
        removeMassList(ml);
      }
    }

    StorableMassList storedMassList;
    if (massList instanceof StorableMassList) {
      storedMassList = (StorableMassList) massList;
    } else {
      DataPoint massListDataPoints[] = massList.getDataPoints();
      try {
        int mlStorageID = rawDataFile.storeDataPoints(massListDataPoints);
        storedMassList = new StorableMassList(rawDataFile, mlStorageID, massList.getName(), this);
      } catch (IOException e) {
        logger.severe("Could not write data to temporary file " + e.toString());
        return;
      }
    }

    // Add the new mass list
    massLists.add(storedMassList);

    // Add the mass list to the tree model
    MZmineProjectImpl project =
        (MZmineProjectImpl) MZmineCore.getProjectManager().getCurrentProject();

  }

  @Override
  public synchronized void removeMassList(final @Nonnull MassList massList) {

    // Remove the mass list
    massLists.remove(massList);
    if (massList instanceof StorableMassList) {
      StorableMassList storableMassList = (StorableMassList) massList;
      storableMassList.removeStoredData();
    }

  }

  @Override
  public void addFragmentScan(int scanNumber) {
    throw new UnsupportedOperationException(
        "adding fragment scans is not supported by " + this.getClass().getName());
  }

  @Override
  @Nonnull
  public MassList[] getMassLists() {
    return massLists.toArray(new MassList[0]);
  }

  @Override
  public MassList getMassList(@Nonnull String name) {
    for (MassList ml : massLists) {
      if (ml.getName().equals(name)) {
        return ml;
      }
    }
    return null;
  }

  @Override
  @Nonnull
  public PolarityType getPolarity() {
    if (polarity == null) {
      polarity = PolarityType.UNKNOWN;
    }
    return polarity;
  }

  @Override
  public String getScanDefinition() {
    if (scanDefinition == null) {
      scanDefinition = "";
    }
    return scanDefinition;
  }

  @Override
  @Nonnull
  public Range<Double> getScanningMZRange() {
    if (scanMZRange == null) {
      scanMZRange = getDataPointMZRange();
    }
    return scanMZRange;
  }

  @Override
  public double getMobility() {
    return mobility;
  }

  @Override
  public MobilityType getMobilityType() {
    return mobilityType;
  }
}
