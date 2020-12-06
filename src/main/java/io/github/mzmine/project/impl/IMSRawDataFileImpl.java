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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.common.collect.Range;
import io.github.msdk.MSDKRuntimeException;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MobilityType;
import io.github.mzmine.datamodel.Scan;

public class IMSRawDataFileImpl extends RawDataFileImpl implements IMSRawDataFile {

  public static final String SAVE_IDENTIFIER = "Ion mobility Raw data file";

  private final TreeMap<Integer, StorableFrame> frames;
  private final Hashtable<Integer, Set<Integer>> frameNumbersCache;
  private final Hashtable<Integer, Range<Double>> dataMobilityRangeCache;
  private final Hashtable<Integer, Set<Frame>> frameMsLevelCache;

  protected Range<Double> mobilityRange;
  protected MobilityType mobilityType;

  public IMSRawDataFileImpl(String dataFileName) throws IOException {
    super(dataFileName);

    frames = /*
              * (TreeMap<Integer, StorableFrame>) Collections .synchronizedMap(
              */new TreeMap<Integer, StorableFrame>()/* ) */;
    frameNumbersCache = new Hashtable<>();
    dataMobilityRangeCache = new Hashtable<>();
    frameMsLevelCache = new Hashtable<>();

    mobilityRange = null;
    mobilityType = MobilityType.NONE;
  }

  @Override
  public synchronized void addScan(Scan newScan) throws IOException {
    if (this.mobilityType == MobilityType.NONE) {
      this.mobilityType = newScan.getMobilityType();
    }
    if (newScan.getMobilityType() != mobilityType) {
      throw new MSDKRuntimeException(
          "The mobility type specified in scan (" + newScan.getMobilityType()
              + ") does not match the mobility type of raw data file (" + getMobilityType() + ")");
    }

    if (newScan instanceof Frame) {
      Frame newFrame = (Frame) newScan;
      if (newScan instanceof StorableFrame) {
        frames.put(((StorableFrame) newScan).getFrameId(), (StorableFrame) newFrame);
        return;
      }
      final int storageId = storeDataPoints(newFrame.getDataPoints());
      StorableFrame storedFrame =
          new StorableFrame(newFrame, this, newFrame.getNumberOfDataPoints(), storageId);
      frames.put(storedFrame.getFrameId(), storedFrame);
      return;
    } else {
      if (mobilityRange == null) {
        mobilityRange = Range.singleton(newScan.getMobility());
      } else if (!mobilityRange.contains(newScan.getMobility())) {
        mobilityRange = mobilityRange.span(Range.singleton(newScan.getMobility()));
      }
      super.addScan(newScan);
    }
  }

  @Nonnull
  @Override
  public Set<Frame> getFrames() {
    return new HashSet<>(frames.values());
  }

  @Nonnull
  @Override
  public Set<Frame> getFrames(int msLevel) {
    return frameMsLevelCache.computeIfAbsent(msLevel, level -> frames.values().stream()
        .filter(frame -> frame.getMSLevel() == msLevel).collect(Collectors.toSet()));
  }

  @Nullable
  @Override
  public Frame getFrame(int frameNum) {
    return frames.get(frameNum);
  }

  @Override
  @Nonnull
  public Set<Frame> getFrames(int msLevel, Range<Float> rtRange) {
    return frameMsLevelCache.get(msLevel).stream()
        .filter(frame -> rtRange.contains(frame.getRetentionTime())).collect(Collectors.toSet());
  }

  @Nonnull
  @Override
  public Set<Integer> getFrameNumbers() {
    return frames.keySet();
  }

  @Nonnull
  @Override
  public Set<Integer> getFrameNumbers(int msLevel) {
    return frameNumbersCache.computeIfAbsent(msLevel, (key) -> {
      Set<Integer> frameNums = new HashSet<>();
      synchronized (frames) {
        for (Entry<Integer, StorableFrame> e : frames.entrySet()) {
          if (e.getValue().getMSLevel() == msLevel) {
            frameNums.add(e.getKey());
          }
        }
      }
      return frameNums;
    });
  }

  @Override
  public int getNumberOfFrames() {
    return frames.size();
  }

  @Nonnull
  @Override
  public Set<Integer> getFrameNumbers(int msLevel, @Nonnull Range<Float> rtRange) {
    // since {@link getFrameNumbers(int)} is prefiltered, this shouldn't lead to NPE
    return getFrameNumbers(msLevel).stream()
        .filter(frameNum -> rtRange.contains(getFrame(frameNum).getRetentionTime()))
        .collect(Collectors.toSet());
  }

  @Nonnull
  @Override
  public Range<Double> getDataMobilityRange() {
    return mobilityRange;
  }

  @Override
  @Nullable
  public Frame getFrameAtRt(double rt) {
    if (rt > getDataRTRange().upperEndpoint()) {
      return null;
    }

    List<Frame> frameList = new ArrayList<>(getFrames());
    frameList.sort(Comparator.comparing(Frame::getRetentionTime));
    double minDiff = 10E10;

    for (int i = 0; i < frameList.size(); i++) {
      double diff = Math.abs(rt - frameList.get(i).getRetentionTime());
      if (diff < minDiff) {
        minDiff = diff;
      } else if (diff > minDiff) { // not triggered in first run
        return frameList.get(i - 1); // the previous one was better
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Frame getFrameAtRt(double rt, int msLevel) {
    if (rt > getDataRTRange(msLevel).upperEndpoint()) {
      return null;
    }
    Range<Float> range = Range.closed((float) rt - 2, (float) rt + 2);
    List<Frame> eligibleFrames = new ArrayList<>(getFrames(msLevel, range));
    eligibleFrames.sort(Comparator.comparing(Frame::getRetentionTime));
    double minDiff = 10E6;

    for (int i = 0; i < eligibleFrames.size(); i++) {
      double diff = Math.abs(rt - eligibleFrames.get(i).getRetentionTime());
      if (diff < minDiff) {
        minDiff = diff;
      } else if (diff > minDiff) { // not triggered in first run
        return eligibleFrames.get(i - 1); // the previous one was better
      }
    }
    return null;
  }

  @Nonnull
  @Override
  public MobilityType getMobilityType() {
    return mobilityType;
  }

  @Nonnull
  @Override
  public Range<Double> getDataMobilityRange(int msLevel) {
    if (dataMobilityRangeCache.get(msLevel) == null) {
      double lower = 1E10;
      double upper = -1E10;
      synchronized (frames) {
        for (Entry<Integer, StorableFrame> e : frames.entrySet()) {
          if (e.getValue().getMSLevel() == msLevel
              && e.getValue().getMobilityRange().lowerEndpoint() < lower) {
            lower = e.getValue().getMobilityRange().lowerEndpoint();
          }
          if (e.getValue().getMSLevel() == msLevel
              && e.getValue().getMobilityRange().upperEndpoint() > upper) {
            upper = e.getValue().getMobilityRange().upperEndpoint();
          }
        }
      }
      dataMobilityRangeCache.put(msLevel, Range.closed(lower, upper));
    }
    return dataMobilityRangeCache.get(msLevel);
  }
}
