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

package io.github.mzmine.modules.dataprocessing.featdet_mobilogrambuilder;

import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MobilityType;
import io.github.mzmine.gui.chartbasics.gui.javafx.template.providers.PlotDatasetProvider;

public interface IMobilogram extends PlotDatasetProvider {

  public double getMZ();

  public double getMobility();

  public double getMaximumIntensity();

  @Nullable
  public Range<Double> getMobilityRange();

  @Nullable
  public Range<Double> getMZRange();

  @Nonnull
  Set<MobilityDataPoint> getDataPoints();

  @Nonnull
  public MobilityDataPoint getHighestDataPoint();

  @Nonnull
  Set<Integer> getScanNumbers();

  public MobilityType getMobilityType();

  public String representativeString();

}