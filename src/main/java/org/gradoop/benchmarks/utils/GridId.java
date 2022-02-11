/*
 * Copyright © 2014 - 2021 Leipzig University (Database Research Group)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradoop.benchmarks.utils;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.gradoop.flink.model.api.functions.KeyFunction;
import org.gradoop.temporal.model.impl.pojo.TemporalVertex;

/**
 * Grouping key function to extract a grid id.
 */
public class GridId implements KeyFunction<TemporalVertex, Integer> {

  /**
   * Get the grid cell id as integer.
   *
   * @param element the vertex to extract the lat and long information
   * @return the grid cell id
   */
  @Override
  public Integer getKey(TemporalVertex element) {
    float lon;
    float lat;
    lon = !element.getPropertyValue("long").getString().equals("NULL") ? Float.parseFloat(element.getPropertyValue("long").getString()) : -1;
    lat = !element.getPropertyValue("lat").getString().equals("NULL") ? Float.parseFloat(element.getPropertyValue("lat").getString()) : -1;
    if (lon != -1 || lat != -1) {
      return GeoUtils.mapToGridCell(lon, lat);
    } else {
      return -1;
    }
  }

  /**
   * Adds information about the grid cells to the resulting super vertex.
   *
   * @param element the vertex to extend
   * @param key the key object of that super vertex, i.e., the cell id
   */
  @Override
  public void addKeyToElement(TemporalVertex element, Object key) {
    element.setProperty("gridId", key);
    element.setProperty("cell_lat", GeoUtils.getGridCellCenterLat((int) key));
    element.setProperty("cell_long", GeoUtils.getGridCellCenterLon((int) key));
  }

  @Override
  public TypeInformation<Integer> getType() {
    return TypeInformation.of(Integer.class);
  }
}
